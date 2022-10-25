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

public class AsciiCurveAverager {
	
	private ArrayList<String> dirs;
	private String outputDir;
	
	public AsciiCurveAverager(ArrayList<String> dirs, String outputDir) {
		File outputDirFile = new File(outputDir);
		
		if (!outputDirFile.exists())
			outputDirFile.mkdirs();
		
		// make sure they all end in File.separator
		if (!outputDir.endsWith(File.separator))
			outputDir += File.separator;
		for (int i=0; i<dirs.size(); i++) {
			if (!dirs.get(i).endsWith(File.separator))
				dirs.set(i, dirs.get(i) + File.separator);
		}
		
		this.dirs = dirs;
		this.outputDir = outputDir;
	}
	
	public void averageDirs() throws IOException {
		// we use the first curve dir to specify all of the points
		
		File masterDir = new File(dirs.get(0));
		File[] dirList = masterDir.listFiles();
		
		// for each file in the list
		for(File dir : dirList){
			// make sure it's a subdirectory
			if (dir.isDirectory() && !dir.getName().endsWith(".")) {
				File[] subDirList=dir.listFiles();
				for(File file : subDirList) {
					String fileName = file.getName();
					
					File outSubDirFile = new File(outputDir + dir.getName());
					if (!outSubDirFile.exists())
						outSubDirFile.mkdir();
					//files that ends with ".txt"
					if(fileName.endsWith(".txt")){
						Location loc = HazardDataSetLoader.decodeFileName(fileName);
						if (loc != null) {
							String relativePath = dir.getName() + File.separator + fileName;
							System.out.println(relativePath);
							
							ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
							for (String curveDir : dirs) {
								funcs.add(ArbitrarilyDiscretizedFunc.loadFuncFromSimpleFile(curveDir + relativePath));
							}
							
							DiscretizedFunc aveCurve = averageCurves(funcs);
							
							ArbitrarilyDiscretizedFunc.writeSimpleFuncFile(aveCurve, outputDir + relativePath);
						}
					}
				}
			}
		}
	}
	
	public static DiscretizedFunc averageCurves(ArrayList<DiscretizedFunc> curves) {
		if (curves.size() < 2) {
			throw new RuntimeException("At least 2 curves must be given to average.");
		}
		
		ArbitrarilyDiscretizedFunc aveFunc = new ArbitrarilyDiscretizedFunc();
		
		int numPoints = curves.get(0).size();
		
		// verify that they all have the same # of points
		for (DiscretizedFunc curve : curves) {
			if (numPoints != curve.size())
				throw new RuntimeException("All curves must have the same # of points!");
		}
		
		for (int i=0; i<numPoints; i++) {
			double x = curves.get(0).getX(i);
			double y = 0;
			
			for (DiscretizedFunc curve : curves) {
				if (x != curve.getX(i))
					throw new RuntimeException("X values on curve don't match!");
				y += curve.getY(i);
			}
			
			y /= (double)curves.size();
			
			aveFunc.set(x, y);
		}
		
		return aveFunc;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArrayList<String> dirs = new ArrayList<String>();
		String outputDir = null;
		if (args.length > 3) {
			for (int i=0; i<(args.length-1); i++) {
				dirs.add(args[i]);
			}
			outputDir = args[args.length-1];
		} else {
			System.out.println("USAGE: CurveAverager dir1 dir2 dir3 [... dirN] outputDir");
			System.exit(2);
		}
		
		AsciiCurveAverager ave = new AsciiCurveAverager(dirs, outputDir);
		try {
			ave.averageDirs();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
