package scratch.UCERF3.inversion.ruptures.strategies;

import java.util.List;

import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

/**
 * This interface defines cluster connections. The addConnections method will be called in order to 
 * populate the connections of each cluster
 * 
 * @author kevin
 *
 */
public interface ClusterConnectionStrategy {
	
	/**
	 * Add all possible connections between the given clusters (via the
	 * FaultSubsectionCluster.addConnection(Jump) method)
	 * 
	 * @param clusters
	 * @param distCalc
	 * @return the number of connections added
	 */
	public int addConnections(List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc);

}
