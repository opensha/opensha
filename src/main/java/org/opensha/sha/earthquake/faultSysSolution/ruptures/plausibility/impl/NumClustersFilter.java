package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class NumClustersFilter implements PlausibilityFilter {
	
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
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		if (rupture.getTotalNumClusters() > maxNumClusters-1)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

}
