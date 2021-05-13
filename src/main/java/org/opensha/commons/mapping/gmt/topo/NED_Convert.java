package org.opensha.commons.mapping.gmt.topo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.opensha.commons.geo.GeoTools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.primitives.Shorts;

public class NED_Convert {

	private static final short buffer = 6; // each tile is buffered by this amount
	
	public static void main(String[] args) throws ZipException, IOException {
		double input_res = GeoTools.secondsToDeg(1d);
		short in_rows = 3600 + 2*buffer;
		short in_cols = 3600 + 2*buffer;
		int arcSecs;
		if (args.length == 1)
			arcSecs = Integer.parseInt(args[0]);
		else if (args.length > 1)
			throw new IllegalArgumentException("too many arguments!");
		else
			arcSecs = 3;
		double output_res = GeoTools.secondsToDeg((double)arcSecs);
		
//		File inputDir = new File("/tmp/dem/1sec_ca/");
//		double regMinLat = 32;
//		double regMaxLat = 43;
//		double regMinLon = -126;
//		double regMaxLon = -113;
//		File outputFile = new File("/tmp/dem_out.flt");
		
		File inputDir = new File("/home/scec-01/opensha/ned_usa/1sec_tiles");
//		double regMinLat = 26;
//		double regMaxLat = 52;
//		double regMinLon = -128;
//		double regMaxLon = -66;
		double regMinLat = 20;
		double regMaxLat = 52;
		double regMinLon = -128;
		double regMaxLon = -60;
		File outputFile = new File("/home/scec-01/opensha/ned_usa/us_dem_"+arcSecs+"sec.flt");
//		double regMinLat = 32;
//		double regMaxLat = 43;
//		double regMinLon = -126;
//		double regMaxLon = -113;
//		File outputFile = new File("/home/scec-01/opensha/ned_usa/ca_dem_"+arcSecs+"sec.flt");
		
		long out_rows = (long)((regMaxLat - regMinLat)/output_res + 1.5);
//		System.out.println((regMaxLat - regMinLat)/output_res);
		long out_cols = (long)((regMaxLon - regMinLon)/output_res + 1.5);
//		System.out.println((regMaxLon - regMinLon)/output_res);
		long outputSize = out_rows * out_cols;
		int numBits = 4;
		long outputSizeBits = outputSize * numBits;
		System.out.println("output size: "+out_rows+" x "+out_cols+" = "+outputSize);
		System.out.println("output bits: "+outputSizeBits);
		int maxSingleBuffer = (int)Math.pow(2, 30);
		System.out.println("max buffer size: "+maxSingleBuffer);
		System.out.println("multi buffers? "+(outputSizeBits > maxSingleBuffer));
		Preconditions.checkState(maxSingleBuffer < Integer.MAX_VALUE);
//		Preconditions.checkState(outputSizeBits <= Integer.MAX_VALUE, "File length ("+outputSizeBits+") must be < "+Integer.MAX_VALUE);
		
//		runTests(input_res);
//		System.exit(0);
		
		Map<FileIndex, List<IndexedLoc>> filesMap = Maps.newHashMap();
		
		System.out.println("Mapping output indexes to files.");
		int i = 0;
		for (double lat=regMinLat; (float)lat<=(float)regMaxLat; lat += output_res) {
			int j = 0;
//		for (int i=0; i<out_rows; i++) {
//			double lat = regMinLat + (double)i * output_res;
			
			FileIndex prevFile = null;
			int startJ = -1;
			short fileI = -1;
			List<Short> fileJs = Lists.newArrayList();
			for (double lon=regMinLon; (float)lon<=(float)regMaxLon; lon += output_res) {
//			for (int j=0; j<out_cols; j++) {
//				double lon = regMinLon + (double)j * output_res;
				FileIndex fileCoords = getFileForLoc(lat, lon);
				if (!filesMap.containsKey(fileCoords))
					filesMap.put(fileCoords, new ArrayList<NED_Convert.IndexedLoc>());
				
				short[] locInFile = getLocInFile(lat, lon, input_res);
				Preconditions.checkState(locInFile[0] < in_rows);
				Preconditions.checkState(locInFile[1] < in_cols);
				
				if (!fileCoords.equals(prevFile)) {
					if (prevFile != null) {
						Preconditions.checkState((j-1)-startJ+1 == fileJs.size());
						filesMap.get(prevFile).add(new IndexedLoc(i, startJ, j-1, fileI, Shorts.toArray(fileJs)));
					}
					
					prevFile = fileCoords;
					startJ = j;
					fileJs = Lists.newArrayList();
					fileI = locInFile[0];
				} else {
					Preconditions.checkState(fileI == locInFile[0], "? "+fileI +" != "+locInFile[0]);
				}
				
				fileJs.add(locInFile[1]);
				
				j++;
			}
			Preconditions.checkState(startJ >= 0);
			Preconditions.checkState((out_cols-1)-startJ+1 == fileJs.size(),
					"huh? out_cols="+out_cols+", j="+j+", startJ="+startJ+", fileJs.length="+fileJs.size());
			filesMap.get(prevFile).add(new IndexedLoc(i, startJ, (int)out_cols-1, fileI, Shorts.toArray(fileJs)));
			i++;
		}
		
		System.out.println("Writing file by file.");
		RandomAccessFile raOut = new RandomAccessFile(outputFile, "rw");
		
		long curSize = 0l;
		List<MappedByteBuffer> outBufs = Lists.newArrayList();
		List<Long> outEnds = Lists.newArrayList();
		while (curSize < outputSizeBits) {
			long prevSize = curSize;
			curSize += maxSingleBuffer;
			if (curSize > outputSizeBits)
				curSize = outputSizeBits;
			long subLen = curSize - prevSize;
			System.out.println("Mapping at "+prevSize+" of size "+subLen);
			outBufs.add(raOut.getChannel().map(MapMode.READ_WRITE, prevSize, subLen));
			outEnds.add(curSize);
		}
//		MappedByteBuffer[] outBufs = 
//		MappedByteBuffer outBuf = raOut.getChannel().map(MapMode.READ_WRITE, 0l, outputSizeBits);
		
		// now load/write one file at a time
		for (FileIndex fileIndices : filesMap.keySet()) {
			float[][] fileVals = loadFile(inputDir, fileIndices, in_rows, in_cols);
			
			for (IndexedLoc loc : filesMap.get(fileIndices)) {
				long index = numBits * (loc.i*out_cols + loc.jStart);
//				Preconditions.checkState(index <= Integer.MAX_VALUE);
				int len = 1 + loc.jEnd - loc.jStart;
				short fileI = loc.fileI;
				short[] fileJs = loc.fileJs;
				Preconditions.checkState(fileJs.length == len, "fileJ lengh wrong. fileJs.lengh = "
						+fileJs.length+", len="+len+", jStart="+loc.jStart+", jEnd="+loc.jEnd);
				
				for (short fileJ : fileJs) {
					Preconditions.checkState(index <= outputSizeBits, "file overrun: "+index+" > "+outputSizeBits);
					
					long prevEnd = 0;
					int relativeIndex = -1;
					MappedByteBuffer outBuf = null;
					for (int b=0; b<outBufs.size(); b++) {
						long end = outEnds.get(b);
						if (index < end) {
							relativeIndex = (int)(index - prevEnd);
							outBuf = outBufs.get(b);
							break;
						}
						prevEnd = end;
					}
					
					outBuf.putFloat(relativeIndex, fileVals[fileI][fileJ]);
					index += numBits;
				}
				
//				for (i=0; i<len; i++) {
//					int fileJ = loc.fileStartJ+i;
//					if (fileJ >= in_cols)
//						System.out.println(fileI+" "+fileJ+" "+i+" "+loc.fileStartJ);
//					outBuf.putFloat((int)index, fileVals[fileI][fileJ]);
//					index++;
//				}
			}
		}
		for (MappedByteBuffer outBuf : outBufs)
			outBuf.force();
		raOut.close();
	}
	
