package scratch.UCERF3.erf.ETAS;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Range;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

public class ETAS_CatalogIO {

	/*
	 * ASCII I/O
	 */

	public static SimpleDateFormat catDateFormat = new SimpleDateFormat("yyyy\tMM\tdd\tHH\tmm\tss.SSS");
	public static final TimeZone utc = TimeZone.getTimeZone("UTC");
	static {
		catDateFormat.setTimeZone(utc);
	}

	/**
	 * This writes simulated event data to a file.
	 */
	public static void writeEventDataToFile(File file, Collection<ETAS_EqkRupture> simulatedRupsQueue)
			throws IOException {
		FileWriter fw1 = new FileWriter(file);
		if (simulatedRupsQueue instanceof ETAS_Catalog && ((ETAS_Catalog)simulatedRupsQueue).getSimulationMetadata() != null) {
			writeMetadataToFile(fw1, ((ETAS_Catalog)simulatedRupsQueue).getSimulationMetadata());
			fw1.write("% \n");
		}
		ETAS_CatalogIO.writeEventHeaderToFile(fw1);
		for(ETAS_EqkRupture rup:simulatedRupsQueue) {
			ETAS_CatalogIO.writeEventToFile(fw1, rup);
		}
		fw1.close();
	}

	/**
	 * This writes the header associated with the writeEventDataToFile(*) method
	 * @param fileWriter
	 * @throws IOException
	 */
	public static void writeEventHeaderToFile(FileWriter fileWriter) throws IOException {
		// OLD FORMAT
		//		fileWriter.write("# nthERFIndex\tID\tparID\tGen\tOrigTime\tdistToParent\tMag\tLat\tLon\tDep\tFSS_ID\tGridNodeIndex\n");
		// NEW FORMAT: Year Month Day Hour Minute Sec Lat Long Depth Magnitude id parentID gen origTime
		// 				distToParent nthERF fssIndex gridNodeIndex
		fileWriter.write("% "+EVENT_FILE_HEADER+"\n");
	}
	
	public static final String EVENT_FILE_HEADER = "Year\tMonth\tDay\tHour\tMinute\tSec\tLat\tLon\tDepth\tMagnitude\t"
				+ "ID\tparID\tGen\tOrigTime\tdistToParent\tnthERFIndex\tFSS_ID\tGridNodeIndex\tETAS_k";
	
	public static void writeMetadataToFile(FileWriter fw, ETAS_SimulationMetadata meta) throws IOException {
		fw.write("% ------------ METADATA -------------\n");
		fw.write("% numRuptures = "+meta.totalNumRuptures+"\n");
		fw.write("% randomSeed = "+meta.randomSeed+"\n");
		if (meta.catalogIndex >= 0)
			fw.write("% catalogIndex = "+meta.catalogIndex+"\n");
		if (meta.rangeHistCatalogIDs != null)
			fw.write("% rangeHistCatalogIDs = ["+meta.rangeHistCatalogIDs.lowerEndpoint()
				+" "+meta.rangeHistCatalogIDs.upperEndpoint()+"]\n");
		if (meta.rangeTriggerRupIDs != null)
			fw.write("% triggerRupParentIDs = ["+meta.rangeTriggerRupIDs.lowerEndpoint()
				+" "+meta.rangeTriggerRupIDs.upperEndpoint()+"]\n");
		fw.write("% simulationStartTime = "+meta.simulationStartTime+"\n");
		fw.write("% simulationEndTime = "+meta.simulationEndTime+"\n");
		fw.write("% numSpontaneousRuptures = "+meta.numSpontaneousRuptures+"\n");
		fw.write("% numSupraSeis = "+meta.numSupraSeis+"\n");
		fw.write("% minMag = "+meta.minMag+"\n");
		fw.write("% maxMag = "+meta.maxMag+"\n");
		fw.write("% -----------------------------------\n");
	}

	/**
	 * This writes the given rupture to the given fileWriter
	 * @param fileWriter
	 * @param rup
	 * @throws IOException
	 */
	public static void writeEventToFile(FileWriter fileWriter, ETAS_EqkRupture rup) throws IOException {
		fileWriter.write(getEventFileLine(rup)+"\n");
	}
	
	public static String getEventFileLine(ETAS_EqkRupture rup) {
		Location hypoLoc = rup.getHypocenterLocation();

		// OLD FORMAT: nthERF id parentID gen origTime distToParent mag lat lon depth [fssIndex gridNodeIndex]
		//		fileWriter.write(rup.getNthERF_Index()+"\t"+rup.getID()+"\t"+rup.getParentID()+"\t"+rup.getGeneration()+"\t"+
		//					rup.getOriginTime()+"\t"+rup.getDistanceToParent()
		//					+"\t"+rup.getMag()+"\t"+hypoLoc.getLatitude()+"\t"+hypoLoc.getLongitude()+"\t"+hypoLoc.getDepth()
		//					+"\t"+rup.getFSSIndex()+"\t"+rup.getGridNodeIndex()+"\n");

		// NEW FORMAT: Year Month Day Hour Minute Sec Lat Long Depth Magnitude id parentID gen origTime
		// 				distToParent nthERF fssIndex gridNodeIndex
		StringBuilder sb = new StringBuilder();
		synchronized (ETAS_CatalogIO.class) {
			// SimpleDateFormat is NOT synchronized and maintains an internal calendar
			sb.append(catDateFormat.format(rup.getOriginTimeCal().getTime())).append("\t");
		}
		if(hypoLoc != null) {
			sb.append((float)hypoLoc.getLatitude()).append("\t");
			sb.append((float)hypoLoc.getLongitude()).append("\t");
			sb.append((float)hypoLoc.getDepth()).append("\t");			
		}
		else {
			sb.append("null").append("\t");
			sb.append("null").append("\t");
			sb.append("null").append("\t");
		}
		sb.append((float)rup.getMag()).append("\t");
		sb.append(rup.getID()).append("\t");
		sb.append(rup.getParentID()).append("\t");
		sb.append(rup.getGeneration()).append("\t");
		sb.append(rup.getOriginTime()).append("\t");
		sb.append((float)rup.getDistanceToParent()).append("\t");
		sb.append(rup.getNthERF_Index()).append("\t");
		sb.append(rup.getFSSIndex()).append("\t");
		sb.append(rup.getGridNodeIndex()).append("\t");
		sb.append(rup.getETAS_k());
		
		return sb.toString();
	}

