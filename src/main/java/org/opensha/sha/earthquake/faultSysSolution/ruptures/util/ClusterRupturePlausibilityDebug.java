package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.ClusterCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.ClusterPathCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.NetClusterCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.SimpleAzimuthCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.RuptureCoulombResult.RupCoulombQuantity;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;

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
		
		// for specific ruptures by ID
//		List<ClusterRupture> clusterRuptures = rupSet.getClusterRuptures();
//		if (clusterRuptures == null) {
//			rupSet.buildClusterRups(new RuptureConnectionSearch(rupSet, config.getDistAzCalc(),
//					config.getConnectionStrategy().getMaxJumpDist(), false));
//			clusterRuptures = rupSet.getClusterRuptures();
//		}
//		
//		int[] testIndexes = {2459343, 326516};
//		
//		List<ClusterRupture> testRuptures = new ArrayList<>();
//		for (int testIndex : testIndexes)
//			testRuptures.add(clusterRuptures.get(testIndex));
//		boolean tryLastJump = true;
		
		// for possible whole-parent ruptures
		int[] parents = {
				301, // SAF Mojave S
				286, // SAF Mojave N
				287, // SAF Big Bend
				300, // SAF Carrizo
				49, // Garlock W
				};
//		int startParent = 301;
		int startParent = -1;
		FaultSubsectionCluster startCluster = null;
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		HashSet<Integer> parentIDsSet = new HashSet<>();
		for (int parent : parents)
			parentIDsSet.add(parent);
		for (FaultSubsectionCluster cluster : config.getConnectionStrategy().getClusters()) {
			if (parentIDsSet.contains(cluster.parentSectionID))
				clusters.add(cluster);
			if (cluster.parentSectionID == startParent)
				startCluster = cluster;
		}
		RuptureConnectionSearch connSearch = new RuptureConnectionSearch(rupSet, config.getDistAzCalc());
		List<Jump> jumps = connSearch.calcRuptureJumps(clusters, true);
		List<ClusterRupture> testRuptures = new ArrayList<>();
		testRuptures.add(connSearch.buildClusterRupture(clusters, jumps, true, startCluster));
		boolean tryLastJump = false;
		
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5);
		PlausibilityFilter[] testFilters = {
//				new CumulativeAzimuthChangeFilter(new SimpleAzimuthCalc(config.getDistAzCalc()), 560f),
				new ClusterPathCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
				new NetClusterCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
				new NetRuptureCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN,
						RupCoulombQuantity.SUM_SECT_CFF, 0f),
				new ClusterCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
		};
		
		for (ClusterRupture rup : testRuptures) {
			System.out.println("===================");
			System.out.println(rup);
			System.out.println("===================");
			for (PlausibilityFilter filter : testFilters) {
				System.out.println("Testing "+filter.getName());
				PlausibilityResult result = filter.apply(rup, true);
				System.out.println("result: "+result);
				System.out.println("===================");
			}
			if (tryLastJump && rup.splays.isEmpty() && rup.clusters.length > 1) {
				// also test by offering jumps
				ClusterRupture newRup = new ClusterRupture(rup.clusters[0]);
				for (int i=1; i<rup.clusters.length-1; i++) {
					Jump jump = newRup.clusters[i-1].getConnectionsTo(rup.clusters[i]).iterator().next();
					newRup = newRup.take(jump);
				}
				FaultSubsectionCluster addition = rup.clusters[rup.clusters.length-1];
				Jump newJump = newRup.clusters[newRup.clusters.length-1].getConnectionsTo(addition)
						.iterator().next();
				System.out.println("Now trying testJump for at last jump");
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
