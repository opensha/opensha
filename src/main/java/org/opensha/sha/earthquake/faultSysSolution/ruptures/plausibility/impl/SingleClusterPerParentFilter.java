package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter dictates that a single parent fault section can only exist once within
 * a rupture. Otherwise, a rupture could start on sect A, jump to sect B, then jump back
 * to another part of sect A. This was not used in UCERF3.
 * 
 * @author kevin
 *
 */
public class SingleClusterPerParentFilter implements PlausibilityFilter {

	@Override
	public String getShortName() {
		return "1ClusterPerParent";
	}

	@Override
	public String getName() {
		return "Single Cluster Per Parent";
	}
	
	private void count(ClusterRupture rup, HashSet<Integer> parents, List<FaultSubsectionCluster> clusters) {
		for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
			parents.add(cluster.parentSectionID);
			clusters.add(cluster);
		}
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		HashSet<Integer> parents = new HashSet<>();
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		count(rupture, parents, clusters);
		if (parents.size() != clusters.size()) {
			if (verbose) System.out.println(getShortName()+": have "+parents.size()
				+" parents but "+clusters.size()+" clusters");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}

}

