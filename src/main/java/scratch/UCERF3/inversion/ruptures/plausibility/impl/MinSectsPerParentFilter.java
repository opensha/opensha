package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;

public class MinSectsPerParentFilter implements PlausibilityFilter {
	
	private int minPerParent;
	private boolean allowIfNoDirect;
	private HashSet<IDPairing> parentConnections;

	public MinSectsPerParentFilter(int minPerParent, boolean allowIfNoDirect,
			List<FaultSubsectionCluster> clusters) {
		this.minPerParent = minPerParent;
		this.allowIfNoDirect = allowIfNoDirect;
		if (allowIfNoDirect) {
			parentConnections = new HashSet<>();
			for (FaultSubsectionCluster cluster : clusters) {
				for (Jump jump : cluster.getConnections()) {
					parentConnections.add(new IDPairing(cluster.parentSectionID, jump.toCluster.parentSectionID));
					parentConnections.add(new IDPairing(jump.toCluster.parentSectionID, cluster.parentSectionID));
				}
			}
		}
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture) {
		PlausibilityResult result = apply(rupture.primaryStrand);
		for (FaultSubsectionCluster[] strand : rupture.splays) {
			if (allowIfNoDirect && strand[0].subSects.size() < minPerParent && strand.length > 1) {
				// add the parent to this splay
				FaultSection first = strand[0].subSects.get(0);
				for (Jump jump : rupture.jumps) {
					if (jump.toSection.equals(first)) {
						FaultSubsectionCluster[] newStrand = new FaultSubsectionCluster[strand.length+1];
						newStrand[0] = jump.fromCluster;
						System.arraycopy(strand, 0, newStrand, 1, strand.length);
						strand = newStrand;
						break;
					}
				}
			}
			result = result.logicalAnd(apply(strand));
		}
		return result;
	}
	
	private boolean isDirectPossible(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return parentConnections.contains(new IDPairing(from.parentSectionID, to.parentSectionID));
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster[] strand) {
		if (strand[0].subSects.size() < minPerParent) {
			// never allow on first cluster
			if (strand.length == 0)
				// other permutations could work, soft fail
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		if (allowIfNoDirect) {
			// complicated case, make sure that we only allow deficient clusters if there is
			// no direct path between the cluster before and the cluster after. also ensure
			// that we don't have multiple deficient clusters in a row
			
			int streak = 0; // num deficient clusters in a row
			for (int i=1; i<strand.length; i++) {
				if (strand[i].subSects.size() < minPerParent) {
					streak++;
					if (streak == 3)
						// 3 in a row, impossible to continue
						return PlausibilityResult.FAIL_HARD_STOP;
					if (streak == 2) {
						// 2 in a row, only possible to continue if we're at the last one
						if (i == strand.length-1)
							return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
						return PlausibilityResult.FAIL_HARD_STOP;
					}
					// streak == 1
					if (i == strand.length-1)
						// last one, so future permutations/jumps could work
						return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
					// we're in the middle of the strand, lets see if a direct connection was possible
					if (isDirectPossible(strand[i-1], strand[i+1]))
						// direct was possible, hard stop
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
			for (int i=1; i<strand.length-1; i++)
				if (strand[i].subSects.size() < minPerParent)
					return PlausibilityResult.FAIL_HARD_STOP;
			// soft fail if just the last one is deficient (other permutations might work)
			if (strand[strand.length-1].subSects.size() < minPerParent)
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			return PlausibilityResult.PASS;
		}
	}

	@Override
	public PlausibilityResult test(ClusterRupture rupture, Jump jump) {
		if (allowIfNoDirect) {
			// need to test the whole rupture to see if that was necessary
			boolean failPossible = jump.toCluster.subSects.size() < minPerParent
					|| jump.fromCluster.subSects.size() < minPerParent;
			for (Jump prevJump : rupture.jumps)
				failPossible = failPossible || prevJump.fromCluster.subSects.size() < minPerParent;
			if (failPossible)
				return apply(rupture.take(jump));
			return PlausibilityResult.PASS;
		}
		if (jump.toCluster.subSects.size() >= minPerParent)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

}
