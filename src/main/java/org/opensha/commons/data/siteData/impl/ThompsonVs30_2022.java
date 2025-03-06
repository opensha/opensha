package org.opensha.commons.data.siteData.impl;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.dom4j.Element;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;
import org.opensha.commons.util.binFile.GeolocatedRectangularBinaryMesh2DCalculator;

import com.google.common.base.Preconditions;
import com.google.common.io.LittleEndianDataInputStream;

public class ThompsonVs30_2022 extends AbstractSiteData<Double> {
	
	public static final int nx = 12483;	// NCOLS
	public static final int ny = 11523;	// NROWS
	
	public static final double spacingX = 0.000833333300008011;
	public static final double spacingY = 0.000833333300008678;
	
	public static final double startLon = -124.50062500035;	// ULXMAP
	public static final double startLat = 42.10062500035;	// ULYMAP
	
	public static final boolean startBottom = false;
	public static final boolean startLeft = true;
	
	public static final String NAME = "Thompson VS30 Map (2022)";
	public static final String SHORT_NAME = "Thompson2022";
	
	public static final String SERVER_BIN_FILE = 
			ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()
				+File.separator+"siteData"+File.separator+"thompson_vs30"+File.separator
				+"2022_07_12"+File.separator+"California_vs30_Wills15_hybrid.flt";
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL()
			+"SiteData/Thompson2022";
	
	private Region applicableRegion;
	
	private RandomAccessFile file = null;
	private String fileName = null;
	private byte[] recordBuffer = null;
	private FloatBuffer floatBuff = null;
	
	private GeolocatedRectangularBinaryMesh2DCalculator calc = null;
	
	private boolean useServlet;
	
	private SiteDataServletAccessor<Double> servlet = null;
	
	public ThompsonVs30_2022() throws IOException {
		this(true, null);
//		this(false, "/home/kevin/OpenSHA/wills_2015/raster_0.0005.flt");
	}
	
	public ThompsonVs30_2022(String dataFile) throws IOException {
		this(false, dataFile);
	}
	
	private ThompsonVs30_2022(boolean useServlet, String dataFile) throws IOException {
		this(useServlet, dataFile, new GeolocatedRectangularBinaryMesh2DCalculator(
				DataType.FLOAT, nx, ny, startLat, startLon, startBottom, startLeft, spacingX, spacingY));
	}
	
	public ThompsonVs30_2022(String dataFile, GeolocatedRectangularBinaryMesh2DCalculator calc)
			throws IOException {
		this(false, dataFile, calc);
	}
	
	private ThompsonVs30_2022(boolean useServlet, String dataFile, GeolocatedRectangularBinaryMesh2DCalculator calc)
			throws IOException {
		super();
		this.useServlet = useServlet;
		this.fileName = dataFile;
		
		this.calc = calc;
		
		applicableRegion = calc.getApplicableRegion();
		
		if (useServlet) {
			servlet = new SiteDataServletAccessor<Double>(this, SERVLET_URL);
		} else {
			file = new RandomAccessFile(new File(dataFile), "r");
			
			recordBuffer = new byte[4];
			Preconditions.checkState((calc.getMaxFilePos()+recordBuffer.length) == file.length(),
					"Mesh calculator and file inconsistent: calc.getMaxFilePos()=%s, file.length()=%s", calc.getMaxFilePos(), file.length());
			ByteBuffer record = ByteBuffer.wrap(recordBuffer);
			record.order(ByteOrder.LITTLE_ENDIAN);
			floatBuff = record.asFloatBuffer();
		}
		initDefaultVS30Params();
		this.paramList.addParameter(minVs30Param);
		this.paramList.addParameter(maxVs30Param);
	}

	public Region getApplicableRegion() {
		return applicableRegion;
	}

	public Location getClosestDataLocation(Location loc) {
		return calc.calcClosestLocation(loc);
	}

	public String getName() {
		return NAME;
	}
	
	public String getShortName() {
		return SHORT_NAME;
	}
	
	public String getMetadata() {
		return "Vs30 values as described in:\n\n" +
				"Thompson, E.M., 2018, An Updated Vs30 Map for California with Geologic and Topographic Constraints: "
				+ "U.S. Geological Survey data release, https://doi.org/10.5066/F7JQ108S.\n"
				+ "Data converted from GeoTIFF to FLT with the 'gdal_translate' linux command, GDAL 2.3.2. "
				+ "It has a grid spacing of "+0.5*(spacingX + spacingY)+" degrees. This is v2 (2022) from the "
				+ "updated ScienceBase release: https://www.sciencebase.gov/catalog/item/5a5fa029e4b06e28e9bfc43a";
	}

