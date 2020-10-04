package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.SimpleAzimuthCalc;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRupturePlausibilityDebug {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		System.out.println("Loading rupture set...");
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(
				new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_cmlAz.zip"));
		System.out.println("Loaded "+rupSet.getNumRuptures()+" ruptures");
		
		PlausibilityConfiguration config = rupSet.getPlausibilityConfiguration();
		List<ClusterRupture> clusterRuptures = rupSet.getClusterRuptures();
		if (clusterRuptures == null) {
			rupSet.buildClusterRups(new RuptureConnectionSearch(rupSet, config.getDistAzCalc(),
					config.getConnectionStrategy().getMaxJumpDist(), false));
			clusterRuptures = rupSet.getClusterRuptures();
		}
		
		int[] testIndexes = {2459343, 326516};
		
		PlausibilityFilter[] testFilters = {
				new CumulativeAzimuthChangeFilter(new SimpleAzimuthCalc(config.getDistAzCalc()), 560f),
		};
		
		for (int testIndex : testIndexes) {
			ClusterRupture rup = clusterRuptures.get(testIndex);
			
			System.out.println("===================");
			System.out.println("Rupture "+testIndex);
			System.out.println(rup);
			System.out.println("===================");
			for (PlausibilityFilter filter : testFilters) {
				System.out.println("Testing "+filter.getName());
				PlausibilityResult result = filter.apply(rup, true);
				System.out.println("result: "+result);
				System.out.println("===================");
			}
			if (rup.splays.isEmpty() && rup.clusters.length > 1) {
				// also test by offering jumps
				ClusterRupture newRup = new ClusterRupture(rup.clusters[0]);
				for (int i=1; i<rup.clusters.length-1; i++) {
					Jump jump = newRup.clusters[i-1].getConnectionsTo(rup.clusters[i]).iterator().next();
					newRup = newRup.take(jump);
				}
				FaultSubsectionCluster addition = rup.clusters[rup.clusters.length-1];
				Jump newJump = newRup.clusters[newRup.clusters.length-1].getConnectionsTo(addition)
						.iterator().next();
				System.out.println("Now trying testJump for at last jump for "+testIndex);
				System.out.println("Main rupture: "+newRup);
				System.out.println("Addition: "+newJump);
				System.out.println("===================");
				for (PlausibilityFilter filter : testFilters) {
					System.out.println("Testing "+filter.getName());
					PlausibilityResult result = filter.testJump(newRup, newJump, true);
					System.out.println("result: "+result);
					System.out.println("===================");
				}
				
			}
			System.out.println();
		}
	}

}
