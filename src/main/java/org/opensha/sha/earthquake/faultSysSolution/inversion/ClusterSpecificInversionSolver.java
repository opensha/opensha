package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ConnectivityClusters;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;

import com.google.common.base.Preconditions;

public class ClusterSpecificInversionSolver extends InversionSolver.Default {
	
	protected BinaryRuptureProbabilityCalc getRuptureExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		return null;
	}
	
	protected boolean shouldInvert(ConnectivityCluster cluster) {
		return true;
	}

	@Override
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfigurationFactory factory,
			LogicTreeBranch<?> branch, int threads, CommandLine cmd) throws IOException {
		// cluster-specific inversions
		
		// we might have an exclusion model that limits us to only certain ruptures, which can affect the clusters that
		// we build
		BinaryRuptureProbabilityCalc rupExclusionModel = getRuptureExclusionModel(rupSet, branch);
		
		List<ConnectivityCluster> clusters;
		if (rupExclusionModel != null) {
			// build only considering those ruptures
			clusters = ConnectivityCluster.build(rupSet, rupExclusionModel);
			rupSet.addModule(new ConnectivityClusters(rupSet, clusters));
		} else if (rupSet.hasModule(ConnectivityClusters.class)) {
			clusters = rupSet.requireModule(ConnectivityClusters.class).get();
		} else {
			ConnectivityClusters clusterModule = ConnectivityClusters.build(rupSet);
			rupSet.addModule(clusterModule);
			clusters = clusterModule.get();
		}
		Preconditions.checkState(!clusters.isEmpty(), "No clusters found?");
		// sort by number of ruptures, decreasing
		clusters = new ArrayList<>(clusters);
		Collections.sort(clusters, ConnectivityCluster.rupCountComparator);
		Collections.reverse(clusters);

		if (clusters.size() > 1) {
			System.out.println("Will invert for "+clusters.size()+" separate connectivity clusters");
			List<FaultSystemSolution> solutions = new ArrayList<>(clusters.size());

//			File tmpDir = new File("/tmp/inv_debug");
//			Preconditions.checkState(tmpDir.exists() || tmpDir.mkdir());
			int numInverted = 0;
			for (ConnectivityCluster cluster : clusters) {
				String clusterName = cluster.toString();
				if (cluster.getParentSectIDs().size() == 1) {
					int sectIndex = cluster.getSectIDs().iterator().next();
					String parentName = rupSet.getFaultSectionData(sectIndex).getParentSectionName();
					clusterName += ", "+parentName;
				}
				if (!shouldInvert(cluster)) {
					solutions.add(null);
					System.out.println("Skipping inversion for cluster: "+clusterName);
					continue;
				}
				System.out.println("Handling cluster: "+clusterName);
				System.out.println("Building subset rupture set for "+clusterName);
				FaultSystemRupSet clusterRupSet = rupSet.getForSectionSubSet(cluster.getSectIDs(), rupExclusionModel);
				System.out.println("Building subset inversion configuration for "+clusterName);
				InversionConfiguration config = factory.buildInversionConfig(clusterRupSet, branch, threads);
				if (config == null) {
					// assume that we're not supposed to invert for this cluster, skip
					solutions.add(null);
					continue;
				}
				if (cmd != null)
					// apply any command line overrides
					config = InversionConfiguration.builder(config).forCommandLine(cmd).build();
				solutions.add(run(clusterRupSet, config));
				numInverted++;

//				String name = "sol_"+(solutions.size()-1)+"_"+cluster.getNumSections()+"sects_"+cluster.getNumRuptures()+"rups";
//				if (cluster.getParentSectIDs().size() == 1)
//					name += "_"+clusterRupSet.getFaultSectionData(0).getParentSectionName().replaceAll("\\W+", "_");
//				File tmpFile = new File(tmpDir, name+".zip");
//				solutions.get(solutions.size()-1).write(tmpFile);
			}

			System.out.println("Finished "+numInverted+" cluster-specific inversions, combining");
			if (numInverted == 0)
				return null;
			double[] rates = new double[rupSet.getNumRuptures()];

			double[] waterLevelRates = null;
			double[] initialRates = null;
			List<InversionMisfits> solMisfits = new ArrayList<>();
			Map<ConnectivityCluster, InversionMisfitStats> clusterMisfitsMap = new HashMap<>();
			InversionMisfitProgress largestProgress = null;

			for (int i=0; i<solutions.size(); i++) {
				ConnectivityCluster cluster = clusters.get(i);
				FaultSystemSolution clusterSol = solutions.get(i);
				// merge each one back in
				if (clusterSol == null)
					continue;
				FaultSystemRupSet clusterRupSet = clusterSol.getRupSet();
				RuptureSubSetMappings mappings = clusterRupSet.requireModule(RuptureSubSetMappings.class);
				WaterLevelRates subsetWL = clusterSol.getModule(WaterLevelRates.class);
				if (subsetWL != null && waterLevelRates == null)
					waterLevelRates = new double[rates.length];
				InitialSolution subsetInitial = clusterSol.getModule(InitialSolution.class);
				if (subsetInitial != null && initialRates == null)
					initialRates = new double[rates.length];
				for (int subsetID=0; subsetID<clusterRupSet.getNumRuptures(); subsetID++) {
					int fullID = mappings.getOrigRupID(subsetID);
					Preconditions.checkState(rates[fullID] == 0d,
							"Rupture %s (%s in the subset solution) was already non-zero (%s), used in multiple clusters?",
							fullID, subsetID, rates[fullID]);
					rates[fullID] = clusterSol.getRateForRup(subsetID);
					if (subsetWL != null)
						waterLevelRates[fullID] = subsetWL.get(subsetID);
					if (subsetInitial != null)
						initialRates[fullID] = subsetInitial.get(subsetID);
				}
				InversionMisfits misfits = clusterSol.requireModule(InversionMisfits.class);
				solMisfits.add(misfits);
				clusterMisfitsMap.put(cluster, misfits.getMisfitStats());
				if (largestProgress == null)
					largestProgress = clusterSol.getModule(InversionMisfitProgress.class);
			}
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
			if (waterLevelRates != null)
				sol.addModule(new WaterLevelRates(waterLevelRates));
			if (initialRates != null)
				sol.addModule(new InitialSolution(initialRates));
			InversionMisfits misfits = InversionMisfits.appendSeparate(solMisfits);
			sol.addModule(misfits);
			sol.addModule(misfits.getMisfitStats());
			sol.addModule(new ConnectivityClusters.ConnectivityClusterSolutionMisfits(
					sol, clusterMisfitsMap, largestProgress));

			// attach any relevant modules before writing out
			SolutionProcessor processor = factory.getSolutionLogicTreeProcessor();

			if (processor != null)
				processor.processSolution(sol, branch);

			return sol;
		} else {
			ConnectivityCluster cluster = clusters.get(0);
			if (!shouldInvert(cluster))
				return null;
			System.out.println("Only 1 connectivity cluster, will run regular inversion");
			InversionConfiguration config = factory.buildInversionConfig(rupSet, branch, threads);
			if (branch != rupSet.getModule(LogicTreeBranch.class))
				rupSet.addModule(branch);
			if (cmd != null)
				// apply any command line overrides
				config = InversionConfiguration.builder(config).forCommandLine(cmd).build();
			
			FaultSystemSolution sol = run(rupSet, config);
			
			// attach any relevant modules before writing out
			SolutionProcessor processor = factory.getSolutionLogicTreeProcessor();
			
			if (processor != null)
				processor.processSolution(sol, branch);
			
			return sol;
		}
	}

}
