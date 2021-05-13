/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.calc.hazardMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.geo.Location;

import com.google.common.collect.Lists;

/**
 * This class makes a single hazard map file (GMT format) from a directory structure containing
 * hazard curves. Curves should be binned into subdirectories, and the files should be titled:
 * {lat}_{lon}.txt .
 * 
 * @author kevin
 *
 */
public class MakeXYZFromHazardMapDir {
	
	public static int WRITES_UNTIL_FLUSH = 1000;
	
	private boolean latFirst;
	private boolean sort;
	private String dirName;

	public MakeXYZFromHazardMapDir(String dirName, boolean sort, boolean latFirst) {
		this.dirName = dirName;
		this.latFirst = latFirst;
		this.sort = sort;
	}
	
	public void writeXYZFile(boolean isProbAt_IML, double level, String fileName) throws IOException {
		parseFiles(isProbAt_IML, level, fileName, false);
	}
	
	public ArbDiscrGeoDataSet getXYZDataset(boolean isProbAt_IML, double level) throws IOException {
		return parseFiles(isProbAt_IML, level, null, false);
	}
	
	public ArbDiscrGeoDataSet getXYZDataset(boolean isProbAt_IML, double level, String fileName) throws IOException {
		return parseFiles(isProbAt_IML, level, fileName, true);
	}
	
	private ArbDiscrGeoDataSet parseFiles(boolean isProbAt_IML, double level, String fileName,
			boolean forceLoad) throws IOException {
		// get and list the dir
		System.out.println("Generating XYZ dataset for dir: " + dirName);
		
		BufferedWriter out = null;
		ArbDiscrGeoDataSet xyz = null;
		
		if (fileName != null && fileName.length() > 0) {
			out = new BufferedWriter(new FileWriter(fileName));
		}
		if (out == null || forceLoad) {
			xyz = new ArbDiscrGeoDataSet(latFirst);
		}
		
		int count = 0;
		
		double minLat = Double.MAX_VALUE;
		double minLon = Double.MAX_VALUE;
		double maxLat = Double.MIN_VALUE;
		double maxLon = -9999;
		
		if (dirName.toLowerCase().trim().endsWith(".zip")) {
			// zip file
			ZipFile zip = new ZipFile(dirName);
			
			List<ZipEntry> entriesList = Lists.newArrayList();
			Enumeration<? extends ZipEntry> entriesEnum = zip.entries();
			while (entriesEnum.hasMoreElements())
				entriesList.add(entriesEnum.nextElement());
			
			System.out.println("Found "+entriesList.size()+" entries");
			
			if (sort)
				Collections.sort(entriesList, new ZipEntryComparator());
			
			for (ZipEntry entry : entriesList) {
				String curveFileName = entry.getName();
//				System.out.println(curveFileName);
				if (!curveFileName.endsWith(".txt"))
					continue;
				
				String locNamePart = new File(curveFileName).getName();
				Location loc = HazardDataSetLoader.decodeFileName(locNamePart);
				if (loc != null) {
					double latVal = loc.getLatitude();
					double lonVal = loc.getLongitude();
					//System.out.println("Lat: " + latVal + " Lon: " + lonVal);
					// handle the file
					double writeVal = handleFile(zip.getInputStream(entry), isProbAt_IML, level);
//					out.write(latVal + "\t" + lonVal + "\t" + writeVal + "\n");
					if (latFirst) {
						if (out != null)
							out.write(latVal + "     " + lonVal + "     " + writeVal + "\n");
						if (xyz != null)
							xyz.set(loc, writeVal);
					} else {
						if (out != null)
							out.write(lonVal + "     " + latVal + "     " + writeVal + "\n");
						if (xyz != null)
							xyz.set(loc, writeVal);
					}
					
					if (latVal < minLat)
						minLat = latVal;
					else if (latVal > maxLat)
						maxLat = latVal;
					if (lonVal < minLon)
						minLon = lonVal;
					else if (lonVal > maxLon)
						maxLon = lonVal;
					
					if (out != null && count % MakeXYZFromHazardMapDir.WRITES_UNTIL_FLUSH == 0) {
						System.out.println("Processed " + count + " curves");
						out.flush();
					}
					count++;
				}
			}
		} else {
			File masterDir = new File(dirName);
			File[] dirList=masterDir.listFiles();
			
			if (sort)
				Arrays.sort(dirList, new FileComparator());

			// for each file in the list
			for(File dir : dirList){
				// make sure it's a subdirectory
				if (dir.isDirectory() && !dir.getName().endsWith(".")) {
					File[] subDirList=dir.listFiles();
					for(File file : subDirList) {
						//only taking the files into consideration
						if(file.isFile()){
							String curveFileName = file.getName();
							//files that ends with ".txt"
							if(curveFileName.endsWith(".txt")){
								Location loc = HazardDataSetLoader.decodeFileName(curveFileName);
								if (loc != null) {
									double latVal = loc.getLatitude();
									double lonVal = loc.getLongitude();
									//System.out.println("Lat: " + latVal + " Lon: " + lonVal);
									// handle the file
									double writeVal = handleFile(file.getAbsolutePath(), isProbAt_IML, level);
//									out.write(latVal + "\t" + lonVal + "\t" + writeVal + "\n");
									if (latFirst) {
										if (out != null)
											out.write(latVal + "     " + lonVal + "     " + writeVal + "\n");
										if (xyz != null)
											xyz.set(loc, writeVal);
									} else {
										if (out != null)
											out.write(lonVal + "     " + latVal + "     " + writeVal + "\n");
										if (xyz != null)
											xyz.set(loc, writeVal);
									}
									
									if (latVal < minLat)
										minLat = latVal;
									else if (latVal > maxLat)
										maxLat = latVal;
									if (lonVal < minLon)
										minLon = lonVal;
									else if (lonVal > maxLon)
										maxLon = lonVal;
									
									if (out != null && count % MakeXYZFromHazardMapDir.WRITES_UNTIL_FLUSH == 0) {
										System.out.println("Processed " + count + " curves");
										out.flush();
									}
								}
							}
						}
						count++;
					}
				}
				
				
			}
		}
		
		if (out != null)
			out.close();
		System.out.println("DONE");
		System.out.println("MinLat: " + minLat + " MaxLat: " + maxLat + " MinLon: " + minLon + " MaxLon " + maxLon);
		System.out.println(count + " curves processed!");
		
		return xyz;
	}
	
