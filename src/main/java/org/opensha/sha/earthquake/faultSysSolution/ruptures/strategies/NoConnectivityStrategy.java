package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * No connectivity allowed
 * 
 * @author kevin
 *
 */
public class NoConnectivityStrategy extends ClusterConnectionStrategy {

	public NoConnectivityStrategy(List<? extends FaultSection> subSections, List<FaultSubsectionCluster> clusters) {
		super(subSections, clusters);
	}

	public NoConnectivityStrategy(List<? extends FaultSection> subSections) {
		super(subSections);
	}

	@Override
	public String getName() {
		return "No Connectivity";
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return null;
	}

	@Override
	public double getMaxJumpDist() {
		return 0d;
	}

}
