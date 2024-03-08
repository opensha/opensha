package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

public class RuptureJumpDistanceWriter {

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("USAGE: <rupture-set.zip> <output-file.csv>");
			System.exit(1);
		}
		
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(args[0]));
		
		ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
		if (cRups == null) {
			// assume single stranded
			System.out.println("Building rupture representation assuming single-stranded ruptures (no splays)");
			cRups = ClusterRuptures.singleStranged(rupSet);
		}
		
		CSVFile<String> csv = new CSVFile<>(false);
		csv.addLine("Rupture Index", "Jump Count", "Cumulative Jump Distance (km)", "Maximum Jump Distance (km)", "Jump 1 (km)", "...", "Jump N (km)");
		
		for (int r=0; r<cRups.size(); r++) {
			ClusterRupture rup = cRups.get(r);
			List<String> line = new ArrayList<>();
			line.add(r+"");
			
			double cmlDist = 0d;
			double maxDist = 0d;
			List<Double> dists = new ArrayList<>();
			for (Jump jump : rup.getJumpsIterable()) {
				maxDist = Math.max(maxDist, jump.distance);
				cmlDist += jump.distance;
				dists.add(jump.distance);
			}
			
			line.add(dists.size()+"");
			line.add((float)cmlDist+"");
			line.add((float)maxDist+"");
			for (double dist : dists)
				line.add((float)dist+"");
			
			csv.addLine(line);
		}
		
		File outputFile = new File(args[1]);
		System.out.println("Writing CSV: "+outputFile.getAbsolutePath());
		csv.writeToFile(outputFile);
	}

}
