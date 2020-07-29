package scratch.UCERF3.inversion.ruptures.strategies;

import java.util.List;

import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public interface ClusterConnectionStrategy {
	
	public int addConnections(List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc);

}
