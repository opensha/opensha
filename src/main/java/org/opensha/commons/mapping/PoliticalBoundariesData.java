package org.opensha.commons.mapping;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.PrimitiveArrayXY_Dataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class PoliticalBoundariesData {

	private static final String US_PREFIX = "us_complete/";
	private static final String CA_NAME = US_PREFIX+"California.txt";
	private static final String NZ_NAME = "oceania/New_Zealand.txt";
	
	private static Map<String, XY_DataSet[]> loadedOutlines;
	private static List<String> fileNames;
	private static List<Location[]> extents;
	
	private static final String PATH_PREFIX = "/data/boundaries/";
	
	private static boolean D = false;

	/**
	 * 
	 * @param region
	 * @return default political boundaries for the given region, or null if none available
	 */
	public static XY_DataSet[] loadDefaultOutlines(Region region) {
		// political boundary special cases

		String name = region.getName();
		if (name != null && name.startsWith("RELM") && region.getMinLat() < 42 && region.getMaxLat() > 32
				&& region.getMinLon() < -114 && region.getMaxLon() > -125) {
			// it's one of the hardcoded California regions
			try {
				//				System.out.println("Hardcoded CA RELM");
				return loadCAOutlines();
			} catch (IOException e) {
				System.err.println("WARNING: couldn't load CA outline data: "+e.getMessage());
			}
		}
		boolean usOnly = false;
		if (name != null && name.startsWith("NSHMP Conterminous"))
			// US only
			usOnly = true;
		try {
			initMappings();
			List<XY_DataSet> ret = new ArrayList<>();
			for (int f=0; f<fileNames.size(); f++) {
				String fileName = fileNames.get(f);
				if (usOnly && !fileName.startsWith(US_PREFIX)) {
					if (D && potentiallyOverlaps(region, extents.get(f)))
						System.out.println(fileName+" overlaps, but skipping because region name matched a US-only pattern: "+name);
					continue;
				}
				if (potentiallyOverlaps(region, extents.get(f))) {
					if (D)
						System.out.println(fileName+" overlaps");
					for (XY_DataSet xy : checkLoadFile(fileName))
						ret.add(xy);
				}
			}
			if (region.getMaxLon() > 180d) {
				// translate any defined < 0
				for (int i=0; i<ret.size(); i++) {
					XY_DataSet xy = ret.get(i);
					if (xy.getMinX() < 0) {
						DefaultXY_DataSet modXY = new DefaultXY_DataSet();
						for (Point2D pt : xy)
							modXY.set(pt.getX()+360d, pt.getY());
						ret.set(i, modXY);
					}
				}
			} else if (region.getMinLon() < 0d) {
				// translate any defined > 180
				for (int i=0; i<ret.size(); i++) {
					XY_DataSet xy = ret.get(i);
					if (xy.getMaxX() > 180) {
						DefaultXY_DataSet modXY = new DefaultXY_DataSet();
						for (Point2D pt : xy)
							modXY.set(pt.getX()-360d, pt.getY());
						ret.set(i, modXY);
					}
				}
			}
			return ret.toArray(new XY_DataSet[0]);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("WARNING: couldn't load outline data: "+e.getMessage());
			return null;
		}
	}

	private static boolean potentiallyOverlaps(Region region, Location[] bounds) {
		if (region.getMinLat() > bounds[1].lat)
			// region is north of the bounds
			return false;
		if (region.getMaxLat() < bounds[0].lat)
			// region is south of the bounds
			return false;

		double minLon = region.getMinLon();
		double maxLon = region.getMaxLon();

		// Copy bounds so we can shift them without touching the inputs
		double bMinLon = bounds[0].lon;
		double bMaxLon = bounds[1].lon;

		// We assume region never crosses the dateline in its own convention.
		// Shift ONLY the bounds to match the region's longitude convention.
		boolean regionLikely360 = minLon >= 0.0 && maxLon > 180.0;
		boolean boundsLikely360 = bMinLon >= 0.0 && bMaxLon > 180.0;

		if (regionLikely360 != boundsLikely360) {
			if (regionLikely360) {
				// region is [0,360]-like; shift bounds from [-180,180] -> [0,360]
				if (bMinLon < 0.0) bMinLon += 360.0;
				if (bMaxLon < 0.0) bMaxLon += 360.0;
			} else {
				// region is [-180,180]-like; shift bounds from [0,360] -> [-180,180]
				if (bMinLon > 180.0) bMinLon -= 360.0;
				if (bMaxLon > 180.0) bMaxLon -= 360.0;
			}

			// After shifting, bounds might now "cross" the dateline (min > max).
			// Since region doesn't cross, handle that by splitting bounds into two intervals.
			if (bMinLon > bMaxLon) {
				// bounds covers [bMinLon, maxOfRange] U [minOfRange, bMaxLon]
				// pick range endpoints based on region convention
				final double lo = regionLikely360 ? 0.0 : -180.0;
				final double hi = regionLikely360 ? 360.0 : 180.0;

				boolean overlapA = intervalsOverlap(minLon, maxLon, bMinLon, hi);
				boolean overlapB = intervalsOverlap(minLon, maxLon, lo, bMaxLon);
				return overlapA || overlapB;
			}
		}

		return intervalsOverlap(minLon, maxLon, bMinLon, bMaxLon);
	}

	private static boolean intervalsOverlap(double aMin, double aMax, double bMin, double bMax) {
		// assumes both intervals are non-wrapping (aMin <= aMax and bMin <= bMax)
		return !(aMin > bMax || aMax < bMin);
	}

	/**
	 * @return array of XY_DataSets that represent California boundaries (plural/array because of islands). X values are longitude
	 * and Y values are latitude.
	 * @throws IOException
	 */
	public synchronized static XY_DataSet[] loadCAOutlines() throws IOException {
		return checkLoadFile(CA_NAME);
	}

	public synchronized static XY_DataSet[] loadUSState(String stateName) throws IOException {
		stateName = stateName.trim().replace(" ", "_");
		while (stateName.contains("__"))
			stateName = stateName.replace("__", "_");
		return checkLoadFile(US_PREFIX+stateName+".txt");
	}

	/**
	 * @return array of XY_DataSets that represent New Zealand boundaries (plural/array because of islands). X values are longitude
	 * and Y values are latitude.
	 * @throws IOException
	 */
	public synchronized static XY_DataSet[] loadNZOutlines() throws IOException {
		return checkLoadFile(NZ_NAME);
	}
	
	private synchronized static XY_DataSet[] checkLoadFile(String fileName) throws IOException {
		if (loadedOutlines == null)
			loadedOutlines = new HashMap<>();
		else if (loadedOutlines.containsKey(fileName))
			return loadedOutlines.get(fileName);
		
		String path = PATH_PREFIX+fileName;
		InputStream is = PoliticalBoundariesData.class.getResourceAsStream(path);
		Preconditions.checkNotNull(is, "Resources not found: %s", path);
		Map<String, XY_DataSet[]> outlines = loadOutlinesFile(is);
		Preconditions.checkState(outlines.size() == 1, "Pre-processed outlines should be separated out by region alread");
		XY_DataSet[] ret = outlines.values().iterator().next();
		loadedOutlines.put(fileName, ret);
		return ret;
	}

	private static Map<String, XY_DataSet[]> loadOutlinesFile(InputStream is) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		Map<String, List<XY_DataSet>> outlines = new HashMap<>();

		String line;

		String curSegName = null;
		DefaultXY_DataSet curSeg = null;

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			if (line.startsWith("segment")) {
				curSegName = line.substring("segment".length()).replace("_", " ").trim();
				curSeg = new DefaultXY_DataSet();
				List<XY_DataSet> segsForName = outlines.get(curSegName);
				if (segsForName == null) {
					segsForName = new ArrayList<>();
					outlines.put(curSegName, segsForName);
				}
				segsForName.add(curSeg);
				continue;
			} else if (curSeg == null) {
				// header
				continue;
			}
			String[] vals = line.trim().split(" ");
			double lat = Double.valueOf(vals[0]);
			double lon = Double.valueOf(vals[1]);
			Preconditions.checkNotNull(curSeg, "value encountered before first segment defined?");
			curSeg.set(lon, lat);
		}
		br.close();

		Preconditions.checkState(!outlines.isEmpty(), "No outlines found");

		Map<String, XY_DataSet[]> ret = new HashMap<>();
		for (String name : outlines.keySet()) {
			List<XY_DataSet> segs = outlines.get(name);

			double minLon = Double.POSITIVE_INFINITY;
			double maxLon = Double.NEGATIVE_INFINITY;
			for (XY_DataSet xy : segs) {
				for (Point2D pt : xy) {
					minLon = Math.min(minLon, pt.getX());
					maxLon = Math.max(maxLon, pt.getX());
				}
			}
			if (minLon < -180d) {
				if (minLon > -180.1) {
					// just trim it
					for (int i=0; i<segs.size(); i++) {
						XY_DataSet xy = segs.get(i);
						XY_DataSet modXY = new DefaultXY_DataSet();
						for (int j=0; j<xy.size(); j++) {
							double x = Math.max(-180d, xy.getX(j));
							double y = xy.getY(j);
							modXY.set(x, y);
						}
						segs.set(i, modXY);
					}
				} else {
					throw new IllegalStateException("Bad minLon="+minLon+" for "+name);
				}
			}

			XY_DataSet[] segArray = new XY_DataSet[segs.size()];
			for (int i=0; i<segs.size(); i++)
				segArray[i] = new PrimitiveArrayXY_Dataset(segs.get(i));

			ret.put(name, segArray);
		}

		return ret;
	}
	
	private synchronized static void initMappings() throws IOException {
		if (extents != null)
			return;
		CSVFile<String> csv = CSVFile.readStream(PoliticalBoundariesData.class.getResourceAsStream(PATH_PREFIX+"mappings.csv"), true);
		List<String> fileNames = new ArrayList<>(csv.getNumRows()-1);
		List<Location[]> extents = new ArrayList<>(csv.getNumRows()-1);
		for (int row=1; row<csv.getNumRows(); row++) {
			fileNames.add(csv.get(row, 0));
			Location lowerLeft = new Location(csv.getDouble(row, 2), csv.getDouble(row, 3));
			Location upperRight = new Location(csv.getDouble(row, 4), csv.getDouble(row, 5));
			extents.add(new Location[] {lowerLeft, upperRight});
		}
		PoliticalBoundariesData.fileNames = fileNames;
		PoliticalBoundariesData.extents = extents;
	}
	
	private static final DecimalFormat outDF = new DecimalFormat("0.###");

	private static void writeMappingsIndex(File sourceDir, File outputDir) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Directory", "Region", "Min Lat", "Min Lon", "Max Lat", "Max Lon");
		for (File file : sourceDir.listFiles()) {
			String name = file.getName();
			if (!name.endsWith(".txt"))
				continue;
			String dirName = name.substring(0, name.indexOf(".txt"));
			File subDir = new File(outputDir, dirName);
			System.out.println("Processing "+dirName);
			Preconditions.checkState(subDir.exists() || subDir.mkdir());
			Stopwatch watch = Stopwatch.createStarted();
			FileInputStream fis = new FileInputStream(file);
			Map<String, XY_DataSet[]> outlines = loadOutlinesFile(fis);
			fis.close();
			watch.stop();
			double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			System.out.println("Took "+(float)secs+" s to load "+outlines.size()+" outlines");

			for (String regName : outlines.keySet()) {
				System.out.println("\tDoing mapping for: "+regName);
				if (regName.equals("Puerto Rico") && !dirName.startsWith("us"))
					// special case, puerto rico is included twice!
					continue;
				XY_DataSet[] regOutlines = outlines.get(regName);
				
				String outFName = FileNameUtils.simplify(regName)+".txt";
				
				File outFile = new File(subDir, outFName);
				FileWriter fw = new FileWriter(outFile);
				
				double minLat = Double.POSITIVE_INFINITY;
				double maxLat = Double.NEGATIVE_INFINITY;
				double minLon = Double.POSITIVE_INFINITY;
				double maxLon = Double.NEGATIVE_INFINITY;
				
				for (XY_DataSet xy : regOutlines) {
					fw.write("segment "+regName+"\n");
					String prevLatStr = null;
					String prevLonStr = null;
					for (Point2D pt : xy) {
						double lat = pt.getY();
						double lon = pt.getX();
						minLat = Math.min(minLat, lat);
						maxLat = Math.max(maxLat, lat);
						minLon = Math.min(minLon, lon);
						maxLon = Math.max(maxLon, lon);
						String latStr = outDF.format(lat);
						String lonStr = outDF.format(lon);
						if (prevLatStr != null && prevLatStr.equals(latStr) && prevLonStr.equals(lonStr)) {
							// identical to our precision, skip
							continue;
						}
						prevLatStr = latStr;
						prevLonStr = lonStr;
						fw.write(outDF.format(pt.getY())+" "+outDF.format(pt.getX())+"\n");
					}
				}
				fw.close();
				csv.addLine(dirName+"/"+outFName, regName, (float)minLat+"", (float)minLon+"", (float)maxLat+"", (float)maxLon+"");
			}
		}
		
		csv.writeToFile(new File(outputDir, "mappings.csv"));
	}

	public static void main(String[] args) throws IOException {
		////		initUSOutlines();
		//		String stateName = "New Mexico";
		//		XY_DataSet[] outlines = loadUSState(stateName);
		//		System.out.println("Loaded "+outlines.length+" outlines for "+stateName);

		//		GriddedRegion gridReg = null;
		//		gridReg.getlat
//		writeMappingsIndex(new File("/home/kevin/workspace/scec_vdo_vtk/data/PoliticalBoundaries/sourcefiles"),
//				new File("/home/kevin/workspace/opensha/src/main/resources/data/boundaries"));
		
//		loadDefaultOutlines(NSHM23_RegionLoader.loadFullConterminousUS());
		D = true;
//		loadDefaultOutlines(PRVI25_RegionLoader.loadPRVI_ModelBroad());
		loadDefaultOutlines(new Region(new Location(-31, 177), new Location(-14, 189)));
//		loadCAOutlines();
//		loadNZOutlines();
	}

}