	/**
	 * This loads an ETAS rupture from a line of an ETAS catalog text file.
	 * 
	 * @param line
	 * @return
	 */
	public static ETAS_EqkRupture loadRuptureFromFileLine(String line) {
		line = line.trim();

		String[] split = line.split("\t");
		Preconditions.checkState(split.length == 10 || split.length == 12 || split.length == 18 || split.length == 19,
				"Line has unexpected number of items. Expected 10/12/18/19, got %s. Line: %s", split.length, line);

		int nthERFIndex, fssIndex, gridNodeIndex, id, parentID, gen;
		long origTime;
		double distToParent, mag, lat, lon, depth, k;

		if (split.length == 10 || split.length == 12) {
			// old format

			// nthERF id parentID gen origTime distToParent mag lat lon depth [fssIndex gridNodeIndex]

			nthERFIndex = Integer.parseInt(split[0]);
			id = Integer.parseInt(split[1]);
			parentID = Integer.parseInt(split[2]);
			gen = Integer.parseInt(split[3]);
			origTime = Long.parseLong(split[4]);
			distToParent = Double.parseDouble(split[5]);
			mag = Double.parseDouble(split[6]);
			lat = Double.parseDouble(split[7]);
			lon = Double.parseDouble(split[8]);
			depth = Double.parseDouble(split[9]);

			if (split.length == 12) {
				// has FSS and grid node indexes
				fssIndex = Integer.parseInt(split[10]);
				gridNodeIndex = Integer.parseInt(split[11]);
			} else {
				fssIndex = -1;
				gridNodeIndex = -1;
			}
			k = Double.NaN;
		} else {
			// new format

			// Year Month Day Hour Minute Sec Lat Long Depth Magnitude id parentID gen origTime
			// 			distToParent nthERF fssIndex gridNodeIndex

			// skip year/month/day/hour/min/sec, use epoch seconds
			lat = Double.parseDouble(split[6]);
			lon = Double.parseDouble(split[7]);
			depth = Double.parseDouble(split[8]);
			mag = Double.parseDouble(split[9]);
			id = Integer.parseInt(split[10]);
			parentID = Integer.parseInt(split[11]);
			gen = Integer.parseInt(split[12]);
			origTime = Long.parseLong(split[13]);
			distToParent = Double.parseDouble(split[14]);
			nthERFIndex = Integer.parseInt(split[15]);
			fssIndex = Integer.parseInt(split[16]);
			gridNodeIndex = Integer.parseInt(split[17]);
			if (split.length == 19)
				k = Double.parseDouble(split[18]);
			else
				k = Double.NaN;
		}

		Location loc = new Location(lat, lon, depth);

		ETAS_EqkRupture rup = new ETAS_EqkRupture();

		rup.setNthERF_Index(nthERFIndex);
		rup.setID(id);
		rup.setParentID(parentID);
		rup.setGeneration(gen);
		rup.setOriginTime(origTime);
		rup.setDistanceToParent(distToParent);
		rup.setMag(mag);
		rup.setHypocenterLocation(loc);
		rup.setFSSIndex(fssIndex);
		rup.setGridNodeIndex(gridNodeIndex);
		rup.setETAS_k(k);

		return rup;
	}

	/**
	 * Loads an ETAS catalog from the given text catalog file
	 * 
	 * @param catalogFile
	 * @return
	 * @throws IOException
	 */
	public static ETAS_Catalog loadCatalog(File catalogFile) throws IOException {
		return ETAS_CatalogIO.loadCatalog(catalogFile, -10d);
	}

	private static boolean isBinary(File file) {
		String name = file.getName().toLowerCase();
		if (name.endsWith(".bin") || name.endsWith(".gz"))
			return true;
		return false;
	}

	/**
	 * Loads an ETAS catalog from the given text catalog file. Only ruptures with magnitudes greater than or equal
	 * to minMag will be returned.
	 * 
	 * @param catalogFile
	 * @param minMag
	 * @return
	 * @throws IOException
	 */
	public static ETAS_Catalog loadCatalog(File catalogFile, double minMag) throws IOException {
		return loadCatalog(catalogFile, minMag, false);
	}

	public static ETAS_Catalog loadCatalog(File catalogFile, double minMag, boolean ignoreFailure)
			throws IOException {
		if (isBinary(catalogFile))
			return loadCatalogBinary(catalogFile, minMag);
		ETAS_Catalog catalog = new ETAS_Catalog(null);
		Map<String, String> metaValues = new HashMap<>();
		for (String line : Files.readLines(catalogFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty())
				continue;
			if (line.startsWith("%")) {
				if (line.contains(" = ")) { // metadata line
					String[] split = line.split(" = ");
					if (split.length != 2)
						System.err.println("Thought we had a metadata line but split.lengh="+split.length+" for: "+line);
					metaValues.put(split[0].replaceAll("%", "").trim(), split[1].trim());
				}
				continue;
			}
			ETAS_EqkRupture rup;
			try {
				rup = loadRuptureFromFileLine(line);
			} catch (RuntimeException e) {
				if (ignoreFailure) {
					System.err.println("Warning, skipping line: "+e.getMessage()+"\n\tFile: "+catalogFile.getAbsolutePath());
					continue;
				}
				else throw e;
			}
			if (rup.getMag() >= minMag)
				catalog.add(rup);
		}
		
		if (!metaValues.isEmpty()) {
			// load metadata
			catalog.meta = loadMetadataASCII(metaValues);
		}
		return catalog;
	}
	
