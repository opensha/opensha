package scratch.UCERF3.erf.ETAS;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TimeZone;
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
				+ "ID\tparID\tGen\tOrigTime\tdistToParent\tnthERFIndex\tFSS_ID\tGridNodeIndex";

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
			sb.append(hypoLoc.getLatitude()).append("\t");
			sb.append(hypoLoc.getLongitude()).append("\t");
			sb.append(hypoLoc.getDepth()).append("\t");			
		}
		else {
			sb.append("null").append("\t");
			sb.append("null").append("\t");
			sb.append("null").append("\t");
		}
		sb.append(rup.getMag()).append("\t");
		sb.append(rup.getID()).append("\t");
		sb.append(rup.getParentID()).append("\t");
		sb.append(rup.getGeneration()).append("\t");
		sb.append(rup.getOriginTime()).append("\t");
		sb.append(rup.getDistanceToParent()).append("\t");
		sb.append(rup.getNthERF_Index()).append("\t");
		sb.append(rup.getFSSIndex()).append("\t");
		sb.append(rup.getGridNodeIndex());
		
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
		Preconditions.checkState(split.length == 10 || split.length == 12 || split.length == 18,
				"Line has unexpected number of items. Expected 10/12/18, got %s. Line: %s", split.length, line);

		int nthERFIndex, fssIndex, gridNodeIndex, id, parentID, gen;
		long origTime;
		double distToParent, mag, lat, lon, depth;

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

		return rup;
	}

	/**
	 * Loads an ETAS catalog from the given text catalog file
	 * 
	 * @param catalogFile
	 * @return
	 * @throws IOException
	 */
	public static List<ETAS_EqkRupture> loadCatalog(File catalogFile) throws IOException {
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
	public static List<ETAS_EqkRupture> loadCatalog(File catalogFile, double minMag) throws IOException {
		return loadCatalog(catalogFile, minMag, false);
	}

	public static List<ETAS_EqkRupture> loadCatalog(File catalogFile, double minMag, boolean ignoreFailure)
			throws IOException {
		if (isBinary(catalogFile))
			return loadCatalogBinary(catalogFile, minMag);
		List<ETAS_EqkRupture> catalog = Lists.newArrayList();
		for (String line : Files.readLines(catalogFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("%") || line.startsWith("#") || line.isEmpty())
				continue;
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
		return catalog;
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
	public static List<ETAS_EqkRupture> loadCatalog(InputStream catalogStream, double minMag) throws IOException {
		return loadCatalog(	catalogStream, minMag, false);
	}
	
	public static List<ETAS_EqkRupture> loadCatalog(InputStream catalogStream, double minMag, boolean ignoreFailure)
			throws IOException {
		List<ETAS_EqkRupture> catalog = Lists.newArrayList();
		BufferedReader reader = new BufferedReader(new InputStreamReader(catalogStream));

		for (String line : CharStreams.readLines(reader)) {
			line = line.trim();
			if (line.startsWith("%") || line.startsWith("#") || line.isEmpty())
				continue;
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

	public static void writeCatalogsBinary(File file, List<List<ETAS_EqkRupture>> catalogs) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkNotNull(catalogs, "Catalog cannot be null!");
		Preconditions.checkArgument(!catalogs.isEmpty(), "Must supply at least one catalog");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), buffer_len));

		// write number of catalogs as int
		out.writeInt(catalogs.size());

		for (List<ETAS_EqkRupture> catalog : catalogs)
			writeCatalogBinary(out, catalog);

		out.close();
	}

	public static void writeCatalogBinary(DataOutputStream out, List<ETAS_EqkRupture> catalog) throws IOException {
		// write file version as short
		out.writeShort(1);

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
		}
	}

	public static List<ETAS_EqkRupture> loadCatalogBinary(File file) throws IOException {
		return loadCatalogBinary(file, -10d);
	}

	public static List<ETAS_EqkRupture> loadCatalogBinary(File file, double minMag) throws IOException {
		return loadCatalogBinary(getIS(file), minMag);
	}

	public static List<ETAS_EqkRupture> loadCatalogBinary(InputStream is, double minMag) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		List<ETAS_EqkRupture> catalog = doLoadCatalogBinary(in, minMag);

		in.close();

		return catalog;
	}

	public static List<List<ETAS_EqkRupture>> loadCatalogsBinary(File file) throws IOException {
		return loadCatalogsBinary(file, -10d);
	}

	public static List<List<ETAS_EqkRupture>> loadCatalogsBinary(File file, double minMag) throws IOException {
		return loadCatalogsBinary(getIS(file), minMag);
	}

	public static final int buffer_len = 655360;

	private static InputStream getIS(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		FileInputStream fis = new FileInputStream(file);

		if (file.getName().toLowerCase().endsWith(".gz"))
			return new GZIPInputStream(fis, buffer_len);
		return new BufferedInputStream(fis, buffer_len);
	}

	public static List<List<ETAS_EqkRupture>> loadCatalogsBinary(InputStream is, double minMag) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream) && !(is instanceof GZIPInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		List<List<ETAS_EqkRupture>> catalogs = Lists.newArrayList();

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

	private static List<ETAS_EqkRupture> doLoadCatalogBinary(DataInput in, double minMag) throws IOException {
		short version = in.readShort();

		Preconditions.checkState(version == 1, "Unknown binary file version: "+version);

		int numRups = in.readInt();

		Preconditions.checkState(numRups >= 0, "Bad num rups: "+numRups);

		List<ETAS_EqkRupture> catalog = Lists.newArrayList();

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

			catalog.add(rup);
		}

		return catalog;
	}

	public static List<List<ETAS_EqkRupture>> loadCatalogs(File zipFile) throws ZipException, IOException {
		return loadCatalogs(zipFile, -10);
	}
	
	public static List<List<ETAS_EqkRupture>> loadCatalogs(File zipFile, double minMag)
			throws ZipException, IOException {
		return loadCatalogs(zipFile, minMag, false);
	}

	public static List<List<ETAS_EqkRupture>> loadCatalogs(File zipFile, double minMag, boolean ignoreFailure)
			throws ZipException, IOException {
		if (isBinary(zipFile))
			return loadCatalogsBinary(zipFile, minMag);
		ZipFile zip = new ZipFile(zipFile);

		List<List<ETAS_EqkRupture>> catalogs = Lists.newArrayList();

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
				List<ETAS_EqkRupture> cat = loadCatalog(
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
	
	public static class BinarayCatalogsIterable implements Iterable<List<ETAS_EqkRupture>> {
		
		private final File binFile;
		private final double minMag;
		
		private int numCatalogs = -1;
		
		private BinarayCatalogsListIterator curIterator = null;
		
		private BinarayCatalogsIterable(final File binFile, final double minMag) {
			this.binFile = binFile;
			this.minMag = minMag;
		}
		
		@Override
		public Iterator<List<ETAS_EqkRupture>> iterator() {
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
	
	private static class BinarayCatalogsListIterator implements Iterator<List<ETAS_EqkRupture>> {
		
		private double minMag;
		
		private int numCatalogs;
		private int index;
		
		private DataInputStream in;
		
		private BinarayCatalogsListIterator(File binFile, double minMag) throws IOException {
			this.minMag = minMag;
			
			InputStream is = getIS(binFile);
			in = new DataInputStream(is);

			numCatalogs = in.readInt();
			index = 0;
		}

		@Override
		public boolean hasNext() {
			return index < numCatalogs;
		}

		@Override
		public List<ETAS_EqkRupture> next() {
			Preconditions.checkState(hasNext(), "No more catalogs to load!");
			try {
				List<ETAS_EqkRupture> catalog = doLoadCatalogBinary(in, minMag);
				index++;
				if (!hasNext())
					in.close();
				return catalog;
			} catch (IOException e) {
				try {
					in.close();
				} catch (IOException e1) {}
				System.err.println("Error loading catalog "+index);
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
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
		List<Iterator<List<ETAS_EqkRupture>>> iterators = Lists.newArrayList();
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
			for (Iterator<List<ETAS_EqkRupture>> it : iterators) {
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
