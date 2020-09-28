package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.HashSet;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each possible path through the given rupture. It tries each
 * cluster as the nucleation point, and builds outwards to each rupture endpoint. Ruptures pass if at least
 * one nucleation point is viable.
 * 
 * @author kevin
 *
 */
public class ClusterPathCoulombCompatibilityFilter implements PlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public ClusterPathCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this.stiffnessCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps()  == 0)
			return PlausibilityResult.PASS;
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			boolean valid = testNucleationPoint(navigator, nucleationCluster);
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster+", result: "+valid);
			if (valid)
				// passes if *any* nucleation point works
				return PlausibilityResult.PASS;
		}
		return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		return apply(rupture.take(newJump), verbose);
	}
	
	private boolean testNucleationPoint(RuptureTreeNavigator navigator,
			FaultSubsectionCluster nucleationCluster) {
		FaultSubsectionCluster predecessor = navigator.getPredecessor(nucleationCluster);
		if (predecessor != null)
			if (!testStrand(navigator, new HashSet<>(), predecessor))
				return false;
		
		for (FaultSubsectionCluster descendant : navigator.getDescendants(nucleationCluster)) {
			if (!testStrand(navigator, new HashSet<>(), descendant))
				return false;
		}
		
		// passed all
		return true;
	}
	
	private boolean testStrand(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
			FaultSubsectionCluster addition) {
		if (!strandClusters.isEmpty()) {
			StiffnessResult[] stiffness = stiffnessCalc.calcAggClustersToClusterStiffness(
					strandClusters, addition);
			double val = stiffnessCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
			if ((float)val < threshold)
				return false;
		}
		
		// this additon passed, continue downstream
		HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
		newStrandClusters.add(addition);
		
		// check predecessor of this strand
		FaultSubsectionCluster predecessor = navigator.getPredecessor(addition);
		if (predecessor != null && !strandClusters.contains(predecessor)) {
			// go down that path
			
			if (!testStrand(navigator, newStrandClusters, predecessor))
				return false;
		}
		
		// check descendants of this strand
		for (FaultSubsectionCluster descendant : navigator.getDescendants(addition)) {
			if (strandClusters.contains(descendant))
				continue;
			// go down that path

			if (!testStrand(navigator, newStrandClusters, descendant))
				return false;
		}
		
		// if we made it here, this either the end of the line or all downstream strand extensions pass
		return true;
	}

	@Override
	public String getShortName() {
		return "ClusterPathCoulomb≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Cluster Path Coulomb  ≥ "+(float)threshold;
	}

}