	private static ETAS_SimulationMetadata loadMetadataASCII(Map<String, String> metaValues) {
		int totalNumRuptures = metaValues.containsKey("totalNumRuptures") ? Integer.parseInt(metaValues.get("numRuptures")) : -1;
		long randomSeed = metaValues.containsKey("randomSeed") ? Long.parseLong(metaValues.get("randomSeed")) : -1l;
		int catalogIndex = metaValues.containsKey("catalogIndex") ? Integer.parseInt(metaValues.get("catalogIndex")) : -1;
		Range<Integer> rangeHistCatalogIDs = metaValues.containsKey("rangeHistCatalogIDs")
				? loadRangeASCII(metaValues.get("rangeHistCatalogIDs")) : null;
		Range<Integer> rangeTriggerRupIDs = metaValues.containsKey("triggerRupParentIDs")
				? loadRangeASCII(metaValues.get("triggerRupParentIDs")) : null;
		long simulationStartTime = metaValues.containsKey("simulationStartTime")
				? Long.parseLong(metaValues.get("simulationStartTime")) : -1l;
		long simulationEndTime = metaValues.containsKey("simulationEndTime")
				? Long.parseLong(metaValues.get("simulationEndTime")) : -1l;
		int numSpontaneousRuptures = metaValues.containsKey("numSpontaneousRuptures")
				? Integer.parseInt(metaValues.get("numSpontaneousRuptures")) : -1;
		int numSupraSeis = metaValues.containsKey("numSupraSeis")
				? Integer.parseInt(metaValues.get("numSupraSeis")) : -1;
		double minMag = metaValues.containsKey("minMag") ? Double.parseDouble(metaValues.get("minMag")) : Double.NaN;
		double maxMag = metaValues.containsKey("maxMag") ? Double.parseDouble(metaValues.get("maxMag")) : Double.NaN;
		return ETAS_SimulationMetadata.instance(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs, rangeTriggerRupIDs,
				simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
	}
	
	private static Range<Integer> loadRangeASCII(String valStr) {
		Preconditions.checkState(valStr.startsWith("[") && valStr.endsWith("]"));
		valStr = valStr.substring(1, valStr.length()-1);
		String[] split = valStr.split(" ");
		return Range.closed(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
	}

	/**
	 * Loads an ETAS catalog from the given text catalog file input stream.
	 * 
	 * @param catalogStream
	 * @return
	 * @throws IOException
	 */
	public static List<ETAS_EqkRupture> loadCatalog(InputStream catalogStream) throws IOException {
		return ETAS_CatalogIO.loadCatalog(catalogStream, -10d);
	}

	/**
	 * Loads an ETAS catalog from the given text catalog file input stream. Only ruptures with magnitudes greater
	 * than or equal to minMag will be returned.
	 * 
	 * @param catalogStream
	 * @param minMag
	 * @return
	 * @throws IOException
	 */
	public static ETAS_Catalog loadCatalog(InputStream catalogStream, double minMag) throws IOException {
		return loadCatalog(	catalogStream, minMag, false);
	}
	
	public static ETAS_Catalog loadCatalog(InputStream catalogStream, double minMag, boolean ignoreFailure)
			throws IOException {
		ETAS_Catalog catalog = new ETAS_Catalog(null);
		BufferedReader reader = new BufferedReader(new InputStreamReader(catalogStream));

		Map<String, String> metaValues = new HashMap<>();
		for (String line : CharStreams.readLines(reader)) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty())
				continue;
			if (line.startsWith("%")) {
				if (line.contains(" = ")) { // metadata line
					String[] split = line.split(" = ");
					if (split.length != 2)
						System.err.println("Thought we had a metadata line but split.lengh="+split.length+" for: "+line);
					metaValues.put(split[0].replaceAll("%", "").trim(), split[1].trim());
				}
				continue;
			}
			try {
				ETAS_EqkRupture rup = loadRuptureFromFileLine(line);
				if (rup.getMag() >= minMag)
					catalog.add(rup);
			} catch (RuntimeException e) {
				if (ignoreFailure) {
					System.err.println("Warning, skipping line: "+e.getMessage());
					continue;
				}
				else throw e;
			}
		}
		
		if (!metaValues.isEmpty()) {
			// load metadata
			catalog.meta = loadMetadataASCII(metaValues);
		}
		return catalog;
	}

	/*
	 * Binary I/O
	 */

	public static void writeCatalogBinary(File file, List<ETAS_EqkRupture> catalog) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkNotNull(catalog, "Catalog cannot be null!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), buffer_len));

		writeCatalogBinary(out, catalog);

		out.close();
	}

	public static void writeCatalogsBinary(File file, List<? extends List<ETAS_EqkRupture>> catalogs) throws IOException {
		Preconditions.checkNotNull(catalogs, "Catalog cannot be null!");

		DataOutputStream out = initCatalogsBinary(file, catalogs.size());

		for (List<ETAS_EqkRupture> catalog : catalogs)
			writeCatalogBinary(out, catalog);

		out.close();
	}
	
	public static DataOutputStream initCatalogsBinary(File file, int numCatalogs) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(numCatalogs > 0, "Must supply at least one catalog");
		
