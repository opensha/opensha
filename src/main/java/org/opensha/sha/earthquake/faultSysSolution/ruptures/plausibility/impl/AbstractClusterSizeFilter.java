package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;

abstract class AbstractClusterSizeFilter implements PlausibilityFilter {
	
	boolean allowIfNoDirect;
	boolean allowChained;
	ClusterConnectionStrategy connStrategy;
	
	AbstractClusterSizeFilter(boolean allowIfNoDirect, boolean allowChained,
			ClusterConnectionStrategy connStrategy) {
		this.allowIfNoDirect = allowIfNoDirect;
		this.allowChained = allowChained;
		this.connStrategy = connStrategy;
	}
	
	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = apply(rupture.clusters, verbose);
		if (result.canContinue()) {
			for (Jump jump : rupture.splays.keySet()) {
				ClusterRupture splay = rupture.splays.get(jump);
				FaultSubsectionCluster[] strand = splay.clusters;
				if (allowIfNoDirect && !isClusterSufficient(strand[0]) && strand.length > 1) {
					// add the parent to this splay
					List<FaultSection> beforeSects = new ArrayList<>();
					for (FaultSection sect : jump.fromCluster.subSects) {
						beforeSects.add(sect);
						if (sect.equals(jump.fromSection))
							break;
					}
					FaultSubsectionCluster[] newStrand = new FaultSubsectionCluster[strand.length+1];
					newStrand[0] = new FaultSubsectionCluster(beforeSects);
					System.arraycopy(strand, 0, newStrand, 1, strand.length);
					strand = newStrand;
				}
				result = result.logicalAnd(apply(strand, verbose));
			}
		}
		return result;
	}
	
	abstract boolean isClusterSufficient(FaultSubsectionCluster cluster);
	
	abstract String getQuantityStr(FaultSubsectionCluster cluster);
	
	private boolean isDirectPossible(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return connStrategy.areParentSectsConnected(from.parentSectionID, to.parentSectionID);
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster[] clusters, boolean verbose) {
		if (!isClusterSufficient(clusters[0])) {
			// never allow on first cluster
			// can fail hard stop as the growing strategy will try each other combination
			if (verbose)
				System.out.println("First cluster ("+clusters[0]+") fails with "+getQuantityStr(clusters[0]));
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		if (allowIfNoDirect) {
			// complicated case, make sure that we only allow deficient clusters if there is
			// no direct path between the cluster before and the cluster after.
			//
			// also, if !allowChained, ensure that we don't have multiple
			// deficient clusters in a row
			
			int streak = 0; // num deficient clusters in a row (clusters with size<minPerParent))
			for (int i=1; i<clusters.length; i++) {
				if (!isClusterSufficient(clusters[i])) {
					if (verbose)
						System.out.println("Cluster "+i+" ("+clusters[i]+") fails with "+getQuantityStr(clusters[i]));
					streak++;
					if (streak > 1) {
						if (!allowChained)
							// multiple deficient clusters in a row aren't allowed
							return PlausibilityResult.FAIL_HARD_STOP;
						// check that we couldn't have skipped the previous deficient cluster
						if (isDirectPossible(clusters[i-streak], clusters[i]))
							// direct was possible, hard stop
							return PlausibilityResult.FAIL_HARD_STOP;
					}
					if (!allowChained) {
						// ensure that we don't have multiple deficient clusters in a row
						if (streak > 1)
							return PlausibilityResult.FAIL_HARD_STOP;
					} else if (streak > 1) {
						
					}
					if (i == clusters.length-1)
						// last one in this strand, so future permutations/jumps could work
						// but it doesn't work as is (not connected to anything
						return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
					// we're in the middle of the strand, lets see if a direct connection was possible
					if (isDirectPossible(clusters[i-1], clusters[i+1]))
						// direct was possible, hard stop
						return PlausibilityResult.FAIL_HARD_STOP;
					if (streak > 1 && isDirectPossible(clusters[i-streak], clusters[i+1]))
						// we took multiple deficient jumps, but the full clusters on either side
						// are directly connected
						return PlausibilityResult.FAIL_HARD_STOP;
					// if we're here then there was a deficient cluster in the middle of the strand,
					// but it was the only way to make a connection between the previous and next
					// clusters, so it's allowed
				} else {
					// passed, reset the streak
					streak = 0;
				}
			}
			return PlausibilityResult.PASS;
		} else {
			// hard fail if any before the last are deficient
			for (int i=1; i<clusters.length-1; i++) {
				if (!isClusterSufficient(clusters[i])) {
					if (verbose)
						System.out.println("Cluster "+i+" ("+clusters[i]+") fails with "+getQuantityStr(clusters[i]));
					return PlausibilityResult.FAIL_HARD_STOP;
				}
			}
			// soft fail if just the last one is deficient (other permutations might work)
			if (!isClusterSufficient(clusters[clusters.length-1])) {
				if (verbose)
					System.out.println("Cluster "+(clusters.length-1)+" ("+clusters[clusters.length-1]+") fails with "+getQuantityStr(clusters[clusters.length-1]));
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			}
			return PlausibilityResult.PASS;
		}
	}

}
