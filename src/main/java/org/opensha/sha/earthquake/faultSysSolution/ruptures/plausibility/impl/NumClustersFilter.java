package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Maximum number of allowed clusters in a rupture. This will mostly be useful for creating
 * rupture sets to test connection points, without worrying about big ruptures
 * 
 * @author kevin
 *
 */
public class NumClustersFilter implements ScalarValuePlausibiltyFilter<Integer> {
	
	private int maxNumClusters;

	public NumClustersFilter(int maxNumClusters) {
		this.maxNumClusters = maxNumClusters;
	}

	@Override
	public String getShortName() {
		return "Max"+maxNumClusters+"Clusters";
	}

	@Override
	public String getName() {
		return "Max "+maxNumClusters+" Clusters";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumClusters() > maxNumClusters)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

	@Override
	public Integer getValue(ClusterRupture rupture) {
		return rupture.getTotalNumClusters();
	}

	@Override
	public Range<Integer> getAcceptableRange() {
		return Range.atMost(maxNumClusters);
	}
	
	@Override
	public String getScalarName() {
		return "Number of Clusters";
	}

	@Override
	public String getScalarUnits() {
		return null;
	}

}