		FileOutputStream fout = new FileOutputStream(file);
		Preconditions.checkArgument(numCatalogs > 0, "Must supply at least one catalog");
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fout, buffer_len));

		// write number of catalogs as int
		out.writeInt(numCatalogs);
		
		return out;
	}
	
	private static Map<Integer, Long> binaryVersionRuptureLengthMap;
	private static Map<Integer, Long> binaryVersionHeaderLengthMap;
	
	static {
		binaryVersionRuptureLengthMap = new HashMap<>();
		binaryVersionRuptureLengthMap.put(1, 70l);
		binaryVersionRuptureLengthMap.put(2, 78l);
		binaryVersionRuptureLengthMap.put(3, 78l);
		binaryVersionHeaderLengthMap = new HashMap<>();
		binaryVersionHeaderLengthMap.put(1, 6l);
		binaryVersionHeaderLengthMap.put(2, 6l);
		binaryVersionHeaderLengthMap.put(3, 78l);
	}
	
	public static long getCatalogLengthBytes(int numRuptures, int version, boolean includeHeader) {
		long ret = (long)numRuptures*binaryVersionRuptureLengthMap.get(version);
		if (includeHeader)
			ret += binaryVersionHeaderLengthMap.get(version);
		return ret;
	}

	public static void writeCatalogBinary(DataOutputStream out, List<ETAS_EqkRupture> catalog) throws IOException {
		// write file version as short
		if (catalog instanceof ETAS_Catalog && ((ETAS_Catalog)catalog).getSimulationMetadata() != null) {
			// we have metadata
			out.writeShort(3);
			ETAS_SimulationMetadata meta = ((ETAS_Catalog)catalog).getSimulationMetadata();
			out.writeInt(meta.totalNumRuptures);
			out.writeLong(meta.randomSeed);
			out.writeInt(meta.catalogIndex);
			if (meta.rangeHistCatalogIDs == null) {
				out.writeInt(-1);
				out.writeInt(-1);
			} else {
				out.writeInt(meta.rangeHistCatalogIDs.lowerEndpoint());
				out.writeInt(meta.rangeHistCatalogIDs.upperEndpoint());
			}
			if (meta.rangeTriggerRupIDs == null) {
				out.writeInt(-1);
				out.writeInt(-1);
			} else {
				out.writeInt(meta.rangeTriggerRupIDs.lowerEndpoint());
				out.writeInt(meta.rangeTriggerRupIDs.upperEndpoint());
			}
			out.writeLong(meta.simulationStartTime);
			out.writeLong(meta.simulationStartTime);
			out.writeInt(meta.numSpontaneousRuptures);
			out.writeInt(meta.numSupraSeis);
			out.writeDouble(meta.minMag);
			out.writeDouble(meta.maxMag);
		} else {
			// no metadata
			out.writeShort(2);
		}
		// write catalog size as int
		out.writeInt(catalog.size());

		// text fields: Year\tMonth\tDay\tHour\tMinute\tSec\tLat\tLon\tDepth\tMagnitude\t"
		// "ID\tparID\tGen\tOrigTime\tdistToParent\tnthERFIndex\tFSS_ID\tGridNodeIndex

		// binary format:
		// id - int
		// parent id - int
		// generation - short
		// origin time - long
		// latitude - double
		// longitude - double
		// depth - double
		// magnitude - double
		// distance to parent - double
		// nth ERF index - int
		// FSS index - int
		// grid node index - int
		// [version 2+] etas k - double

		for (ETAS_EqkRupture rup : catalog) {
			out.writeInt(rup.getID());
			out.writeInt(rup.getParentID());
			out.writeShort(rup.getGeneration());
			out.writeLong(rup.getOriginTime());
			Location hypo = rup.getHypocenterLocation();
			out.writeDouble(hypo.getLatitude());
			out.writeDouble(hypo.getLongitude());
			out.writeDouble(hypo.getDepth());
			out.writeDouble(rup.getMag());
			out.writeDouble(rup.getDistanceToParent());
			out.writeInt(rup.getNthERF_Index());
			out.writeInt(rup.getFSSIndex());
			out.writeInt(rup.getGridNodeIndex());
			out.writeDouble(rup.getETAS_k());
		}
	}

	public static ETAS_Catalog loadCatalogBinary(File file) throws IOException {
		return loadCatalogBinary(file, -10d);
	}

	public static ETAS_Catalog loadCatalogBinary(File file, double minMag) throws IOException {
		return loadCatalogBinary(getIS(file), minMag);
	}

	public static ETAS_Catalog loadCatalogBinary(InputStream is, double minMag) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		ETAS_Catalog catalog = doLoadCatalogBinary(in, minMag);

		in.close();

		return catalog;
	}

	public static List<ETAS_Catalog> loadCatalogsBinary(File file) throws IOException {
		return loadCatalogsBinary(file, -10d);
	}

	public static List<ETAS_Catalog> loadCatalogsBinary(File file, double minMag) throws IOException {
		return loadCatalogsBinary(getIS(file), minMag);
	}

	public static final int buffer_len = 6553600;

	private static InputStream getIS(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		FileInputStream fis = new FileInputStream(file);

		if (file.getName().toLowerCase().endsWith(".gz"))
			return new GZIPInputStream(fis, buffer_len);
		return new BufferedInputStream(fis, buffer_len);
	}

	public static List<ETAS_Catalog> loadCatalogsBinary(InputStream is, double minMag) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream) && !(is instanceof GZIPInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		List<ETAS_Catalog> catalogs = new ArrayList<>();

		int numCatalogs = in.readInt();
		int printMod = 1000;
		if (numCatalogs >= 100000)
			printMod = 10000;

		Preconditions.checkState(numCatalogs > 0, "Bad num catalogs: %s", numCatalogs);

		for (int i=0; i<numCatalogs; i++) {
			catalogs.add(doLoadCatalogBinary(in, minMag));
			if ((i+1) % printMod == 0)
				System.out.println("Loaded "+(i+1)+"/"+numCatalogs+" catalogs (and counting)...");
		}
		System.out.println("Loaded "+catalogs.size()+" catalogs");

		in.close();

		return catalogs;
	}
	
	public static boolean isBinaryCatalogFileComplete(File eventsFile) {
		try {
			InputStream is = getIS(eventsFile);
			DataInputStream in = new DataInputStream(is);
			short version = in.readShort();
			readBinaryMetadata(in, version);
			int numRuptures = in.readInt();
			if (is instanceof GZIPInputStream)
				// assume that since it's gzipped and we got this far, we're good
				return true;
			long calcLen = getCatalogLengthBytes(numRuptures, version, true);
			return eventsFile.length() == calcLen;
		} catch (IOException e) {
			System.err.println("Error reading binary file, assuming not complete: "+e.getMessage());
		}
		return false;
	}
	
	public static ETAS_SimulationMetadata readBinaryMetadata(DataInput in, short version) throws IOException {
		Preconditions.checkState(version >= 1 && version <= 3, "Bad version=%s", version);
		if (version == 3) {
			int totalNumRuptures = in.readInt();
			Preconditions.checkState(totalNumRuptures >= -1, "Bad numRuptures=%s", totalNumRuptures);
			long randomSeed = in.readLong();
			int catalogIndex = in.readInt();
			Preconditions.checkState(catalogIndex >= -1, "Bad catalogIndex=%s", catalogIndex);
			Range<Integer> rangeHistCatalogIDs = null;
			int startHistID = in.readInt();
			Preconditions.checkState(startHistID >= -1, "Bad startHistID=%s", startHistID);
			int endHistID = in.readInt();
			Preconditions.checkState(endHistID >= startHistID, "Bad endHistID=%s", endHistID);
			if (startHistID >= 0)
				rangeHistCatalogIDs = Range.closed(startHistID, endHistID);
			Range<Integer> rangeTriggerRupIDs = null;
			int startTriggerID = in.readInt();
			Preconditions.checkState(startTriggerID >= -1, "Bad startTriggerID=%s", startTriggerID);
			int endTriggerID = in.readInt();
			Preconditions.checkState(endTriggerID >= startTriggerID, "Bad endTriggerID=%s", endTriggerID);
			if (startTriggerID >= 0)
				rangeTriggerRupIDs = Range.closed(startTriggerID, endTriggerID);
			long simulationStartTime= in.readLong();
			Preconditions.checkState(simulationStartTime >= 0, "Bad simulationStartTime=%s", simulationStartTime);
			long simulationEndTime= in.readLong();
			Preconditions.checkState(simulationEndTime >= simulationStartTime, "Bad simulationEndTime=%s", simulationEndTime);
			int numSpontaneousRuptures = in.readInt();
			Preconditions.checkState(numSpontaneousRuptures >= 0, "Bad numSpontaneousRuptures=%s", numSpontaneousRuptures);
			int numSupraSeis = in.readInt();
			Preconditions.checkState(numSupraSeis >= 0, "Bad numSupraSeis=%s", numSupraSeis);
			double minMag = in.readDouble();
			double maxMag = in.readDouble();
			return ETAS_SimulationMetadata.instance(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs, rangeTriggerRupIDs,
					simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
		}
		return null;
	}

	private static ETAS_Catalog doLoadCatalogBinary(DataInput in, double minMag) throws IOException {
		short version = in.readShort();

		Preconditions.checkState(version == 1 || version == 2 || version == 3, "Unknown binary file version: "+version);
		
		ETAS_SimulationMetadata meta = readBinaryMetadata(in, version);
		if (meta != null) {
			double metaMinMag = meta.minMag;
			if (minMag > metaMinMag || (minMag > 0 && !Double.isFinite(metaMinMag)))
				// if we're loading this in at a higher minMag, use that
				meta = meta.getModMinMag(minMag);
		}

		int numRups = in.readInt();

		Preconditions.checkState(numRups >= 0, "Bad num rups: "+numRups);

		ETAS_Catalog catalog = new ETAS_Catalog(meta);

		for (int i=0; i<numRups; i++) {
			int id = in.readInt();
			Preconditions.checkState(id >= 0);
			int parentID = in.readInt();
			Preconditions.checkState(id >= -1);
			int gen = in.readShort();
			Preconditions.checkState(id >= 0);
			long origTime = in.readLong();
			// loc will be validated on instantiation
			double lat = in.readDouble();
			double lon = in.readDouble();
			double depth = in.readDouble();
			double mag = in.readDouble();
			Preconditions.checkState(mag >= 0 && mag < 10, "Bad Mag: %s", mag);
			double distToParent = in.readDouble();
			Preconditions.checkState(Double.isNaN(distToParent) || distToParent >= 0, "bad dist to parent: %s", distToParent);
			int nthERFIndex = in.readInt();
			Preconditions.checkState(nthERFIndex >= -1, "Bad Grid Node Index: %s", nthERFIndex);
			int fssIndex = in.readInt();
			Preconditions.checkState(fssIndex >= -1, "Bad Grid Node Index: %s", fssIndex);
			int gridNodeIndex = in.readInt();
			Preconditions.checkState(gridNodeIndex >= -1, "Bad Grid Node Index: %s", gridNodeIndex);
			double k = version >= 2 ? in.readDouble() : Double.NaN;

			if (mag < minMag)
				continue;

			Location loc = new Location(lat, lon, depth);

			ETAS_EqkRupture rup = new ETAS_EqkRupture();

			rup.setNthERF_Index(nthERFIndex);
			rup.setID(id);
			rup.setParentID(parentID);
			rup.setGeneration(gen);
			rup.setOriginTime(origTime);
			rup.setDistanceToParent(distToParent);
			rup.setMag(mag);
			rup.setHypocenterLocation(loc);
			rup.setFSSIndex(fssIndex);
			rup.setGridNodeIndex(gridNodeIndex);
			rup.setETAS_k(k);

			catalog.add(rup);
		}

		return catalog;
	}

	public static List<ETAS_Catalog> loadCatalogs(File zipFile) throws ZipException, IOException {
		return loadCatalogs(zipFile, -10);
	}
	
	public static List<ETAS_Catalog> loadCatalogs(File zipFile, double minMag)
			throws ZipException, IOException {
		return loadCatalogs(zipFile, minMag, false);
	}

	public static List<ETAS_Catalog> loadCatalogs(File zipFile, double minMag, boolean ignoreFailure)
			throws ZipException, IOException {
		if (isBinary(zipFile))
			return loadCatalogsBinary(zipFile, minMag);
		ZipFile zip = new ZipFile(zipFile);

		List<ETAS_Catalog> catalogs = new ArrayList<>();

		for (ZipEntry entry : Collections.list(zip.entries())) {
			if (!entry.isDirectory())
				continue;
			//			System.out.println(entry.getName());
			String subEntryName = entry.getName()+"simulatedEvents.txt";
			ZipEntry catEntry = zip.getEntry(subEntryName);
			if (catEntry == null)
				continue;
			//			System.out.println("Loading "+catEntry.getName());

			try {
				ETAS_Catalog cat = loadCatalog(
						zip.getInputStream(catEntry), minMag, ignoreFailure);

				catalogs.add(cat);
			} catch (Exception e) {
				//				ExceptionUtils.throwAsRuntimeException(e);
				System.out.println("Skipping catalog "+entry.getName()+": "+e.getMessage());
			}
			if (catalogs.size() % 1000 == 0)
				System.out.println("Loaded "+catalogs.size()+" catalogs (and counting)...");
		}

		zip.close();

		System.out.println("Loaded "+catalogs.size()+" catalogs");

		return catalogs;
	}

	private static void assertEquals(ETAS_EqkRupture expected, ETAS_EqkRupture actual) {
		Preconditions.checkState(expected.getID() == actual.getID());
		Preconditions.checkState(expected.getParentID() == actual.getParentID());
		Preconditions.checkState(expected.getGeneration() == actual.getGeneration());
		Preconditions.checkState(expected.getOriginTime() == actual.getOriginTime());
		if (Double.isNaN(expected.getDistanceToParent()))
			Preconditions.checkState(Double.isNaN(actual.getDistanceToParent()));
		else
			Preconditions.checkState(expected.getDistanceToParent() == actual.getDistanceToParent(),
			"%s != %s", expected.getDistanceToParent(), actual.getDistanceToParent());
		Preconditions.checkState(expected.getMag() == actual.getMag());
		Preconditions.checkState(expected.getHypocenterLocation().equals(actual.getHypocenterLocation()));
		Preconditions.checkState(expected.getFSSIndex() == actual.getFSSIndex());
		Preconditions.checkState(expected.getGridNodeIndex() == actual.getGridNodeIndex());
	}
	
	public static BinarayCatalogsIterable getBinaryCatalogsIterable(final File binFile, final double minMag) {
		return new BinarayCatalogsIterable(binFile, minMag);
	}
	
	public static class BinarayCatalogsIterable implements Iterable<ETAS_Catalog> {
		
		private final File binFile;
		private final double minMag;
		
		private int numCatalogs = -1;
		
		private BinarayCatalogsListIterator curIterator = null;
		
		private BinarayCatalogsIterable(final File binFile, final double minMag) {
			this.binFile = binFile;
			this.minMag = minMag;
		}
		
		@Override
		public Iterator<ETAS_Catalog> iterator() {
			BinarayCatalogsListIterator ret = getIterator();
			curIterator = null; // clear out so that next call gets a new iterator
			return ret;
		}
		
		private BinarayCatalogsListIterator getIterator() {
			if (curIterator == null) {
				try {
					curIterator = new BinarayCatalogsListIterator(binFile, minMag);
					numCatalogs = curIterator.numCatalogs;
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			return curIterator;
		}
		
		public int getNumCatalogs() {
			if (numCatalogs < 0)
				getIterator();
			Preconditions.checkState(numCatalogs >= 0);
			return numCatalogs;
		}
	}
	
	private static final int ITERABLE_PRELOAD_CAPACITY = 500; // catalogs
	
	private static class BinarayCatalogsListIterator implements Iterator<ETAS_Catalog> {
		
		private int numCatalogs;
		private int retIndex;
		private int loadIndex;
		
		private Throwable exception;
		
		private Thread loadThread;
		private LinkedBlockingDeque<ETAS_Catalog> deque;
		
		private DataInputStream in;
		
		private BinarayCatalogsListIterator(File binFile, double minMag) throws IOException {
			InputStream is = getIS(binFile);
			in = new DataInputStream(is);

			numCatalogs = in.readInt();
			
			deque = new LinkedBlockingDeque<>(ITERABLE_PRELOAD_CAPACITY);
			loadIndex = 0;
			loadThread = new Thread() {
				@Override
				public void run() {
					try {
						while (loadIndex < numCatalogs) {
							deque.putLast(doLoadCatalogBinary(in, minMag));
							loadIndex++;
						}
						in.close();
					} catch (Exception e) {
						exception = e;
						try {
							in.close();
						} catch (IOException e1) {}
					}
				}
			};
			loadThread.start();
			retIndex = 0;
		}

		@Override
		public boolean hasNext() {
			waitUntilReady();
			return !deque.isEmpty();
		}

		@Override
		public ETAS_Catalog next() {
			waitUntilReady();
			if (deque.isEmpty()) {
				if (exception == null) {
					throw new IllegalStateException("No more catalogs to load (loaded "+retIndex+"/"+numCatalogs+")");
				} else {
					System.err.println("Error loading catalog "+retIndex);
					System.err.flush();
					throw ExceptionUtils.asRuntimeException(exception);
				}
			}
			ETAS_Catalog catalog = deque.removeFirst();
			retIndex++;
			return catalog;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
		/**
		 * Blocks until either a new event is available or the loading thread has completed (all events populated)
		 */
		private void waitUntilReady() {
			while (deque.isEmpty() && loadThread.isAlive()) {
//				try {
//					Thread.sleep(100);
//				} catch (InterruptedException e) {
//					ExceptionUtils.throwAsRuntimeException(e);
//				}
			}
		}
		
	}
	
	public static BinarayCatalogsMetadataIterator getBinaryCatalogsMetadataIterator(File binFile) throws IOException {
		return new BinarayCatalogsMetadataIterator(binFile);
	}
	
	public static class BinarayCatalogsMetadataIterator implements PeekingIterator<ETAS_SimulationMetadata>, Closeable {

		private RandomAccessFile ra;
		private int numCatalogs;
		
		private int curIndex = -1;
		private ETAS_SimulationMetadata current;
		private short curVersion = -1;
		private long curStartPos = -1;
		private long curEndPos = -1;
		private int curNumRuptures = -1;
		private long length;
		
		private BinarayCatalogsMetadataIterator(File binFile) throws IOException {
			ra = new RandomAccessFile(binFile, "r");
			numCatalogs = ra.readInt();
		}
		
		private void checkLoad() throws IOException {
			if (curEndPos > curStartPos)
				// already loaded
				return;
			current = null;
			curEndPos = -1;
			curNumRuptures = -1;
			curVersion = -1;
			if (curIndex >= numCatalogs-1) {
				ra.close();
				return;
			}
			
			curIndex++;
			ETAS_SimulationMetadata meta;
			long headerStartPos = ra.getFilePointer();
			try {
				curVersion = ra.readShort();
				meta = readBinaryMetadata(ra, curVersion);
			} catch (Exception e) {
				System.err.println("Error reading metadata for catalog "+curIndex+" at header pos="+headerStartPos
						+", trucated? "+e.getMessage());
				close();
				return;
			}
			curNumRuptures = ra.readInt();
			curStartPos = ra.getFilePointer();
			curEndPos = ra.getFilePointer() + getCatalogLengthBytes(curNumRuptures, curVersion, false);
			current = meta;
			length = ra.length();
			if (curEndPos >= length)
				close();
			else
				ra.seek(curEndPos);
		}

		@Override
		public synchronized boolean hasNext() {
			if (curIndex >= numCatalogs)
				return false;
			try {
				checkLoad();
			} catch (IOException e) {
				System.err.println("WARNING: truncated? "+e.getMessage());
				return false;
			}
			return curEndPos > curStartPos;
		}

		@Override
		public synchronized ETAS_SimulationMetadata next() {
			ETAS_SimulationMetadata ret = peek();
			current = null;
			curVersion = -1;
			curEndPos = -1;
			curStartPos = -1;
			curNumRuptures = -1;
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
		public long getNextStartPos() {
			peek();
			return curStartPos;
		}
		
		public long getNextEndPos() {
			peek();
			return curEndPos;
		}
		
		public int getNextNumRuptures() {
			peek();
			return curNumRuptures;
		}
		
		public short getNextFileVersion() {
			peek();
			return curVersion;
		}
		
		public boolean isNextFullyWritten() {
			return curEndPos > 0 && curEndPos <= length;
		}

		@Override
		public synchronized ETAS_SimulationMetadata peek() {
			try {
				checkLoad();
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			return current;
		}

		@Override
		public void close() throws IOException {
			ra.close();
		}
		
	}
	
	public static void consolidateResultsDirBinary(File resultsDir, File outputFile, double minMag)
			throws IOException {
		consolidateResultsDirBinary(new File[] {resultsDir}, outputFile, minMag);
	}

	public static void consolidateResultsDirBinary(File[] resultsDirs, File outputFile, double minMag)
			throws IOException {
		List<File> eventsFiles = Lists.newArrayList();

		for (File resultsDir : resultsDirs) {
			File[] subDirs = resultsDir.listFiles();
			Arrays.sort(subDirs, new FileNameComparator());
			
			for (File subDir : subDirs) {
				if (!subDir.isDirectory() || !subDir.getName().startsWith("sim_"))
					continue;

				// check ASCII first
				File asciiFile = new File(subDir, "simulatedEvents.txt");
				if (asciiFile.exists()) {
					eventsFiles.add(asciiFile);
					continue;
				}
				
				// then try to just copy over binary data
				File binaryFile = new File(subDir, "simulatedEvents.bin");
				if (binaryFile.exists() && binaryFile.length() > 0l) {
					eventsFiles.add(binaryFile);
					continue;
				}
				File binaryGZipFile = new File(subDir, "simulatedEvents.bin.gz");
				if (binaryGZipFile.exists() && binaryGZipFile.length() > 0l) {
					eventsFiles.add(binaryGZipFile);
					continue;
				}
			}
		}

		System.out.println("Detected "+eventsFiles.size()+" catalogs");

		Preconditions.checkState(!eventsFiles.isEmpty(), "No catalogs detected!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), buffer_len));

		// write number of catalogs as int
		out.writeInt(eventsFiles.size());

		for (File eventsFile : eventsFiles) {
			String name = eventsFile.getName();
			// make sure that we actually write something
			if (!eventsFile.exists() && name.endsWith(".txt")) {
				// currently running, skip
				eventsFile = new File(eventsFile.getParentFile(), "simulatedEvents.bin");
				name = eventsFile.getName();
				Preconditions.checkState(eventsFile.exists(), "TXT file deleted but bin doesn't exist?");
			}
			int prevCounter = out.size();
			try {
				if (minMag < 0 && name.endsWith(".bin")) {
					ByteStreams.copy(new BufferedInputStream(new FileInputStream(eventsFile), buffer_len), out);
				} else if (minMag < 0 && name.endsWith(".bin.gz")) {
					ByteStreams.copy(new GZIPInputStream(new FileInputStream(eventsFile), buffer_len), out);
				} else {
					writeCatalogBinary(out, loadCatalog(eventsFile, minMag, true));
				}
			} catch (IOException e) {
				System.err.println("FAILED on "+eventsFile.getAbsolutePath());
				throw e;
			} catch (RuntimeException e) {
				System.err.println("FAILED on "+eventsFile.getAbsolutePath());
				throw e;
			}
			int newCounter = out.size();
			Preconditions.checkState(newCounter == Integer.MAX_VALUE || newCounter > prevCounter,
					"Didn't write anything for catalog in %s. before: %s, after %s bytes",
					eventsFile.getAbsolutePath(), prevCounter, newCounter);
		}

		out.close();
	}

	public static void zipToBin(File zipFile, File binFile, double minMag)
			throws ZipException, IOException {
		ZipFile zip = new ZipFile(zipFile);

		List<ZipEntry> entries = Lists.newArrayList();

		for (ZipEntry entry : Collections.list(zip.entries())) {
			if (!entry.isDirectory())
				continue;
			//			System.out.println(entry.getName());
			String subEntryName = entry.getName()+"simulatedEvents.txt";
			ZipEntry catEntry = zip.getEntry(subEntryName);
			if (catEntry == null)
				continue;
			//			System.out.println("Loading "+catEntry.getName());

			entries.add(catEntry);
		}

		Collections.sort(entries, new Comparator<ZipEntry>() {

			@Override
			public int compare(ZipEntry o1, ZipEntry o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		System.out.println("Detected "+entries.size()+" catalogs");

		Preconditions.checkState(!entries.isEmpty(), "No catalogs detected!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
				new FileOutputStream(binFile), buffer_len));

		// write number of catalogs as int
		out.writeInt(entries.size());

		for (int i=0; i<entries.size(); i++) {
			ZipEntry catEntry = entries.get(i);
			List<ETAS_EqkRupture> cat = loadCatalog(
					zip.getInputStream(catEntry), minMag);
			writeCatalogBinary(out, cat);

			if ((i+1) % 1000 == 0)
				System.out.println("Converted "+(i+1)+" catalogs (and counting)...");
		}

		zip.close();
		out.close();

		System.out.println("Converted "+entries.size()+" catalogs");
	}
	
	public static void mergeBinary(File outputFile, double minDuration, File... inputFiles)
			throws ZipException, IOException {
		int count = 0;
		int skipped = 0;
		
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), buffer_len));

		// write number of catalogs as int
		out.writeInt(-1); // will overwrite later
		
		for (File inputFile : inputFiles) {
			System.out.println("Handling "+inputFile.getAbsolutePath());
//			List<List<ETAS_EqkRupture>> subCatalogs = loadCatalogs(inputFile);
			for (List<ETAS_EqkRupture> catalog : getBinaryCatalogsIterable(inputFile, 0d)) {
				double duration = 0;
				if (!catalog.isEmpty())
					duration = ETAS_MultiSimAnalysisTools.calcDurationYears(catalog);
				if (minDuration > 0 && duration < minDuration) {
					skipped++;
				} else {
					count++;
					writeCatalogBinary(out, catalog);
				}
			}
		}

		out.close();
		
		System.out.println("Wrote "+count+" catalogs (skipped "+skipped+")");
		
		// now fix the catalog count
		RandomAccessFile raFile = new RandomAccessFile(outputFile, "rw");
		raFile.seek(0l);
		raFile.writeInt(count);
		raFile.close();
	}
	
	public static void unionBinary(File outputFile, File... inputFiles)
			throws ZipException, IOException {
		Preconditions.checkArgument(inputFiles.length > 1);
		
		BinarayCatalogsIterable[] iterables = new BinarayCatalogsIterable[inputFiles.length];
		List<Iterator<ETAS_Catalog>> iterators = new ArrayList<>();
		for (int i=0; i<inputFiles.length; i++) {
			iterables[i] = getBinaryCatalogsIterable(inputFiles[i], 0d);
			iterators.add(iterables[i].iterator());
		}
		
		int numCatalogs = iterables[0].getNumCatalogs();
		
		for (int i=1; i<inputFiles.length; i++)
			Preconditions.checkState(iterables[i].getNumCatalogs() == numCatalogs);
		
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), buffer_len));
		
		long uniqueRups = 0;
		long totalRups = 0;

		// write number of catalogs as int
		out.writeInt(numCatalogs);
		
		for (int i=0; i<numCatalogs; i++) {
			if (i % 1000 == 0)
				System.out.println("Processing catalog "+i);
			Map<Integer, ETAS_EqkRupture> catalogMap = Maps.newHashMap();
			for (Iterator<ETAS_Catalog> it : iterators) {
				List<ETAS_EqkRupture> catalog = it.next();
				for (ETAS_EqkRupture rup : catalog) {
					Integer id = rup.getID();
					if (catalogMap.containsKey(id))
						Preconditions.checkState(catalogMap.get(id).getOriginTime() == rup.getOriginTime(),
								"Trying to union between different catalogs");
					else
						catalogMap.put(id, rup);
				}
				totalRups += catalog.size();
			}
			uniqueRups += catalogMap.size();
			List<ETAS_EqkRupture> catalog = Lists.newArrayList(catalogMap.values());
			Collections.sort(catalog, ETAS_SimAnalysisTools.eventComparator);
			writeCatalogBinary(out, catalog);
		}

		out.close();
		
		double keptPercent = 100d*uniqueRups/(double)(totalRups);
		
		System.out.println("Union complete. "+(float)keptPercent+"% of ruptures kept");
	}
	
	public static void binaryCatalogsFilterByMag(File inputFile, File outputFile, double minMag,
			boolean preserveChain) throws ZipException, IOException {
		int count = 0;
		
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), buffer_len));

		// write number of catalogs as int
		out.writeInt(-1); // will overwrite later
		
		if (preserveChain) {
			// we need to include events below the minimum magnitude that are part of a chain leading to an
			// event which is above the minimum magnitude
			BinarayCatalogsIterable it = getBinaryCatalogsIterable(inputFile, 0d);
			System.out.println("Input has "+it.getNumCatalogs()+" catalogs");
			for (List<ETAS_EqkRupture> catalog : it) {
				if (count % 1000 == 0)
					System.out.println("Processing catalog "+count);
				catalog = ETAS_SimAnalysisTools.getAboveMagPreservingChain(catalog, minMag);
				count++;
				writeCatalogBinary(out, catalog);
			}
		} else {
			for (List<ETAS_EqkRupture> catalog : getBinaryCatalogsIterable(inputFile, minMag)) {
				if (count % 1000 == 0)
					System.out.println("Processing catalog "+count);
				count++;
				writeCatalogBinary(out, catalog);
			}
		}

		out.close();
		
		System.out.println("Wrote "+count+" catalogs");
		
		// now fix the catalog count
		RandomAccessFile raFile = new RandomAccessFile(outputFile, "rw");
		raFile.seek(0l);
		raFile.writeInt(count);
		raFile.close();
	}
	
	public static class ETAS_Catalog extends ArrayList<ETAS_EqkRupture> {
		
		private ETAS_SimulationMetadata meta;

		public ETAS_Catalog(ETAS_SimulationMetadata meta) {
			this.meta = meta;
		}

		public ETAS_SimulationMetadata getSimulationMetadata() {
			return meta;
		}

		public void setSimulationMetadata(ETAS_SimulationMetadata meta) {
			this.meta = meta;
		}
		
		public void updateMetadataForCatalog() {
			meta = meta.getUpdatedForCatalog(this);
		}
	}

	public static void main(String[] args) throws ZipException, IOException {
		if (args.length == 2 || args.length == 3) {
			// we're consolidating a results dir
			File[] resultsDirs;
			if (args[0].contains(",")) {
				List<File> dirs = Lists.newArrayList();
				for (String dir : Splitter.on(",").split(args[0])) {
					if (dir.isEmpty())
						continue;
					File d = new File(dir);
					Preconditions.checkState(d.exists());
					dirs.add(d);
				}
				resultsDirs = dirs.toArray(new File[0]);
			} else {
				resultsDirs = new File[] { new File(args[0]) };
			}
			File outputFile = new File(args[1]);
			double minMag = -10;
			if (args.length == 3)
				minMag = Double.parseDouble(args[2]);
			if (resultsDirs.length == 0 && resultsDirs[0].getName().endsWith(".zip"))
				zipToBin(resultsDirs[0], outputFile, minMag);
			else
				consolidateResultsDirBinary(resultsDirs, outputFile, minMag);
			System.exit(0);
		}
		//		File resultsZipFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
		//				+ "2015_08_07-mojave_m7-full_td/results.zip");
		//		File resultsBinFile = new File(resultsZipFile.getAbsolutePath().replaceAll("zip", "bin"));
		//		zipToBin(resultsZipFile, resultsBinFile, -10);

		//		File resultFile = new File("/tmp/asdf/results/sim_1/simulatedEvents.txt");
		//		writeEventDataToFile(resultFile, loadCatalog(resultFile));

//		File resultsDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2015_08_21-spontaneous-full_td-grCorr/results/");
//		for (File subDir : resultsDir.listFiles()) {
//			if (!subDir.getName().startsWith("sim_"))
//				continue;
//			System.out.println(subDir.getName());
//			File eventFile = new File(subDir, "simulatedEvents.txt");
//			writeEventDataToFile(eventFile, loadCatalog(eventFile));
//		}
		
		File dir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k");
				+ "2016_08_24-spontaneous-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined");
		binaryCatalogsFilterByMag(new File(dir, "results_m4.bin"), new File(dir, "results_m5.bin"), 5d, false);
		
