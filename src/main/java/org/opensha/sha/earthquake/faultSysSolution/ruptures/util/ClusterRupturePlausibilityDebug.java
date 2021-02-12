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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.NetClusterCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.PathPlausibilityFilter.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.SimpleAzimuthCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.collect.Range;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRupturePlausibilityDebug {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		System.out.println("Loading rupture set...");
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(
//				new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_cmlAz.zip"));
				new File("/home/kevin/OpenSHA/UCERF4/rup_sets/"
						+ "fm3_1_adapt5_10km_sMax1_slipP0.01incr_cff3_4_IntsPos_comb3Paths_cffP0.01_cffSPathFav15_cffCPathRPatchHalfPos.zip"));
//						+ "nz_demo5_crustal_slipP0.01incr_cff3_4_IntsPos_comb3Paths_cffP0.01_cffSPathFav15_cffCPathRPatchHalfPos_sectFractPerm0.05.zip"));
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
////		int[] testIndexes = { 199428 };
//		int[] testIndexes = { 127180 };
//		
//		List<ClusterRupture> testRuptures = new ArrayList<>();
//		for (int testIndex : testIndexes)
//			testRuptures.add(clusterRuptures.get(testIndex));
//		boolean tryLastJump = true;
		
		// for possible whole-parent ruptures
//		int[] parents = {
//				301, // SAF Mojave S
//				286, // SAF Mojave N
////				287, // SAF Big Bend
////				300, // SAF Carrizo
//				49, // Garlock W
//				};
		int[] parents = {
				103, // Elsinore Coyote Mountains
				102, // Elsinore Julian
		};
////		int startParent = 301;
//		int startParent = -1;
//		int[] parents = {
//				653, // SAF Offshore
//				654, // SAF North Coast
//				655, // SAF Peninsula
//				657, // SAF Santa Cruz
//				658, // SAF Creeping
//				32, // SAF Parkfield
//				285, // SAF Cholame
//				300, // SAF Carrizo
//				287, // SAF Big Bend
//				286, // SAF Mojave N
//				301, // SAF Mojave S
//				282, // SAF SB N
//				283, // SAF SB S
//				284, // SAF SGP-GH
//				295, // SAF Coachella
//				170, // Brawley
//				};
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
		
//		PlausibilityFilter[] testFilters = null;
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
		PlausibilityFilter[] testFilters = {
//				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//						AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
//						config.getConnectionStrategy(), false, true, true)),
//				new CumulativeProbabilityFilter(0.1f, new RelativeSlipRateProb(config.getConnectionStrategy(), false)),
//				new CumulativeProbabilityFilter(0.1f, new RelativeSlipRateProb(config.getConnectionStrategy(), true)),
//				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//						AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.FRACT_POSITIVE), 0.75f),
				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
						AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.FLAT_SUM, AggregationMethod.NUM_NEGATIVE), Range.atMost(5f)),
//				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//						AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.FLAT_SUM, AggregationMethod.NUM_NEGATIVE), Range.atMost(5f)),
				new PathPlausibilityFilter(new SectCoulombPathEvaluator(
						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
						AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
						Range.atLeast(0f), PlausibilityResult.FAIL_HARD_STOP, true, 15f, config.getDistAzCalc())),
		};
//		PlausibilityFilter[] testFilters = {
//				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
//						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
//						config.getConnectionStrategy(), false, false, false)),
//				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
//						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
//						config.getConnectionStrategy(), false, true, false)),
//				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
//						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
//						config.getConnectionStrategy(), true, true, false)),
//				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//						AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM,
//						AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS), Range.greaterThan(0f)),
//				new ClusterCoulombCompatibilityFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//						AggregationMethod.SUM, AggregationMethod.PASSTHROUGH,
//						AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE), 0.5f),
//				new ClusterPathCoulombCompatibilityFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//						AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
//						Range.atLeast(0f))
////				new CumulativeAzimuthChangeFilter(new SimpleAzimuthCalc(config.getDistAzCalc()), 560f),
////				new ClusterPathCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
////				new NetClusterCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
////				new NetRuptureCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN,
////						RupCoulombQuantity.SUM_SECT_CFF, 0f),
////				new ClusterCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
//		};
		
		for (ClusterRupture rup : testRuptures) {
			System.out.println("===================");
			System.out.println(rup);
			System.out.println("===================");
			if (testFilters != null) {
				System.out.println("Test filters");
				System.out.println("===================");
				for (PlausibilityFilter filter : testFilters) {
					System.out.println("Testing "+filter.getName());
					PlausibilityResult result = filter.apply(rup, true);
					System.out.println("result: "+result);
					System.out.println("===================");
				}
			} else if (config.getFilters() != null) {
				System.out.println("Rup Set filters");
				System.out.println("===================");
				for (PlausibilityFilter filter : config.getFilters()) {
					System.out.println("Testing "+filter.getName());
					PlausibilityResult result = filter.apply(rup, true);
					System.out.println("result: "+result);
					System.out.println("===================");
				}
			}
			
			System.out.println();
		}
	}

}
