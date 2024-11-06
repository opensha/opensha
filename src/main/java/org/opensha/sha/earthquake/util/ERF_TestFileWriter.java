package org.opensha.sha.earthquake.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.RuptureSurface;

public class ERF_TestFileWriter {
	
	private static void writeCSV(ERF erf, File outputFile) throws IOException {
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(outputFile));
		CSVWriter writer = new CSVWriter(bout, true);
		
		List<String> header = List.of("Source ID", "Rupture ID", "Magnitude", "Probability", "Surface Type",
				"Surface Length", "Surface Width", "First Location Lat", "First Location Lon", "First Location Depth",
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
				RuptureSurface surf = rup.getRuptureSurface();
				line.add(ClassUtils.getClassNameWithoutPackage(surf.getClass()));
				line.add((float)surf.getAveLength()+"");
				line.add((float)surf.getAveWidth()+"");
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

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		File outputDir = new File("/home/kevin/OpenSHA/erf_test_files/2024_11_06/");
		for (ERF_Ref ref : ERF_Ref.values()) {
			System.out.println(ref);
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
		}
		System.exit(0);
	}

}
