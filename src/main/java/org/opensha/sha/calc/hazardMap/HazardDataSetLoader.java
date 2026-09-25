package org.opensha.sha.calc.hazardMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.HazardCurveUtils;
import org.opensha.commons.util.FileNameComparator;

public class HazardDataSetLoader {
	
	/**
	 * Recursively loads all of the arbitrarily discretized functions at locations in the given
	 * directory structure. Files should be named "lat_lon".txt.
	 * 
	 * @param dir
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static HashMap<Location, ArbitrarilyDiscretizedFunc> loadDataSet(File dir) throws FileNotFoundException, IOException {
		HashMap<Location, ArbitrarilyDiscretizedFunc> curves = new HashMap<Location, ArbitrarilyDiscretizedFunc>();
		
		File[] files = dir.listFiles();
		Arrays.sort(files, new FileNameComparator());
		
		for (File file : files) {
			if (shouldSkip(file))
				continue;
			
			// recursively parse dirs
			if (file.isDirectory()) {
				curves.putAll(loadDataSet(file));
				continue;
			}
			
			Location loc = decodeFileName(file.getName());
			if (loc == null) {
				System.err.println("Could not decode filename, skipping: " + file.getAbsolutePath());
				continue;
			}
			ArbitrarilyDiscretizedFunc curve =
					ArbitrarilyDiscretizedFunc.loadFuncFromSimpleFile(file.getAbsolutePath());
			curves.put(loc, curve);
		}
		return curves;
	}
	
	public static boolean shouldSkip(File file) {
		if (file.getName().startsWith("."))
			return true;
		
		// make sure it's the right format
		if (file.isFile() && !file.getName().endsWith(".txt"))
			return true;
		return false;
	}
	
	public static GeoDataSet extractPointFromCurves(
			Map<Location, ArbitrarilyDiscretizedFunc> curves,
			boolean isProbAt_IML, double level) {
		GeoDataSet xyz = new ArbDiscrGeoDataSet(true);
		for (Location loc : curves.keySet()) {
			ArbitrarilyDiscretizedFunc curve = curves.get(loc);
			double val = getCurveVal(curve, isProbAt_IML, level);
			xyz.set(loc, val);
		}
		return xyz;
	}
	
	public static GeoDataSet extractPointFromCurves(File dir,
			boolean isProbAt_IML, double level) throws FileNotFoundException, IOException {
		ArbDiscrGeoDataSet xyz = new ArbDiscrGeoDataSet(true);
		
		File[] files = dir.listFiles();
		Arrays.sort(files, new FileNameComparator());
		
		for (File file : files) {
			if (shouldSkip(file))
				continue;
			
			// recursively parse dirs
			if (file.isDirectory()) {
				xyz.setAll(extractPointFromCurves(file, isProbAt_IML, level));
				continue;
			}
			
			Location loc = decodeFileName(file.getName());
			if (loc == null) {
				System.err.println("Could not decode filename, skipping: " + file.getAbsolutePath());
				continue;
			}
			ArbitrarilyDiscretizedFunc curve =
					ArbitrarilyDiscretizedFunc.loadFuncFromSimpleFile(file.getAbsolutePath());
			double val = getCurveVal(curve, isProbAt_IML, level);
			xyz.set(loc, val);
		}
		return xyz;
	}

	public static double getCurveVal(DiscretizedFunc func, boolean isProbAt_IML, double level) {
		return isProbAt_IML ? HazardCurveUtils.getExceedanceProbability(func, level)
				: HazardCurveUtils.getIML(func, level);
	}

	/**
	 * Decodes a filename of the format lat_lon.txt
	 * 
	 * @param fileName
	 * @return
	 */
	public static Location decodeFileName(String fileName) {
		int index = fileName.indexOf("_");
		int firstIndex = fileName.indexOf(".");
		int lastIndex = fileName.lastIndexOf(".");
		// Hazard data files have 3 "." in their names
		//And leaving the rest of the files which contains only 1"." in their names
		try {
			if(firstIndex != lastIndex){

				//getting the lat and Lon values from file names
				Double latVal = Double.valueOf(fileName.substring(0,index).trim());
				Double lonVal = Double.valueOf(fileName.substring(index+1,lastIndex).trim());
				return new Location(latVal, lonVal);
			} else {
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	public static void main(String args[]) throws FileNotFoundException, IOException {
		long start = System.currentTimeMillis();
		String curveDir = "/home/kevin/OpenSHA/gem/ceus/curves_0.1/imrs2";
		String outfile = "/home/kevin/OpenSHA/gem/ceus/curves_0.1/imrs2_new.txt";
		boolean isProbAt_IML = false;
		double level = 0.02;
		
//		HashMap<Location, ArbitrarilyDiscretizedFunc> curves = loadDataSet(new File(curveDir));
//		XYZ_DataSetAPI xyz = extractPointFromCurves(curves, isProbAt_IML, level);
		
		GeoDataSet xyz = extractPointFromCurves(new File(curveDir), isProbAt_IML, level);
		ArbDiscrGeoDataSet.writeXYZFile(xyz, outfile);
		
		System.out.println("took " + ((System.currentTimeMillis() - start) / 1000d) + " secs");
	}

}
