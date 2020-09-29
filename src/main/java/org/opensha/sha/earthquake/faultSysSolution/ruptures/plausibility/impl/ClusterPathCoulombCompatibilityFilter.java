package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.HashSet;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each possible path through the given rupture. It tries each
 * cluster as the nucleation point, and builds outwards to each rupture endpoint. Ruptures pass if at least
 * one nucleation point is viable.
 * 
 * @author kevin
 *
 */
public class ClusterPathCoulombCompatibilityFilter implements ScalarValuePlausibiltyFilter<Float> {
	
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
		float maxVal = Float.NEGATIVE_INFINITY;
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			float val = testNucleationPoint(navigator, nucleationCluster, !verbose);
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster
						+", result: "+(val >= threshold));
			maxVal = Float.max(maxVal, val);
			if (!verbose && maxVal >= threshold)
				// passes if *any* nucleation point works
				return PlausibilityResult.PASS;
		}
		if (maxVal >= threshold)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		return apply(rupture.take(newJump), verbose);
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		float maxVal = Float.NEGATIVE_INFINITY;
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			float val = testNucleationPoint(navigator, nucleationCluster, false);
			maxVal = Float.max(maxVal, val);
		}
		return maxVal;
	}

	@Override
	public Float getValue(ClusterRupture rupture, Jump newJump) {
		return getValue(rupture.take(newJump));
	}
	
	private float testNucleationPoint(RuptureTreeNavigator navigator,
			FaultSubsectionCluster nucleationCluster, boolean shortCircuit) {
		HashSet<FaultSubsectionCluster> curClusters = new HashSet<>();
		curClusters.add(nucleationCluster);
		FaultSubsectionCluster predecessor = navigator.getPredecessor(nucleationCluster);
		Float minVal = Float.POSITIVE_INFINITY;
		if (predecessor != null) {
			minVal = Float.min(minVal, testStrand(navigator, curClusters, predecessor, shortCircuit));
			if (shortCircuit && minVal < threshold)
				return minVal;
		}
		
		for (FaultSubsectionCluster descendant : navigator.getDescendants(nucleationCluster)) {
			minVal = Float.min(minVal, testStrand(navigator, curClusters, descendant, shortCircuit));
			if (shortCircuit && minVal < threshold)
				return minVal;
		}
		
		return minVal;
	}
	
	private float testStrand(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
			FaultSubsectionCluster addition, boolean shortCircuit) {
		float minVal = Float.POSITIVE_INFINITY;
		if (!strandClusters.isEmpty()) {
			StiffnessResult[] stiffness = stiffnessCalc.calcAggClustersToClusterStiffness(
					strandClusters, addition);
			double val = stiffnessCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// this additon passed, continue downstream
		HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
		newStrandClusters.add(addition);
		
		// check predecessor of this strand
		FaultSubsectionCluster predecessor = navigator.getPredecessor(addition);
		if (predecessor != null && !strandClusters.contains(predecessor)) {
			// go down that path
			
			float val = testStrand(navigator, newStrandClusters, predecessor, shortCircuit);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// check descendants of this strand
		for (FaultSubsectionCluster descendant : navigator.getDescendants(addition)) {
			if (strandClusters.contains(descendant))
				continue;
			// go down that path

			float val = testStrand(navigator, newStrandClusters, descendant, shortCircuit);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// if we made it here, this either the end of the line or all downstream strand extensions pass
		return minVal;
	}

	@Override
	public String getShortName() {
		return "ClusterPathCoulomb≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Cluster Path Coulomb  ≥ "+(float)threshold;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast((float)threshold);
	}

}
