package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each added cluster as a rupture is built. It ensures
 * that, conditioned on unit slip of the existing rupture, each new cluster has a net Coulomb compatibility
 * at or above the given threshold. For example, to ensure that each added cluster is net postitive, set
 * the threshold to 0.
 * 
 * @author kevin
 *
 */
public class ClusterCoulombCompatibilityFilter implements ScalarCoulombPlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public ClusterCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this.stiffnessCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double worstVal = doTest(new ArrayList<>(), rupture.clusters[0], rupture.getTreeNavigator(),
				verbose, !verbose);
		PlausibilityResult result =
				(float)worstVal >= threshold ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": worst val="+worstVal+"\tresult="+result.name());
		return result;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
//		StiffnessResult[] stiffness = stiffnessCalc.calcAggRupToClusterStiffness(rupture, newJump.toCluster);
//		double val = stiffnessCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			clusters.add(cluster);
		double val = doTest(clusters, newJump.toCluster, null, verbose, !verbose);
		PlausibilityResult result =
				(float)val >= threshold ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": val="+val+"\tresult="+result.name());
		return result;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumJumps()  == 0)
			return null;
		return (float)doTest(new ArrayList<>(), rupture.clusters[0], rupture.getTreeNavigator(),
				false, false);
	}

	@Override
	public Float getValue(ClusterRupture rupture, Jump newJump) {
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			clusters.add(cluster);
		return (float)doTest(clusters, newJump.toCluster, null, false, false);
	}
	
	private double doTest(List<FaultSubsectionCluster> curClusters, FaultSubsectionCluster nextCluster,
			RuptureTreeNavigator navigator, boolean verbose, boolean shortCircuit) {
		double val = Double.POSITIVE_INFINITY;
		if (!curClusters.isEmpty()) {
			// check rupture so far
			StiffnessResult stiffness = stiffnessCalc.calcAggClustersToClusterStiffness(
					StiffnessType.CFF, curClusters, nextCluster);
			val = stiffness.getValue(aggMethod);
			if (verbose)
				System.out.println(getShortName()+": "+curClusters.size()+" clusters to "
						+nextCluster+", val="+val);
			else if ((float)val < threshold)
				return val;
		}
		
		if (navigator != null) {
			for (FaultSubsectionCluster descendant : navigator.getDescendants(nextCluster)) {
				List<FaultSubsectionCluster> newClusters = new ArrayList<>(curClusters);
				newClusters.add(nextCluster);
				val = Math.min(val, doTest(newClusters, descendant, navigator, verbose, shortCircuit));
				if (!verbose && (float)val < threshold)
					break;
			}
		}
		
		return val;
	}

	@Override
	public String getShortName() {
		if (threshold == 0f)
			return "JumpClusterCFF≥0";
		return "JumpClusterCFF≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Jump Cluster Coulomb  ≥ "+(float)threshold;
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return true;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast((float)threshold);
	}

	@Override
	public SubSectStiffnessCalculator getStiffnessCalc() {
		return stiffnessCalc;
	}

}
