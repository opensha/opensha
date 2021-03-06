package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter.*;
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
//						+ "fm3_1_adapt5_10km_sMax1_slipP0.05incr_cff3_4_IntsPos_comb2Paths_cffP0.05_cffRatioN2P0.2.zip"));
//						+ "fm3_1_plausible10km_slipP0.05incr_cff3_4_IntsPos_comb2Paths_cffP0.05_cffRatioN2P0.5_"
//						+ "sectFractPerm0.05_comp/fm3_1_plausible10km_slipP0.05incr_cff3_4_IntsPos_comb2Paths_"
//						+ "cffFavP0.05_cffFavRatioN2P0.5_sectFractPerm0.05.zip"));
//						+ "fm3_1_plausible10km_slipP0.2incr_cff0.67IntsPos_comb2Paths_cffFavP0.05_cffFavRatioN2P0.5_sectFractPerm0.05.zip"));
//						+ "fm3_1_plausible10km_direct_slipP0.2incr_cff0.67IntsPos_comb2Paths_cffFavP0.05_cffFavRatioN2P0.5_sectFractPerm0.05.zip"));
//						+ "fm3_1_plausible10km_direct_slipP0.1incr_cff0.67IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip"));
//						+ "nz_demo5_crustal_slipP0.01incr_cff3_4_IntsPos_comb3Paths_cffP0.01_cffSPathFav15_cffCPathRPatchHalfPos_sectFractPerm0.05.zip"));
						+ "fm3_1_plausible10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip"));
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
////		int[] testIndexes = { 372703 };
//		int[] testIndexes = { 379782 };
//		
//		List<ClusterRupture> testRuptures = new ArrayList<>();
//		for (int testIndex : testIndexes)
//			testRuptures.add(clusterRuptures.get(testIndex));
		
		// by string
		int[] sectIDs = ClusterRuptureBuilder.loadRupString(
				"[724:243,242][90:557,556,555][723:880,879,878][92:1025,1024][91:991,990,989]", false);
		List<FaultSection> rupSects = new ArrayList<>();
		for (int sectID : sectIDs)
			rupSects.add(rupSet.getFaultSectionData(sectID));
		List<ClusterRupture> testRuptures = new ArrayList<>();
		testRuptures.add(ClusterRupture.forOrderedSingleStrandRupture(rupSects, config.getDistAzCalc()));
		
		
		// for possible whole-parent ruptures
////		int[] parents = {
////				301, // SAF Mojave S
////				286, // SAF Mojave N
//////				287, // SAF Big Bend
//////				300, // SAF Carrizo
////				49, // Garlock W
////				};
//		int[] parents = {
//				103, // Elsinore Coyote Mountains
//				102, // Elsinore Julian
//		};
//////		int startParent = 301;
////		int startParent = -1;
////		int[] parents = {
////				653, // SAF Offshore
////				654, // SAF North Coast
////				655, // SAF Peninsula
////				657, // SAF Santa Cruz
////				658, // SAF Creeping
////				32, // SAF Parkfield
////				285, // SAF Cholame
////				300, // SAF Carrizo
////				287, // SAF Big Bend
////				286, // SAF Mojave N
////				301, // SAF Mojave S
////				282, // SAF SB N
////				283, // SAF SB S
////				284, // SAF SGP-GH
////				295, // SAF Coachella
////				170, // Brawley
////				};
////		int startParent = 102;
//		int startParent = -1;
//		FaultSubsectionCluster startCluster = null;
//		List<FaultSubsectionCluster> clusters = new ArrayList<>();
//		HashSet<Integer> parentIDsSet = new HashSet<>();
//		for (int parent : parents)
//			parentIDsSet.add(parent);
//		for (FaultSubsectionCluster cluster : config.getConnectionStrategy().getClusters()) {
//			if (parentIDsSet.contains(cluster.parentSectionID))
//				clusters.add(cluster);
//			if (cluster.parentSectionID == startParent)
//				startCluster = cluster;
//		}
//		RuptureConnectionSearch connSearch = new RuptureConnectionSearch(rupSet, config.getDistAzCalc());
//		List<Jump> jumps = connSearch.calcRuptureJumps(clusters, true);
//		List<ClusterRupture> testRuptures = new ArrayList<>();
//		testRuptures.add(connSearch.buildClusterRupture(clusters, jumps, true, startCluster));
//		boolean tryLastJump = false;
		// for sub-section ruptures
//		int[] sectIDs = { 1093, 1092, 1091, 1090, 864, 863};
//		List<FaultSection> sects = new ArrayList<>();
//		for (int sectID : sectIDs)
//			sects.add(rupSet.getFaultSectionData(sectID));
//		List<ClusterRupture> testRuptures = new ArrayList<>();
//		testRuptures.add(ClusterRupture.forOrderedSingleStrandRupture(sects, config.getDistAzCalc()));
		
		PlausibilityFilter[] testFilters = null;
//		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
//				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
//		PlausibilityFilter[] testFilters = {
////				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
////						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
////								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
////						config.getConnectionStrategy(), false, false, false)),
////				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
////						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
////								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
////						config.getConnectionStrategy(), false, true, false)),
////				new CumulativeProbabilityFilter(0.02f, new RelativeCoulombProb(
////						new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
////								AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
////						config.getConnectionStrategy(), true, true, false)),
////				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
////						AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM,
////						AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS), Range.greaterThan(0f)),
////				new ClusterCoulombCompatibilityFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
////						AggregationMethod.SUM, AggregationMethod.PASSTHROUGH,
////						AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE), 0.5f),
////				new ClusterPathCoulombCompatibilityFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
////						AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM),
////						Range.atLeast(0f))
////				new CumulativeAzimuthChangeFilter(new SimpleAzimuthCalc(config.getDistAzCalc()), 560f),
////				new ClusterPathCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
////				new NetClusterCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
////				new NetRuptureCoulombFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN,
////						RupCoulombQuantity.SUM_SECT_CFF, 0f),
////				new ClusterCoulombCompatibilityFilter(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f),
////				new CumulativeProbabilityFilter(0.05f, new RelativeSlipRateProb(config.getConnectionStrategy(), true)),
//				new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//						AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT), 0.67f),
//				new DirectPathPlausibilityFilter(config.getConnectionStrategy()),
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
					if (filter instanceof ScalarValuePlausibiltyFilter<?>)
						System.out.println("scalar: "+((ScalarValuePlausibiltyFilter<?>)filter).getValue(rup));
					System.out.println("===================");
				}
			} else if (config.getFilters() != null) {
				System.out.println("Rup Set filters");
				System.out.println("===================");
				for (PlausibilityFilter filter : config.getFilters()) {
					System.out.println("Testing "+filter.getName());
					PlausibilityResult result = filter.apply(rup, true);
					System.out.println("result: "+result);
					if (filter instanceof ScalarValuePlausibiltyFilter<?>)
						System.out.println("scalar: "+((ScalarValuePlausibiltyFilter<?>)filter).getValue(rup));
					System.out.println("===================");
				}
			}
			
			System.out.println();
		}
	}

}
