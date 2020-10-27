package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.HashMultimap;

class PrecomputedClusterConnectionStrategy extends ClusterConnectionStrategy {

	private String name;
	private double maxJumpDist;

	PrecomputedClusterConnectionStrategy(String name, List<? extends FaultSection> subSections,
			List<FaultSubsectionCluster> clusters, double maxJumpDist) {
		super(subSections, clusters);
		this.name = name;
		this.maxJumpDist = maxJumpDist;
		this.connectionsAdded = true;
		connectedParents = new HashSet<>();
		jumpsFrom = HashMultimap.create();
		for (FaultSubsectionCluster cluster : clusters) {
			for (Jump jump : cluster.getConnections()) {
				connectedParents.add(new IDPairing(cluster.parentSectionID, jump.toCluster.parentSectionID));
				jumpsFrom.put(jump.fromSection, jump);
			}
		}
		if (connectedParents.isEmpty())
			System.err.println("WARNING: no connections detected");
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		throw new IllegalStateException("Already built when pre-computed");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}

}
