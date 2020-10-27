package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the net Coulomb compatibility of each cluster within a rupture. For each participating
 * cluster, it computes Coulomb with all other clusters as a source that cluster as the receiver. It then
 * aensures that each cluster's net Coulomb value is at or above the given threshold
 * 
 * @author kevin
 *
 */
public class NetClusterCoulombFilter implements ScalarCoulombPlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public NetClusterCoulombFilter(SubSectStiffnessCalculator stiffnessCalc, StiffnessAggregationMethod aggMethod,
			float threshold) {
		super();
		this.stiffnessCalc = stiffnessCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumClusters() == 1)
			return PlausibilityResult.PASS;
		float val = getValue(rupture);
		PlausibilityResult result = val < threshold ?
				PlausibilityResult.FAIL_HARD_STOP : PlausibilityResult.PASS;
		if (verbose)
			System.out.println(getShortName()+": val="+val+", result="+result);
		return result;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		float val = getValue(rupture, newJump);
		PlausibilityResult result = val < threshold ?
				PlausibilityResult.FAIL_HARD_STOP : PlausibilityResult.PASS;
		if (verbose)
			System.out.println(getShortName()+": val="+val+", result="+result);
		return result;
	}

	@Override
	public String getShortName() {
		if (threshold == 0f)
			return "NetClusterCFF≥0";
		return "NetClusterCFF≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Net Cluster Coulomb  ≥ "+(float)threshold;
	}
	
	private float getMinValue(List<FaultSubsectionCluster> clusters) {
		float minVal = Float.POSITIVE_INFINITY;
		for (FaultSubsectionCluster cluster : clusters) {
			StiffnessResult val = stiffnessCalc.calcAggClustersToClusterStiffness(
					StiffnessType.CFF, clusters, cluster);
			minVal = Float.min(minVal, (float)val.getValue(aggMethod));
		}
		return minVal;
	}
	
	private List<FaultSubsectionCluster> getClusterList(ClusterRupture rupture) {
		List<FaultSubsectionCluster> clusters = new ArrayList<>(rupture.getTotalNumClusters());
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			clusters.add(cluster);
		return clusters;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumClusters() == 1)
			return null;
		return getMinValue(getClusterList(rupture));
	}

	@Override
	public Float getValue(ClusterRupture rupture, Jump newJump) {
		List<FaultSubsectionCluster> clusterList = getClusterList(rupture);
		clusterList.add(newJump.toCluster);
		return getMinValue(clusterList);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast(threshold);
	}

	@Override
	public SubSectStiffnessCalculator getStiffnessCalc() {
		return stiffnessCalc;
	}

}
