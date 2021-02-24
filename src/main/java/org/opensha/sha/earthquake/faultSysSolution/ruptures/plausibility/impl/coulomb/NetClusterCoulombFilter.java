package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

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
	
	private AggregatedStiffnessCalculator aggCalc;
	private float threshold;

	public NetClusterCoulombFilter(AggregatedStiffnessCalculator aggCalc, float threshold) {
		super();
		this.aggCalc = aggCalc;
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
		List<FaultSection> allSects = new ArrayList<>();
		for (FaultSubsectionCluster cluster : clusters)
			allSects.addAll(cluster.subSects);
		float minVal = Float.POSITIVE_INFINITY;
		for (FaultSubsectionCluster cluster : clusters) {
			// get sublist of source sects: all sects not on this cluster
			List<FaultSection> sources = allSects.stream().filter(s -> !cluster.contains(s)).collect(Collectors.toList());
			double val = aggCalc.calc(sources, cluster.subSects);
			minVal = Float.min(minVal, (float)val);
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
	public Range<Float> getAcceptableRange() {
		return Range.atLeast(threshold);
	}

	@Override
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

}
