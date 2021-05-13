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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.hazardMap.components.BinaryCurveArchiver;
import org.opensha.sha.calc.hazardMap.components.CurveMetadata;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BinaryCurveAverager {
	
	private List<File> binFiles;
	private List<Double> weights;
	private File outputFile;
	
	public BinaryCurveAverager(List<File> binFiles, File outputFile) {
		this(binFiles, null, outputFile);
	}
	
	public BinaryCurveAverager(List<File> binFiles, List<Double> weights, File outputFile) {
		if (weights ==  null) {
			double weight = 1d/(double)binFiles.size();
			weights = Lists.newArrayList();
			for (int i=0; i<binFiles.size(); i++)
				weights.add(weight);
		} else {
			// normalize
			double weightTot = 0d;
			for (double weight : weights)
				weightTot += weight;
			if (weightTot != 1d) {
				weights = Lists.newArrayList(weights);
				for (int i=0; i<weights.size(); i++)
					weights.set(i, weights.get(i)/weightTot);
			}
		}
		Preconditions.checkArgument(binFiles.size() == weights.size());
		Preconditions.checkArgument(binFiles.size() >= 2);
		this.binFiles = binFiles;
		this.weights = weights;
		this.outputFile = outputFile;		
	}
	
	public void averageDirs() throws Exception {
		List<Map<Location, ArbitrarilyDiscretizedFunc>> maps = Lists.newArrayList();
		for (File binFile : binFiles)
			maps.add(new BinaryHazardCurveReader(binFile.getAbsolutePath()).getCurveMap());
		
		// find the intersection of each location set
		HashSet<Location> commonLocs = null;
		for (Map<Location, ArbitrarilyDiscretizedFunc> map : maps) {
			if (commonLocs == null)
				commonLocs = new HashSet<Location>(map.keySet());
			else
				// intersection with current map
				commonLocs.retainAll(map.keySet());
		}
		
		System.out.println("Averaging "+commonLocs.size()+" curves from "+maps.size()+" sources");
		Preconditions.checkState(!commonLocs.isEmpty());
		
		String fName = outputFile.getName();
		fName = fName.replaceAll(".bin", "");
		
		DiscretizedFunc xVals = maps.get(0).values().iterator().next().deepClone();
		for (int i=0; i<xVals.size(); i++)
			xVals.set(i, 0d);
		Map<String, DiscretizedFunc> xValsMap = Maps.newHashMap();
		xValsMap.put(fName, xVals);
		
//		BinaryCurveArchiver archive = new BinaryCurveArchiver(outputFile.getParentFile(), commonLocs.size(), xValsMap);
//		archive.initialize();
//		
//		int index = 0;
//		for (Location loc : commonLocs) {
//			DiscretizedFunc avgFunc = xVals.deepClone();
//			for (int i=0; i<maps.size(); i++) {
//				double weight = weights.get(i);
//				DiscretizedFunc curve = maps.get(i).get(loc);
//				
//				for (int j=0; j<avgFunc.getNum(); j++)
//					avgFunc.set(j, avgFunc.getY(j)+curve.getY(j)*weight);
//			}
//			CurveMetadata meta = new CurveMetadata(new Site(loc), index, null, fName);
//			archive.archiveCurve(avgFunc, meta);
//			
//			index++;
//		}
//		
//		archive.close();
		
		List<DiscretizedFunc> curves = Lists.newArrayList();
		List<Location> locs = Lists.newArrayList();
		
		for (Location loc : commonLocs) {
			DiscretizedFunc avgFunc = xVals.deepClone();
			for (int i=0; i<maps.size(); i++) {
				double weight = weights.get(i);
				DiscretizedFunc curve = maps.get(i).get(loc);
				
				for (int j=0; j<avgFunc.size(); j++)
					avgFunc.set(j, avgFunc.getY(j)+curve.getY(j)*weight);
			}
			locs.add(loc);
			curves.add(avgFunc);
		}
		
		BinaryHazardCurveWriter writer = new BinaryHazardCurveWriter(outputFile);
		writer.writeCurves(curves, locs);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		List<File> binFiles = Lists.newArrayList();
		File outputFile = null;
		if (args.length > 3) {
			for (int i=0; i<(args.length-1); i++) {
				File binFile = new File(args[i]);
				Preconditions.checkArgument(binFile.exists());
				binFiles.add(binFile);
			}
			outputFile = new File(args[args.length-1]);
		} else {
			System.out.println("USAGE: BinaryCurveAverager file1 file2 file3 [... dirN] outputFile");
			System.exit(2);
		}
		
		BinaryCurveAverager ave = new BinaryCurveAverager(binFiles, outputFile);
		try {
			ave.averageDirs();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