	private static void runTests(double input_res) {
		System.out.println("*** Lat Tests ***");
		test(36, -120, input_res);
		test(36-input_res, -120, input_res);
		test(36-2d*input_res, -120, input_res);
		test(36-3d*input_res, -120, input_res);
		
		System.out.println("*** Lon Tests ***");
		test(36, -120, input_res);
		test(36, -120+input_res, input_res);
		test(36, -120+2*input_res, input_res);
		test(36, -120+3*input_res, input_res);
	}
	private static void test(double lat, double lon, double input_res) {
		System.out.println("file for "+lat+", "+lon+": "+getFileForLoc(lat, lon));
		System.out.println("loc in file for "+lat+", "+lon+": "+getShortArrayStr(getLocInFile(lat, lon, input_res)));
	}
	
	private static String getShortArrayStr(short[] array) {
		return "["+array[0]+", "+array[1]+"]";
	}
	
	private static class FileIndex {
		short i, j;
		public FileIndex(short i, short j) {
			this.i = i;
			this.j = j;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + i;
			result = prime * result + j;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FileIndex other = (FileIndex) obj;
			if (i != other.i)
				return false;
			if (j != other.j)
				return false;
			return true;
		}
		
		public String toString() {
			return "["+i+","+j+"]";
		}
	}
	