	public double getResolution() {
		return 0.5d*(spacingX + spacingY);
	}

	public String getDataType() {
		return TYPE_VS30;
	}
	
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}

	public Double getValue(Location loc) throws IOException {
		if (useServlet) {
			return certifyMinMaxVs30(servlet.getValue(loc));
		} else {
			long pos = calc.calcClosestLocationFileIndex(loc);
			
			if (pos < 0 || pos > calc.getMaxFilePos())
				return Double.NaN;
			
			file.seek(pos);
			file.read(recordBuffer);
			
			double val = floatBuff.get(0);
			
			if ((float)val <= 0f || Double.isNaN(val))
				return Double.NaN;
			
			// round to get rid of float nastiness
			Preconditions.checkState(Double.isNaN(val) || val > 0, "Bad value! %s", val);
			double roundVal = Math.round(val*100d)/100d;
			Preconditions.checkState(Double.isNaN(roundVal) || roundVal > 0, "Bad rounded value: %s (orig: %s)", roundVal, val);
			double boundsVal = certifyMinMaxVs30((double)roundVal);
			Preconditions.checkState(Double.isNaN(boundsVal) || boundsVal > 0, "Bad bounds val! %s (rounded: %s, orig %s)", boundsVal, roundVal, val);
			return roundVal;
		}
	}

	public ArrayList<Double> getValues(LocationList locs) throws IOException {
		if (useServlet) {
			ArrayList<Double> vals = servlet.getValues(locs);
			for (int i=0; i<vals.size(); i++) {
				vals.set(i, certifyMinMaxVs30(vals.get(i)));
			}
			return vals;
		} else {
			return super.getValues(locs);
		}
	}
	
	public boolean isValueValid(Double val) {
		return val != null && !Double.isNaN(val) && val > 0d;
	}
	
	@Override
	protected Element addXMLParameters(Element paramsEl) {
		paramsEl.addAttribute("useServlet", this.useServlet + "");
		paramsEl.addAttribute("fileName", this.fileName);
		return super.addXMLParameters(paramsEl);
	}
	
	public static ThompsonVs30_2022 fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		String fileName = paramsElem.attributeValue("fileName");
		
		return new ThompsonVs30_2022(useServlet, fileName);
	}
	
	public static void main(String[] args) throws IOException {
		
		ThompsonVs30_2022 map = new ThompsonVs30_2022(
				"/tmp/California_vs30_Wills15_hybrid.flt");
		
		long pos = 0;
		double min = Double.POSITIVE_INFINITY;
		double max = 0d;
		DataInput din = new LittleEndianDataInputStream(new BufferedInputStream(new FileInputStream(new File(map.fileName))));
		int numRead = 0;
		int numValid = 0;
		while (true) {
			double val;
			try {
				val = din.readFloat();
			} catch (IOException e) {
				break;
			}
//			System.out.println("read "+val+" at pos="+pos+" (len="+map.file.length()+")");
			numRead++;
			if (map.isValueValid(val)) {
				numValid++;
				min = Math.min(min, val);
				max = Math.max(max, val);
			}
		}
		System.out.println("Read "+numRead+" values, "+numValid+" were valid");
		System.out.println("Range: ["+(float)min+", "+(float)max+"]");
		
//		ThompsonVs30_2022 map = new ThompsonVs30_2022();
		
//		GriddedRegion region = 
//			new GriddedRegion(
//					new Location(37, -122.75),
//					new Location(38.5, -121.5),
//					0.01, new Location(0,0));
		
//		SiteDataToXYZ.writeXYZ(map, region, "/tmp/wills.txt");
		
//		SiteDataServletAccessor<Double> serv = new SiteDataServletAccessor<Double>(SERVLET_URL);
//		
		LocationList locs = new LocationList();
		locs.add(new Location(34.01920, -118.28800));
		locs.add(new Location(34.91920, -118.3200));
		locs.add(new Location(34.781920, -118.88600));
		locs.add(new Location(34.21920, -118.38600));
		locs.add(new Location(34.61920, -118.18600));
		
		ArrayList<Double> vals = map.getValues(locs);
		for (double val : vals)
			System.out.println(val);
	}

}