//		File binFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/results_m4.bin");
//		File binFile = new File("/home/scec-00/kmilner/ucerf3_etas_results_stampede/"
//				+ "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/results.bin");
//		int cnt = 0;
//		File asciiDir = new File(binFile.getParentFile(), "ascii");
//		Preconditions.checkState(asciiDir.exists() || asciiDir.mkdir());
//		for (List<ETAS_EqkRupture> catalog : getBinaryCatalogsIterable(binFile, 0d)) {
//			if (cnt == 100)
//				break;
//			writeEventDataToFile(new File(asciiDir, "catalog_"+cnt+".txt"), catalog);
//			cnt++;
//		}
		
		
//		List<List<ETAS_EqkRupture>> catalogs = loadCatalogsBinary(binFile, 4d);
////		for (int i=0; i<5; i++)
//		for (int i=0; i<catalogs.size(); i++)
//			writeEventDataToFile(new File(asciiDir, "catalog_"+i+"m4.txt"), catalogs.get(i));
		
//		File testFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2015_12_08-spontaneous-1000yr-full_td-noApplyLTR/results_m4_first200.bin");
//		for (List<ETAS_EqkRupture> catalog : getBinaryCatalogsIterable(testFile, 0d))
//			System.out.println("Catalog has "+catalog.size()+" ruptures");
//		System.exit(0);
		
