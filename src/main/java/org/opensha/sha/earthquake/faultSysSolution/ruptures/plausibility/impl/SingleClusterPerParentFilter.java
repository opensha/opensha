package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.HashSet;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SingleClusterPerParentFilter implements PlausibilityFilter {

	@Override
	public String getShortName() {
		return "GapWithinSect";
	}

	@Override
	public String getName() {
		return "Gap Within Fault Section";
	}
	
	private void count(ClusterRupture rup, HashSet<Integer> parents, HashSet<FaultSubsectionCluster> clusters) {
		for (FaultSubsectionCluster cluster : rup.clusters) {
			parents.add(cluster.parentSectionID);
			clusters.add(cluster);
		}
		for (ClusterRupture splay : rup.splays.values())
			count(splay, parents, clusters);
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		HashSet<Integer> parents = new HashSet<>();
		HashSet<FaultSubsectionCluster> clusters = new HashSet<>();
		count(rupture, parents, clusters);
		if (parents.size() != clusters.size()) {
			if (verbose) System.out.println(getShortName()+": have "+parents.size()
				+" parents but "+clusters.size()+" clusters");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		HashSet<Integer> parents = new HashSet<>();
		HashSet<FaultSubsectionCluster> clusters = new HashSet<>();
		count(rupture, parents, clusters);
		parents.add(newJump.toCluster.parentSectionID);
		clusters.add(newJump.toCluster);
		if (parents.size() != clusters.size()) {
			if (verbose) System.out.println(getShortName()+": have "+parents.size()
				+" parents but "+clusters.size()+" clusters");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}

}

