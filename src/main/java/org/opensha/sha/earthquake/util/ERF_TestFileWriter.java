package org.opensha.sha.earthquake.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public class ERF_TestFileWriter {
	
	private static void writeCSV(ERF erf, File outputFile) throws IOException {
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(outputFile));
		CSVWriter writer = new CSVWriter(bout, true);
		
		List<String> header = List.of("Source ID", "Rupture ID", "Magnitude", "Probability", "Rake", "Surface Type",
				"Surface Length", "Surface Width", "Surface Top Depth", "Surface Dip",
				"First Location Lat", "First Location Lon", "First Location Depth",
				"rJB at 100km", "rRup at 100km", "rSeis at 100km", "rX at 100km");
		
		writer.write(header);
		
		int numSources = erf.getNumSources();
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			int numRups = source.getNumRuptures();
			for (int rupID=0; rupID<numRups; rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				List<String> line = new ArrayList<>(header.size());
				
				line.add(sourceID+"");
				line.add(rupID+"");
				line.add((float)rup.getMag()+"");
				line.add((float)rup.getProbability()+"");
				line.add((float)rup.getAveRake()+"");
				RuptureSurface surf = rup.getRuptureSurface();
				line.add(ClassUtils.getClassNameWithoutPackage(surf.getClass()));
				line.add((float)surf.getAveLength()+"");
				line.add((float)surf.getAveWidth()+"");
				line.add((float)surf.getAveRupTopDepth()+"");
				line.add((float)surf.getAveDip()+"");
				Location loc0 = surf.getEvenlyDiscretizedLocation(0);
				line.add((float)loc0.lat+"");
				line.add((float)loc0.lon+"");
				line.add((float)loc0.depth+"");
				Location testLoc = LocationUtils.location(loc0, 0d, 100d);
				line.add((float)surf.getDistanceJB(testLoc)+"");
				line.add((float)surf.getDistanceRup(testLoc)+"");
				line.add((float)surf.getDistanceSeis(testLoc)+"");
				line.add((float)surf.getDistanceX(testLoc)+"");
				
				writer.write(line);
			}
		}
		
		writer.flush();
		bout.close();
	}
	
	private static void compareDirs(File refDir, File testDir) throws IOException {
		File[] files = refDir.listFiles();
		Arrays.sort(files, new FileNameComparator());
		Joiner j = Joiner.on(", ");
		for (File file : files) {
			if (!file.getName().endsWith(".csv"))
				continue;
			File testFile = new File(testDir, file.getName());
			
			System.out.println("Testing "+file.getName());
			System.out.flush();
			if (!testFile.exists()) {
				System.err.println("\tDoesn't exist in "+testDir.getAbsolutePath());
				System.err.flush();
			} else {
				CSVFile<String> refCSV = CSVFile.readFile(file, true);
				CSVFile<String> testCSV = CSVFile.readFile(testFile, true);
				
				List<String> header = refCSV.getLine(0);
				
				int rows1 = refCSV.getNumRows();
				int rows2 = testCSV.getNumRows();
				Preconditions.checkState(refCSV.getNumCols() == testCSV.getNumCols());
				int cols = refCSV.getNumCols();
				
				int maxRows = Integer.max(rows1, rows2);
				
				boolean pass = true;
				
				for (int row=1; row<maxRows; row++) {
					if (row == rows1) {
						System.err.println("\tTest has "+(rows2-rows1)+" extra rows; first: "+j.join(testCSV.getLine(row)));
						System.err.flush();
						pass = false;
						break;
					} else if (row == rows2) {
						System.err.println("\tReference has "+(rows1-rows2)+" extra rows; first: "+j.join(refCSV.getLine(row)));
						System.err.flush();
						pass = false;
						break;
					}
					// validate them
					for (int col=0; col<cols; col++) {
						String refStr = refCSV.get(row, col);
						String testStr = testCSV.get(row, col);
						if (!refStr.equals(testStr)) {
							System.err.println("\tMismatch at row "+row+", col "+col+" ("+header.get(col)+"): '"+refStr+"' != '"+testStr+"'");
							System.err.println("\tRef line:\t"+j.join(refCSV.getLine(row)));
							System.err.println("\tTest line:\t"+j.join(testCSV.getLine(row)));
							System.err.flush();
							pass = false;
							break;
						}
					}
					if (!pass)
						break;
				}
				
				if (pass)
					System.out.println("\tPerfect match");
			}
			
			System.out.println();
			System.out.flush();
		}
	}

	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		File outputDir = new File("/home/kevin/OpenSHA/erf_test_files/2024_11_06/");
		
//		compareDirs(new File(outputDir, "master"), new File(outputDir, "point_source_refactor"));
		
		outputDir = new File(outputDir, "master");
		for (ERF_Ref ref : ERF_Ref.values()) {
			System.out.println(ref);
			System.out.flush();
			File outputFile = new File(outputDir, ref.name()+".csv");
			try {
				BaseERF baseERF = ref.instance();
				if (baseERF instanceof ERF) {
					ERF erf = (ERF)baseERF;
					
					erf.updateForecast();
					
					writeCSV(erf, outputFile);
				} else {
					System.err.println("Skipping ERF of type "+baseERF.getClass().getName());
				}
			} catch (Throwable e) {
				e.printStackTrace();
				if (outputFile.exists())
					outputFile.delete();
			}
			System.out.flush();
			System.err.flush();
			System.out.println();
			System.out.flush();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.exit(0);
	}

}