//		File baseDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
//		File baseDir = new File("/auto/scec-00/kmilner/ucerf3_etas_results_stampede/");
//		mergeBinary(new File(new File(baseDir, "2015_12_09-spontaneous-30yr-full_td-noApplyLTR"), "results.bin"),
//				0, new File[] {
//						new File(new File(baseDir, "2015_12_09-spontaneous-30yr-full_td-noApplyLTR"), "results_first1000.bin"),
//						new File(new File(baseDir, "2015_12_09-spontaneous-30yr-full_td-noApplyLTR"), "results_4000more.bin")
//				});

		//		File resultsZipFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
		//				+ "2015_08_07-mojave_m7-poisson-grCorr/results_m4.zip");
		//		File resultsZipFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
		//				+ "2015_08_07-mojave_m7-full_td/results.zip");
		//		
		//		boolean validate = false;
		//		
		//		Stopwatch timer = Stopwatch.createStarted();
		//		List<List<ETAS_EqkRupture>> origCatalogs = ETAS_MultiSimAnalysisTools.loadCatalogsZip(resultsZipFile);
		//		timer.stop();
		//		long time = timer.elapsed(TimeUnit.SECONDS);
		//		System.out.println("ASCII loading took "+time+" seconds");
		//		
		//		File binaryFile = new File("/tmp/catalog.bin");
		//		timer.reset();
		//		timer.start();
		//		writeCatalogsBinary(binaryFile, origCatalogs);
		//		timer.stop();
		//		time = timer.elapsed(TimeUnit.SECONDS);
		//		System.out.println("Binary writing took "+time+" seconds");
		//		
		//		if (!validate)
		//			origCatalogs = null;
		//		System.gc();
		//		
		//		timer.reset();
		//		timer.start();
		//		List<List<ETAS_EqkRupture>> newCatalogs = loadCatalogsBinary(binaryFile);
		//		timer.stop();
		//		time = timer.elapsed(TimeUnit.SECONDS);
		//		System.out.println("Binary loading took "+time+" seconds ("+newCatalogs.size()+" catalogs)");
		//		
		//		// now validate
		//		if (validate) {
		//			Random r = new Random();
		//			for (int i=0; i<origCatalogs.size(); i++) {
		//				List<ETAS_EqkRupture> catalog1 = origCatalogs.get(i);
		//				List<ETAS_EqkRupture> catalog2 = newCatalogs.get(i);
		//				
		//				Preconditions.checkState(catalog1.size() == catalog2.size());
		//				
		//				for (int j=0; j<100; j++) {
		//					int index = r.nextInt(catalog1.size());
		//					assertEquals(catalog1.get(index), catalog2.get(index));
		//				}
		//			}
		//		}

		//		Stopwatch timer = Stopwatch.createStarted();
		//		List<List<ETAS_EqkRupture>> newCatalogs = loadCatalogsBinary(
		//				new GZIPInputStream(new FileInputStream(new File("/tmp/catalog.bin.gz"))), -10d);
		//		timer.stop();
		//		long time = timer.elapsed(TimeUnit.SECONDS);
		//		System.out.println("Binary loading took "+time+" seconds ("+newCatalogs.size()+" catalogs)");
	}

}
