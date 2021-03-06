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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayCountFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayLengthFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptivePermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptivePermutationStrategy.ConnPointCleanupFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveClusterPermuationStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRupturePerturbationBuilder {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		File markdownDir = new File("/home/kevin/markdown/rupture-sets");
		
//		String primaryName = "Plausible 10km, Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.02], 5% Fract Increase";
//		File primaryFile = new File(rupSetsDir, "fm3_1_plausible10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
		String primaryName = "Plausible 10km (MultiEnds), Slip P>0.05 (@Incr), CFF 3/4 Ints >0, CFF Comb Paths: [Sect R>0.5, P>0.02], 5% Fract Increase";
		File primaryFile = new File(rupSetsDir, "fm3_1_plausibleMulti10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
		ClusterPermutationStrategy permutationStrategy = new SectCountAdaptivePermutationStrategy(0.05f, true);
		ScalingRelationships scale = ScalingRelationships.SHAW_2009_MOD;
		boolean rebuild = true;
		boolean replot = true;
		boolean skipPlausibility = true; // in plots
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(primaryFile);
		PlausibilityConfiguration primaryConfig = rupSet.getPlausibilityConfiguration();
		Preconditions.checkNotNull(primaryConfig);
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		// alternate connection strategies
		SectionDistanceAzimuthCalculator distAzCalc = primaryConfig.getDistAzCalc();
		DistCutoffClosestSectClusterConnectionStrategy neighborsConnStrat = new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d);
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		AggregatedStiffnessCalculator fractIntsAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		// MAKE SURE THESE ARE UP TO DATE ********************************************
		float cffFractInts = 0.75f;
		int cffRatioN = 2;
		float cffRatioThresh = 0.5f;
		float cffRelativeProb = 0.02f;
		boolean favorableJumps = true;
		double[] altJumpDists = { 5d, 15d };
		
		double origMaxJumpDist = primaryConfig.getConnectionStrategy().getMaxJumpDist();
		List<ClusterConnectionStrategy> altConnStrats = new ArrayList<>();
		altConnStrats.add(new DistCutoffClosestSectClusterConnectionStrategy(rupSet.getFaultSectionDataList(),
				primaryConfig.getDistAzCalc(), origMaxJumpDist));
		altConnStrats.add(new PlausibleClusterConnectionStrategy(subSects, distAzCalc, origMaxJumpDist,
				new FallbackJumpSelector(true, new PassesMinimizeFailedSelector(), new BestScalarSelector(2d)),
				buildPlausibleConnFilters(distAzCalc, neighborsConnStrat, sumAgg,
						fractIntsAgg, cffFractInts, cffRatioN, cffRatioThresh, cffRelativeProb, favorableJumps,
						origMaxJumpDist)));
		for (double maxJumpDist : altJumpDists) {
			List<PlausibilityFilter> connFilters = buildPlausibleConnFilters(distAzCalc, neighborsConnStrat, sumAgg,
					fractIntsAgg, cffFractInts, cffRatioN, cffRatioThresh, cffRelativeProb, favorableJumps,
					maxJumpDist);
			altConnStrats.add(new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
					PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, connFilters));
		}
		
		// alternate permuation strategies
		List<ClusterPermutationStrategy> altPermStrats = new ArrayList<>();
		altPermStrats.add(new ExhaustiveClusterPermuationStrategy());
		altPermStrats.add(new SectCountAdaptivePermutationStrategy(0.1f, true));
		
		System.out.println("Primnary has "+rupSet.getNumRuptures()+" ruptures");
		List<PlausibilityFilter> filters = primaryConfig.getFilters();
		ClusterConnectionStrategy primaryConnStrat = primaryConfig.getConnectionStrategy();
		
		String primaryPrefix = primaryFile.getName().replace(".zip", "");
		File indexDir = new File(markdownDir, primaryPrefix);
		Preconditions.checkState(indexDir.exists() || indexDir.mkdir());
		File outputDir = new File(rupSetsDir, primaryPrefix+"_comp");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		List<PlausibilityConfiguration> configs = new ArrayList<>();
		List<ClusterPermutationStrategy> permStrats = new ArrayList<>();
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
					permStrats.add(permutationStrategy);
				}
				// now add one version where they're separate paths
				List<PlausibilityFilter> myFilters = new ArrayList<>(otherFilters);
				for (NucleationClusterEvaluator eval : evals)
					myFilters.add(new PathPlausibilityFilter(pathFilter.getFractPassThreshold(), pathFilter.isLogicalOr(), eval));
				configs.add(new PlausibilityConfiguration(myFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add(evals.length+" Path Filters Separated");
				prefixes.add("separate_paths");
				permStrats.add(permutationStrategy);
			} else {
				configs.add(new PlausibilityConfiguration(otherFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add("Sans Filter: "+filter.getName());
				prefixes.add("sans_"+fileSafe(filter.getShortName()));
				permStrats.add(permutationStrategy);
			}
		}
		
		// add UCERF3 filters
		configs.add(PlausibilityConfiguration.builder(primaryConnStrat, distAzCalc).minSectsPerParent(2, true, true).u3Azimuth()
				.cumulativeRakeChange(180f).cumulativeAzChange(560f).build());
		names.add("UCERF3 Azimuth & Cumulative Filters");
		prefixes.add("ucerf_filters_sans_coulomb");
		permStrats.add(permutationStrategy);
		
		if (altPermStrats != null) {
			for (ClusterPermutationStrategy altPermStrat : altPermStrats) {
				List<PlausibilityFilter> otherFilters = new ArrayList<>();
				for (PlausibilityFilter filter : filters)
					if (!(filter instanceof ConnPointCleanupFilter))
						otherFilters.add(filter);
				if (altPermStrat instanceof SectCountAdaptivePermutationStrategy)
					otherFilters.add(((SectCountAdaptivePermutationStrategy)altPermStrat).buildConnPointCleanupFilter(primaryConnStrat));
				configs.add(new PlausibilityConfiguration(otherFilters, primaryConfig.getMaxNumSplays(), primaryConnStrat, distAzCalc));
				names.add("Alt Permutations: "+altPermStrat.getName());
				prefixes.add("alt_perm_"+fileSafe(altPermStrat.getName()));
				permStrats.add(altPermStrat);
			}
		}
		
		if (altConnStrats != null && altConnStrats.size() > 0) {
			for (ClusterConnectionStrategy altConnStrat : altConnStrats) {
				configs.add(new PlausibilityConfiguration(filters, primaryConfig.getMaxNumSplays(), altConnStrat, distAzCalc));
				names.add("Alt Connections: "+altConnStrat.getName());
				prefixes.add("alt_conn_"+fileSafe(altConnStrat.getName()));
				permStrats.add(permutationStrategy);
			}
		}
		
		if (primaryConfig.getMaxNumSplays() > 0) {
			List<PlausibilityFilter> otherFilters = new ArrayList<>();
			for (PlausibilityFilter filter : filters)
				if (!(filter instanceof SplayLengthFilter) && !(filter instanceof SplayCountFilter))
					otherFilters.add(filter);
			configs.add(new PlausibilityConfiguration(otherFilters, 0, primaryConnStrat, distAzCalc));
			names.add("Sans: Splays");
			prefixes.add("sans_sect_increase_thinning");
			permStrats.add(permutationStrategy);
		}

		// see if we should load any coulomb cache
		Map<String, List<AggregatedStiffnessCache>> loadedCoulombCaches = new HashMap<>();
		RupSetDiagnosticsPageGen.checkLoadCoulombCache(filters, rupSetsDir, loadedCoulombCaches);
		
		System.out.println("Will process "+names.size()+" perterbations:");
		HashSet<String> prevPrefixes = new HashSet<>();
		for (int i=0; i<configs.size(); i++) {
			String name = names.get(i);
			String prefix = prefixes.get(i);
			System.out.println("\t"+name+": "+prefix);
			
			Preconditions.checkState(!prevPrefixes.contains(prefix), "Duplicate prefix: %s", prefix);
			prevPrefixes.add(prefix);
		}
		
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
		
		for (int i=0; i<configs.size(); i++) {
			System.gc();
			PlausibilityConfiguration altConfig = configs.get(i);
			String name = names.get(i);
			String prefix = prefixes.get(i);
			System.out.println("Processing alternative: "+name);
			ClusterPermutationStrategy permStrat = permStrats.get(i);
			
			File outputFile = new File(outputDir, prefix+".zip");
			System.out.println("RupSet file: "+outputFile.getAbsolutePath());
			
			FaultSystemRupSet altRupSet = null;
			if (rebuild || !outputFile.exists()) {
				System.out.println("Building...");
				ClusterRuptureBuilder build = new ClusterRuptureBuilder(altConfig);
				
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

}