	private static class IndexedLoc {
		int i, jStart, jEnd;
		short fileI;
		short[] fileJs;
		
		public IndexedLoc(int i, int jStart, int jEnd, short fileI, short[] fileJs) {
			super();
			this.i = i;
			this.jStart = jStart;
			this.jEnd = jEnd;
			this.fileI = fileI;
			this.fileJs = fileJs;
		}
	}
	
	private static short[] getLocInFile(double lat, double lon, double input_res) {
		if (lat == Math.floor(lat))
			lat = 0d;
		else
			lat = 1d - (lat - Math.floor(lat));
		Preconditions.checkState(lat >= 0d);
		Preconditions.checkState(lat <= 1d);
		lon = Math.abs(lon);
		lon = 1d - (lon - Math.floor(lon));
		Preconditions.checkState(lon >= 0d);
		Preconditions.checkState(lat <= 1d);
		
		double latIndex = lat/input_res;
		double lonIndex = lon/input_res;
		
		short i = (short)((short)Math.round(latIndex) + buffer);
		short j = (short)((short)Math.round(lonIndex) + buffer);
		return new short[] {i,j};
	}
	
	private static FileIndex getFileForLoc(double lat, double lon) {
//		double lat = loc.getLatitude();
//		double lon = loc.getLongitude();
		return new FileIndex((short)Math.ceil(lat), (short)Math.ceil(Math.abs(lon)));
	}
	
	private static float[][] loadFile(File dir, FileIndex indexes, short rows, short cols) throws ZipException, IOException {
		String jStr = indexes.j+"";
		if (indexes.j < 100)
			jStr = "0"+jStr;
		String regName = "n"+indexes.i+"w"+jStr;
		
		float[][] ret = new float[rows][cols];
		
		File zipFile = new File(dir, regName+".zip");
		if (!zipFile.exists()) {
			System.out.println("Zip file '"+zipFile.getAbsolutePath()+"' doesn't exist, skipping");
			for (int i=0; i<rows; i++)
				for (int j=0; j<cols; j++)
					ret[i][j] = Float.NaN;
			return ret; // all NaNs
		}
		
		ZipFile zip = new ZipFile(zipFile);
		String entryName = regName+"/float"+regName+"_1.flt";
		System.out.println("Reading "+entryName+" from "+zipFile.getAbsolutePath());
		ZipEntry entry = zip.getEntry(entryName);
		InputStream is = zip.getInputStream(entry);
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(new BufferedInputStream(is));
		
		for (int i=0; i<rows; i++) {
			for (int j=0; j<rows; j++) {
				float val = dis.readFloat();
				if (val == -9999f)
					val = Float.NaN;
				ret[i][j] = val;
			}
		}
		dis.close();
		
		return ret;
	}

}