	public double handleFile(String fileName, boolean isProbAt_IML, double val) {
		try {
			ArbitrarilyDiscretizedFunc func = AbstractDiscretizedFunc.loadFuncFromSimpleFile(fileName);
			
			return HazardDataSetLoader.getCurveVal(func, isProbAt_IML, val);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Double.NaN;
	}
	
	public double handleFile(InputStream is, boolean isProbAt_IML, double val) {
		try {
			ArbitrarilyDiscretizedFunc func = AbstractDiscretizedFunc.loadFuncFromSimpleFile(is);
			
			return HazardDataSetLoader.getCurveVal(func, isProbAt_IML, val);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Double.NaN;
	}
	
	private static class FileComparator implements Comparator<File> {
		
		FileNameComparator c = new FileNameComparator();

		public int compare(File f1, File f2) {
			if(f1 == f2)
				return 0;

			if(f1.isDirectory() && f2.isFile())
				return -1;
			if(f1.isFile() && f2.isDirectory())
				return 1;
			
			return c.compare(f1.getName(), f2.getName());
		}
	}
	
	private static class ZipEntryComparator implements Comparator<ZipEntry> {
		
		FileNameComparator c = new FileNameComparator();

		public int compare(ZipEntry f1, ZipEntry f2) {
			if(f1 == f2)
				return 0;
			
			return c.compare(f1.getName(), f2.getName());
		}
	}
	
	private static class FileNameComparator implements Comparator<String> {
		private static Collator c = Collator.getInstance();
		
		public String invertFileName(String fileName) {
			int index = fileName.indexOf("_");
			int firstIndex = fileName.indexOf(".");
			int lastIndex = fileName.lastIndexOf(".");
			// Hazard data files have 3 "." in their names
			//And leaving the rest of the files which contains only 1"." in their names
			if(firstIndex != lastIndex){

				//getting the lat and Lon values from file names
				String lat = fileName.substring(0,index).trim();
				String lon = fileName.substring(index+1,lastIndex).trim();
				
				return lon + "_" + lat;
			}
			return fileName;
		}

		@Override
		public int compare(String o1, String o2) {
			return c.compare(invertFileName(o1), invertFileName(o2));
		}
	}
	
	public static void main(String args[]) {
		try {
			long start = System.currentTimeMillis();
//			String curveDir = "/home/kevin/OpenSHA/condor/test_results";
//			String curveDir = "/home/kevin/OpenSHA/condor/oldRuns/statewide/test_30000_2/curves";
//			String curveDir = "/home/kevin/OpenSHA/condor/frankel_0.1";
//			String curveDir = "/home/kevin/CyberShake/baseMaps/ave2008/curves_3sec";
//			String curveDir = "/home/kevin/OpenSHA/gem/ceus/curves_0.1/imrs2";
//			String curveDir = "/home/kevin/OpenSHA/UCERF3/test_inversion/maps/ucerf3_inv_state_run_1/imrs1";
//			String curveDir = "/home/kevin/CyberShake/baseMaps/2012_05_22-cvmh/CB2008"; 
//			String outfile = "xyzCurves.txt";
//			String outfile = "/home/kevin/OpenSHA/condor/oldRuns/statewide/test_30000_2/xyzCurves.txt";
//			String outfile = "/home/kevin/CyberShake/baseMaps/ave2008/xyzCurves.txt";
//			String outfile = "/home/kevin/OpenSHA/gem/ceus/curves_0.1/imrs2.txt";
//			String outfile = "/tmp/xyzCurves.txt";
			String curveDir = "/home/kevin/Simulators/maps/2013_04_10-rsqsim-long-la-box-0.05" +
					"-pga-cb2008-30yrs/2013_04_10-rsqsim-long-la-box-0.05-pga-cb2008-30yrs_curves.zip";
			String outfile = "/home/kevin/Simulators/maps/2013_04_10-rsqsim-long-la-box-0.05" +
					"-pga-cb2008-30yrs/2percent_in_30.txt";
//			String curveDir = "/home/kevin/Simulators/maps/2013_04_10-rsqsim-long-la-box-0.05" +
//					"-pga-cb2008-30yrs-quietmojave156/2013_04_10-rsqsim-long-la-box-0.05-pga-cb2008-30yrs-quietmojave156_curves.zip";
//			String outfile = "/home/kevin/Simulators/maps/2013_04_10-rsqsim-long-la-box-0.05" +
//					"-pga-cb2008-30yrs-quietmojave156/10percent_in_30.txt";
//			boolean isProbAt_IML = true;
//			double level = 0.2;
			boolean isProbAt_IML = false;
			double level = 0.02;
//			double level = 0.1;
//			double level = 0.002;		// 10% in 50
//			double level = 0.0004;		// 	2% in 50
			boolean latFirst = true;
			MakeXYZFromHazardMapDir maker = new MakeXYZFromHazardMapDir(curveDir, false, latFirst);
			maker.writeXYZFile(isProbAt_IML, level, outfile);
			System.out.println("took " + ((System.currentTimeMillis() - start) / 1000d) + " secs");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
