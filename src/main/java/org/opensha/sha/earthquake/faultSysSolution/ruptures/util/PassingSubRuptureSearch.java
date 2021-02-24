package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MultiDirectionalPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveClusterPermuationStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class PassingSubRuptureSearch {
	
	private ClusterRupture rupture;
	private List<PlausibilityFilter> filters;
	private ClusterPermutationStrategy permStrat;
	private RuptureTreeNavigator nav;

	public PassingSubRuptureSearch(ClusterRupture rupture, List<PlausibilityFilter> filters) {
		this.rupture = rupture;
		this.filters = new ArrayList<>();
		for (PlausibilityFilter filter : filters) {
			if (filter instanceof MultiDirectionalPlausibilityFilter) {
				// don't need multi directional here (we're trying all directions anyway)
				this.filters.add(((MultiDirectionalPlausibilityFilter)filter).getFilter());
			} else {
				this.filters.add(filter);
			}
		}
		this.nav = rupture.getTreeNavigator();
		this.permStrat = new ExhaustiveClusterPermuationStrategy();
	}
	
	/**
	 * Finds the largest passing subset of this rupture that starts from a full nucleation cluster
	 * 
	 * @return
	 */
	public ClusterRupture getLargestPassinSubset() {
		ClusterRupture largestPassing = null;
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			ClusterRupture rup = new FilterDataClusterRupture(nucleationCluster);
			if (!passes(rup)) {
				rup = null;
				int largestPassingPerm = 0;
				for (FaultSection sect : nucleationCluster.subSects) {
					for (FaultSubsectionCluster perm : permStrat.getPermutations(nucleationCluster, sect)) {
						if (perm.subSects.size() > largestPassingPerm) {
							ClusterRupture test = new FilterDataClusterRupture(perm);
							if (passes(test)) {
								rup = test;
								largestPassingPerm = perm.subSects.size();
							}
						}
					}
				}
			}
			if (rup != null) {
				ClusterRupture full = grow(rup);
				if (largestPassing == null || full.getTotalNumSects() > largestPassing.getTotalNumSects())
					largestPassing = full;
			}
		}
		return largestPassing;
	}
	
	public ClusterRupture grow(ClusterRupture rup) {
		ClusterRupture largest = rup;
		for (FaultSubsectionCluster fromCluster : rup.getClustersIterable()) {
			for (FaultSection sect : fromCluster.subSects) {
				for (FaultSection dest : getDestinationsNotInRupture(rup, sect)) {
					Jump jump = nav.getJump(sect, dest);
					jump = new Jump(sect, fromCluster, dest, jump.toCluster, jump.distance);
					// try the full destination cluster
					ClusterRupture taken = rup.take(jump);
					if (!passes(taken)) {
						// see if a subset passes
						taken = null;
						int maxPass = 0;
						for (FaultSubsectionCluster perm : permStrat.getPermutations(jump.toCluster, dest)) {
							if (perm.subSects.size() < maxPass)
								continue;
							Jump j2 = new Jump(jump.fromSection, jump.fromCluster, dest, perm, jump.distance);
							ClusterRupture permTest = rup.take(j2);
							if (passes(permTest)) {
								taken = permTest;
								maxPass = perm.subSects.size();
							}
						}
					}
					if (taken != null) {
						// passes! grow it as large as we can
						taken = grow(taken);
						if (taken.getTotalNumSects() > largest.getTotalNumSects())
							largest = taken;
					}
				}
			}
		}
		return largest;
	}
	
	private List<FaultSection> getDestinationsNotInRupture(ClusterRupture rup, FaultSection sect) {
		List<FaultSection> ret = new ArrayList<>();
		FaultSection predecessor = nav.getPredecessor(sect);
		if (predecessor != null && !rup.contains(predecessor) & predecessor.getParentSectionId() != sect.getParentSectionId())
			ret.add(predecessor);
		for (FaultSection descendant : nav.getDescendants(sect))
			if (!rup.contains(descendant) & descendant.getParentSectionId() != sect.getParentSectionId())
				ret.add(descendant);
		return ret;
	}
	
	private boolean passes(ClusterRupture testRup) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (PlausibilityFilter filter : filters) {
			result = result.logicalAnd(filter.apply(testRup, false));
			if (!result.isPass())
				return false;
		}
		return result.isPass();
	}

}
