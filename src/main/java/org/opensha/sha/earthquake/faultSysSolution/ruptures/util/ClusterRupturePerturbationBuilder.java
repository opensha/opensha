package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayCountFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayLengthFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.U3CompatibleCumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.AdaptiveClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptiveRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptiveRuptureGrowingStrategy.ConnPointCleanupFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveUnilateralRuptureGrowingStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRupturePerturbationBuilder {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		File markdownDir = new File("/home/kevin/markdown/rupture-sets");
		
//		String primaryName = "Plausible 10km, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.02], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausible10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
//		String primaryName = "Plausible 10km (MultiEnds), Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.02], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
//		String primaryName = "Plausible 10km (MultiEnds), Rake≤360, Jump P>0.001, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.01], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti10km_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip");
//		String primaryName = "Plausible 10km (MultiEnds), Rake≤360, Jump P>0.001, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.01], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti10km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip");
//		String primaryName = "Plausible Adaptive 5-10km (MultiEnds), Rake≤360, Jump P>0.001, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.01], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti10km_adaptive5km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip");
//		String primaryName = "Plausible Adaptive 6-15km (MultiEnds), Rake≤360, Jump P>0.001, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.01], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip");
		String primaryName = "Plausible Adaptive 6-15km (MultiEnds), Rake≤360, Jump P>0.001, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.01], 10% Fract Increase";
		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
		// MAKE SURE THESE ARE UP TO DATE ********************************************
		float cffFractInts = 0.75f;
		int cffRatioN = 2;
		float cffRatioThresh = 0.5f;
		float cffRelativeProb = 0.01f;
		boolean favorableJumps = true;
		float sectGrowFract = 0.1f;
		double[] altJumpDists = { 5d, 6d, 8d, 10d, 15d, 20d };
		double adaptiveR0 = 6d;
		// END plausibility params
		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
		boolean rebuild = false;
		boolean replot = false;
		boolean skipPlausibility = true; // in plots
		
		RuptureGrowingStrategy primaryGrowingStrat;
		if (sectGrowFract > 0f)
			primaryGrowingStrat = new SectCountAdaptiveRuptureGrowingStrategy(sectGrowFract, true, 2);
		else
			primaryGrowingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(primaryFile);
		PlausibilityConfiguration primaryConfig = rupSet.getPlausibilityConfiguration();
		Preconditions.checkNotNull(primaryConfig);
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		// alternate connection strategies
		SectionDistanceAzimuthCalculator distAzCalc = primaryConfig.getDistAzCalc();
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		AggregatedStiffnessCalculator fractIntsAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		
		System.out.println("Initializing alt connection strategies");
		double origMaxJumpDist = primaryConfig.getConnectionStrategy().getMaxJumpDist();
		List<ClusterConnectionStrategy> altConnStrats = new ArrayList<>();
		altConnStrats.add(new DistCutoffClosestSectClusterConnectionStrategy(rupSet.getFaultSectionDataList(),
				primaryConfig.getDistAzCalc(), origMaxJumpDist));
		List<PlausibilityFilter> origConnFilters = buildPlausibleConnFilters(
				distAzCalc, new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d), sumAgg,
				fractIntsAgg, cffFractInts, cffRatioN, cffRatioThresh, cffRelativeProb, favorableJumps,
				origMaxJumpDist);
		ClusterConnectionStrategy singleConnStrat = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, origMaxJumpDist,
				PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT_SINGLE, origConnFilters);
		if (adaptiveR0 > 0d) {
			// add regular distance cutoff, but adaptive
			altConnStrats.add(new AdaptiveClusterConnectionStrategy(altConnStrats.get(0), adaptiveR0, 1));
			// add single conn
			altConnStrats.add(new AdaptiveClusterConnectionStrategy(singleConnStrat, adaptiveR0, 1));
			// add regular but non-adaptive
			altConnStrats.add(new PlausibleClusterConnectionStrategy(subSects, distAzCalc, origMaxJumpDist,
					PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, origConnFilters));
			// add different adaptive distances
			for (double altR0 : altJumpDists) {
				if ((float)altR0 != (float)adaptiveR0 && (float)altR0 < (float)origMaxJumpDist) {
					PlausibleClusterConnectionStrategy plausible = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, origMaxJumpDist,
							PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, origConnFilters);
					altConnStrats.add(new AdaptiveClusterConnectionStrategy(plausible, altR0, 1));
				}
			}
		} else {
			altConnStrats.add(singleConnStrat);
		}
		
		for (double maxJumpDist : altJumpDists) {
			if ((float)maxJumpDist == (float)origMaxJumpDist)
				continue;
			List<PlausibilityFilter> connFilters = buildPlausibleConnFilters(
					distAzCalc, new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d), sumAgg,
					fractIntsAgg, cffFractInts, cffRatioN, cffRatioThresh, cffRelativeProb, favorableJumps,
					maxJumpDist);
			PlausibleClusterConnectionStrategy plausible = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
					PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, connFilters);
			if (adaptiveR0 > 0d && (float)adaptiveR0 < (float)maxJumpDist)
				altConnStrats.add(new AdaptiveClusterConnectionStrategy(plausible, adaptiveR0, 1));
			else
				altConnStrats.add(plausible);
		}
		
		System.out.println("Initializing alt growing strategies");
		// alternate growing strategies
		List<RuptureGrowingStrategy> altGrowStrats = new ArrayList<>();
		altGrowStrats.add(new ExhaustiveUnilateralRuptureGrowingStrategy());
		if (sectGrowFract != 0.1f)
			altGrowStrats.add(new SectCountAdaptiveRuptureGrowingStrategy(0.1f, true, 2));
		if (sectGrowFract != 0.05f)
			altGrowStrats.add(new SectCountAdaptiveRuptureGrowingStrategy(0.05f, true, 2));
		altGrowStrats.add(new SectCountAdaptiveRuptureGrowingStrategy(
				new ExhaustiveBilateralRuptureGrowingStrategy(SecondaryVariations.EQUAL_LEN, false), sectGrowFract, true, 2));
		altGrowStrats.add(new SectCountAdaptiveRuptureGrowingStrategy(
				new ExhaustiveBilateralRuptureGrowingStrategy(SecondaryVariations.SINGLE_FULL, false), sectGrowFract, true, 2));
		altGrowStrats.add(new SectCountAdaptiveRuptureGrowingStrategy(
				new ExhaustiveBilateralRuptureGrowingStrategy(SecondaryVariations.ALL, false), sectGrowFract, true, 2));
		
		System.out.println("Primnary has "+rupSet.getNumRuptures()+" ruptures");
		List<PlausibilityFilter> filters = primaryConfig.getFilters();
		ClusterConnectionStrategy primaryConnStrat = primaryConfig.getConnectionStrategy();
		
		String primaryPrefix = primaryFile.getName().replace(".zip", "");
		File indexDir = new File(markdownDir, primaryPrefix);
		Preconditions.checkState(indexDir.exists() || indexDir.mkdir());
		File outputDir = new File(rupSetsDir, primaryPrefix+"_comp");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		System.out.println("Adding filter-removal configs");
		List<PlausibilityConfiguration> configs = new ArrayList<>();
		List<RuptureGrowingStrategy> growingStrats = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<String> prefixes = new ArrayList<>();
		// add filter removal configurations
		for (int f=0; f<filters.size(); f++) {
			PlausibilityFilter filter = filters.get(f);
			if (filter instanceof ConnPointCleanupFilter)
				continue;
			List<PlausibilityFilter> otherFilters = new ArrayList<>(filters);
			otherFilters.remove(f);
			if (filter instanceof PathPlausibilityFilter && ((PathPlausibilityFilter)filter).getEvaluators().length > 1) {
				// split remove them one at a time
				PathPlausibilityFilter pathFilter = (PathPlausibilityFilter)filter;
				NucleationClusterEvaluator[] evals = pathFilter.getEvaluators();
				for (int e=0; e<evals.length; e++) {
					NucleationClusterEvaluator[] oEvals = new NucleationClusterEvaluator[evals.length-1];
					for (int i=0; i<e; i++)
						oEvals[i] = evals[i];
					for (int i=e+1; i<evals.length; i++)
						oEvals[i-1] = evals[i];
					PathPlausibilityFilter oFilter = new PathPlausibilityFilter(
							pathFilter.getFractPassThreshold(), pathFilter.isLogicalOr(), oEvals);
					List<PlausibilityFilter> myFilters = new ArrayList<>(otherFilters);
					myFilters.add(oFilter);
					configs.add(new PlausibilityConfiguration(myFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
					names.add("Sans Filter: "+evals[e].getName());
					prefixes.add("sans_"+fileSafe(evals[e].getShortName()));
					growingStrats.add(primaryGrowingStrat);
				}
				// now add one version where they're separate paths
				List<PlausibilityFilter> myFilters = new ArrayList<>(otherFilters);
				for (NucleationClusterEvaluator eval : evals)
					myFilters.add(new PathPlausibilityFilter(pathFilter.getFractPassThreshold(), pathFilter.isLogicalOr(), eval));
				configs.add(new PlausibilityConfiguration(myFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add(evals.length+" Path Filters Separated");
				prefixes.add("separate_paths");
				growingStrats.add(primaryGrowingStrat);
			} else {
				configs.add(new PlausibilityConfiguration(otherFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add("Sans Filter: "+filter.getName());
				prefixes.add("sans_"+fileSafe(filter.getShortName()));
				growingStrats.add(primaryGrowingStrat);
			}
		}
		
		System.out.println("Adding UCERF3-related alternatives");
		// add UCERF3 filters
		Builder builder = PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).minSectsPerParent(2, true, true).u3Azimuth()
				.cumulativeRakeChange(180f).cumulativeAzChange(560f);
		if (primaryGrowingStrat instanceof SectCountAdaptiveRuptureGrowingStrategy)
			builder.add(((SectCountAdaptiveRuptureGrowingStrategy)primaryGrowingStrat).buildConnPointCleanupFilter(primaryConnStrat));
		configs.add(builder.build());
		names.add("UCERF3 Azimuth & Cumulative Filters");
		prefixes.add("ucerf_filters_sans_coulomb");
		growingStrats.add(primaryGrowingStrat);

		boolean hasCmlAz = false;
		for (PlausibilityFilter filter : filters)
			if (filter instanceof CumulativeAzimuthChangeFilter)
				hasCmlAz = true;
		if (!hasCmlAz) {
			// add cumulative azimuth
			List<PlausibilityFilter> plusAz = new ArrayList<>(filters);
			plusAz.add(new CumulativeAzimuthChangeFilter(new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
			configs.add(PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).addAll(plusAz).build());
			names.add("Add Cumulative Azimuth");
			prefixes.add("add_CumulativeAzimuth");
			growingStrats.add(primaryGrowingStrat);
		}
		
		boolean hasCmlRake = false;
		for (PlausibilityFilter filter : filters)
			if (filter instanceof CumulativeRakeChangeFilter || filter instanceof U3CompatibleCumulativeRakeChangeFilter)
				hasCmlRake = true;
		if (!hasCmlRake) {
			// add cumulative azimuth
			List<PlausibilityFilter> plusRake = new ArrayList<>(filters);
			plusRake.add(new CumulativeRakeChangeFilter(180f));
			configs.add(PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).addAll(plusRake).build());
			names.add("Add Cumulative Rake<=180");
			prefixes.add("add_CumulativeRake");
			growingStrats.add(primaryGrowingStrat);
		}
		
		boolean hasCmlJump = false;
		for (PlausibilityFilter filter : filters)
			if (filter instanceof CumulativeProbabilityFilter
					&& ((CumulativeProbabilityFilter)filter).getProbCalcs()[0] instanceof Shaw07JumpDistProb)
				hasCmlJump = true;
		if (hasCmlRake && hasCmlJump) {
			// add cumulative azimuth
			List<PlausibilityFilter> withoutRakeJump = new ArrayList<>(filters);
			for (int r=withoutRakeJump.size(); --r>=0;) {
				PlausibilityFilter filter = filters.get(r);
				if (filter instanceof CumulativeRakeChangeFilter || filter instanceof U3CompatibleCumulativeRakeChangeFilter
						|| (filter instanceof CumulativeProbabilityFilter
								&& ((CumulativeProbabilityFilter)filter).getProbCalcs()[0] instanceof Shaw07JumpDistProb))
					withoutRakeJump.remove(r);
			}
			Preconditions.checkState(withoutRakeJump.size() == filters.size()-2);
			configs.add(PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).addAll(withoutRakeJump).build());
			names.add("Sans Filters: Cumulative Rake & Jump Distance");
			prefixes.add("sans_CumulativeRakeAndJump");
			growingStrats.add(primaryGrowingStrat);
		}
		
		// add UCERF3 filters
		builder = PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).minSectsPerParent(2, true, true)
				.cumulativeRakeChange(180f).cumulativeAzChange(560f);
		builder.add(new CumulativeProbabilityFilter(cffRatioThresh, new CoulombSectRatioProb(
				sumAgg, cffRatioN, favorableJumps, (float)primaryConnStrat.getMaxJumpDist(), distAzCalc)));
		builder.add(new NetRuptureCoulombFilter(fractIntsAgg, cffFractInts));
		if (primaryGrowingStrat instanceof SectCountAdaptiveRuptureGrowingStrategy)
			builder.add(((SectCountAdaptiveRuptureGrowingStrategy)primaryGrowingStrat).buildConnPointCleanupFilter(primaryConnStrat));
		configs.add(builder.build());
		names.add("UCERF3 Cumulative Filters, New CFF Ratio & 3/4 Interactions");
		prefixes.add("ucerf3_cumulatives_cffRatio_cffInteractions");
		growingStrats.add(primaryGrowingStrat);
		
		if (altGrowStrats != null) {
			System.out.println("Adding alt gorwing strategies");
			for (RuptureGrowingStrategy altPermStrat : altGrowStrats) {
				List<PlausibilityFilter> otherFilters = new ArrayList<>();
				for (PlausibilityFilter filter : filters)
					if (!(filter instanceof ConnPointCleanupFilter))
						otherFilters.add(filter);
				if (altPermStrat instanceof SectCountAdaptiveRuptureGrowingStrategy)
					otherFilters.add(((SectCountAdaptiveRuptureGrowingStrategy)altPermStrat).buildConnPointCleanupFilter(primaryConnStrat));
				configs.add(new PlausibilityConfiguration(otherFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add("Alt Growing: "+altPermStrat.getName());
				prefixes.add("alt_grow_"+fileSafe(altPermStrat.getName()));
				growingStrats.add(altPermStrat);
			}
		}
		
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
		
		if (altConnStrats != null && altConnStrats.size() > 0) {
			System.out.println("Adding alt connection strategies");
			String filtersJSON = primaryConfig.filtersToJSON(filters);
//			System.out.println("Original filters JSON:");
//			System.out.println(filtersJSON);
			for (ClusterConnectionStrategy altConnStrat : altConnStrats) {
				// filters can use the connection strategy, so need to build new ones (through serialization)
				altConnStrat.checkBuildThreaded(threads);
				// update distances
				List<PlausibilityFilter> altFilters = PlausibilityConfiguration.readFiltersJSON(filtersJSON, altConnStrat, distAzCalc);
				if ((float)altConnStrat.getMaxJumpDist() != (float)primaryConnStrat.getMaxJumpDist()) {
					System.out.println("Chekcing for Max Jump Dist updates...");
					for (PlausibilityFilter filter : altFilters) {
						if (filter instanceof CumulativeProbabilityFilter) {
							for (RuptureProbabilityCalc calc : ((CumulativeProbabilityFilter) filter).getProbCalcs())
								checkUpdateProbCalcJumpDist((float)altConnStrat.getMaxJumpDist(), calc);
						} else if (filter instanceof PathPlausibilityFilter) {
							for (NucleationClusterEvaluator nuclEval : ((PathPlausibilityFilter) filter).getEvaluators()) {
								if (nuclEval instanceof CumulativeProbPathEvaluator) {
									for (RuptureProbabilityCalc calc : ((CumulativeProbPathEvaluator) nuclEval).getCalcs())
										checkUpdateProbCalcJumpDist((float)altConnStrat.getMaxJumpDist(), calc);
								} else if (nuclEval instanceof SectCoulombPathEvaluator) {
									((SectCoulombPathEvaluator)nuclEval).setMaxJumpDist((float)altConnStrat.getMaxJumpDist());
									System.out.println("Updated "+nuclEval.getName()+" to "+(float)altConnStrat.getMaxJumpDist()+"km");
								}
							}
						}
					}
				}
				configs.add(new PlausibilityConfiguration(altFilters, primaryConfig.getMaxNumSplays(), altConnStrat, distAzCalc));

//				if (altConnStrat.getName().contains("15")) {
//					System.out.println("Mod filters JSON");
//					System.out.println(configs.get(configs.size()-1).filtersToJSON(altFilters));
//				}
				names.add("Alt Connections: "+altConnStrat.getName());
				prefixes.add("alt_conn_"+fileSafe(altConnStrat.getName()));
				growingStrats.add(primaryGrowingStrat);
			}
//			System.exit(0);
		}
		
		if (primaryConfig.getMaxNumSplays() > 0) {
			List<PlausibilityFilter> otherFilters = new ArrayList<>();
			for (PlausibilityFilter filter : filters)
				if (!(filter instanceof SplayLengthFilter) && !(filter instanceof SplayCountFilter))
					otherFilters.add(filter);
			configs.add(new PlausibilityConfiguration(otherFilters, 0, primaryConnStrat, distAzCalc));
			names.add("Sans: Splays");
			prefixes.add("sans_sect_increase_thinning");
			growingStrats.add(primaryGrowingStrat);
		}

		// see if we should load any coulomb cache
		System.out.println("Loading Coulomb caches");
		Map<String, List<AggregatedStiffnessCache>> loadedCoulombCaches = new HashMap<>();
		RupSetDiagnosticsPageGen.checkLoadCoulombCache(filters, rupSetsDir, loadedCoulombCaches);
		
		System.out.println("Will process "+names.size()+" perterbations:");
		HashSet<String> prevPrefixes = new HashSet<>();
		for (int i=0; i<configs.size(); i++) {
			String name = names.get(i);
			String prefix = prefixes.get(i);
			File outputFile = new File(outputDir, prefix+".zip");
			System.out.println("\t"+name+": "+prefix+"\texists ? "+outputFile.exists());
			
			Preconditions.checkState(!prevPrefixes.contains(prefix), "Duplicate prefix: %s", prefix);
			prevPrefixes.add(prefix);
		}
		
		// add pure UCERF3
		if (subSects.size() == 2606) {
			String prefix = "ucerf3";
			File plotDir = new File(indexDir, prefix);
			Preconditions.checkState(plotDir.exists() || plotDir.mkdir());
			
			if (replot || !new File(plotDir, "README.md").exists()) {
				FaultSystemSolution u3 = FaultSystemIO.loadSol(new File(rupSetsDir, "fm3_1_ucerf3.zip"));
				System.out.println("Plotting UCERF3");
				RupSetDiagnosticsPageGen pageGen = new RupSetDiagnosticsPageGen(rupSet, null, primaryName, u3.getRupSet(), u3, "UCERF3", plotDir);
				pageGen.setSkipPlausibility(false);
				pageGen.setIndexDir(indexDir);
				
				// now add "alt" filters to test how many UCERF3 ruptures pass our filters (even if they use different connection points
				// or growing strategies)
				ClusterConnectionStrategy altConnStrat = new UCERF3ClusterConnectionStrategy(
						subSects, distAzCalc, 5d, CoulombRates.loadUCERF3CoulombRates(FaultModels.FM3_1));
				// get the new model filters but using the UCERF3 connection strategy instead
				String filtersJSON = primaryConfig.filtersToJSON(filters);
				List<PlausibilityFilter> altFilters = PlausibilityConfiguration.readFiltersJSON(filtersJSON, altConnStrat, distAzCalc);
				for (int i=altFilters.size(); --i>=0;)
					if (altFilters.get(i) instanceof ConnPointCleanupFilter)
						// don't include this cleanup filter which is part of the adaptive growing strategy
						altFilters.remove(i);
				RupSetDiagnosticsPageGen.checkLoadCoulombCache(altFilters, rupSetsDir, loadedCoulombCaches);
				pageGen.setAltFilters(altFilters);
				pageGen.setApplyAltToComparison(true); // we want to apply these alt filters to UCERF3, which is comparison
				pageGen.generatePage();
			}
		}
		
		for (int i=0; i<configs.size(); i++) {
			System.gc();
			PlausibilityConfiguration altConfig = configs.get(i);
			String name = names.get(i);
			String prefix = prefixes.get(i);
			System.out.println("Processing alternative: "+name);
			RuptureGrowingStrategy permStrat = growingStrats.get(i);
			
			File outputFile = new File(outputDir, prefix+".zip");
			System.out.println("RupSet file: "+outputFile.getAbsolutePath());
			
			FaultSystemRupSet altRupSet = null;
			if (rebuild || !outputFile.exists()) {
				System.out.println("Building...");
				ClusterRuptureBuilder build = new ClusterRuptureBuilder(altConfig);
				
//				build.setDebugCriteria(new SectsRupDebugCriteria(false, false,
//						ClusterRuptureBuilder.loadRupString("[667:12,11,10,9,8,7,6][696:2534,2535]", false)), true);
				
				List<ClusterRupture> rups = build.build(permStrat, threads);
				
				altRupSet = ClusterRuptureBuilder.buildClusterRupSet(scale, rupSet.getFaultSectionDataList(), altConfig, rups);
				
				System.out.println("Writing to "+outputFile.getAbsolutePath());
				FaultSystemIO.writeRupSet(altRupSet, outputFile);
			}
			
			File plotDir = new File(indexDir, prefix);
			Preconditions.checkState(plotDir.exists() || plotDir.mkdir());
			
			if (replot || !new File(plotDir, "README.md").exists() || altRupSet != null) { // last check is true if we just rebuilt
				if (altRupSet == null) {
					System.out.println("Loading already built "+name+" from "+outputFile.getAbsolutePath());
					altRupSet = FaultSystemIO.loadRupSet(outputFile);
				}
				System.out.println("Plotting "+name);
				RupSetDiagnosticsPageGen pageGen = new RupSetDiagnosticsPageGen(rupSet, null, primaryName, altRupSet, null, name, plotDir);
				pageGen.setSkipPlausibility(skipPlausibility);
				pageGen.setIndexDir(indexDir);
				pageGen.generatePage();
			}
		}
		System.out.println("DONE");
	}

	private static List<PlausibilityFilter> buildPlausibleConnFilters(SectionDistanceAzimuthCalculator distAzCalc,
			DistCutoffClosestSectClusterConnectionStrategy neighborsConnStrat, AggregatedStiffnessCalculator sumAgg,
			AggregatedStiffnessCalculator fractIntsAgg, float cffFractInts, int cffRatioN, float cffRatioThresh,
			float cffRelativeProb, boolean favorableJumps, double maxJumpDist) {
		List<PlausibilityFilter> connFilters = new ArrayList<>();
		if (cffRatioThresh > 0f) {
			connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new CoulombSectRatioProb(
					sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)));
			if (cffRelativeProb > 0f)
				connFilters.add(new PathPlausibilityFilter(
						new CumulativeProbPathEvaluator(cffRatioThresh, PlausibilityResult.FAIL_HARD_STOP,
								new CoulombSectRatioProb(sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)),
						new CumulativeProbPathEvaluator(cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP,
								new RelativeCoulombProb(sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc))));
		} else if (cffRelativeProb > 0f) {
			connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new RelativeCoulombProb(
					sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc)));
		}
		if (cffFractInts > 0f)
			connFilters.add(new NetRuptureCoulombFilter(fractIntsAgg, cffFractInts));
		return connFilters;
	}
	
	private static String fileSafe(String str) {
		str = str.replace(",", "_");
		str = str.replace("(", "_");
		str = str.replace(")", "_");
		str = str.replace("≥", "GE");
		str = str.replace("≤", "LE");
		str = str.replace(">", "GT");
		str = str.replace("<", "LT");
		str = str.replace(".", "p");
		while (str.contains("__"))
			str = str.replace("__", "_");
		return str.replaceAll("\\W+", "");
	}
	
	private static void checkUpdateProbCalcJumpDist(float newJumpDist, RuptureProbabilityCalc calc) {
		if (calc instanceof RelativeCoulombProb) {
			((RelativeCoulombProb) calc).setMaxJumpDist(newJumpDist);
			System.out.println("Updated "+calc.getName()+" to "+newJumpDist+"km");
		} else if (calc instanceof CoulombSectRatioProb) {
			((CoulombSectRatioProb) calc).setMaxJumpDist(newJumpDist);
			System.out.println("Updated "+calc.getName()+" to "+newJumpDist+"km");
		}
	}

}
