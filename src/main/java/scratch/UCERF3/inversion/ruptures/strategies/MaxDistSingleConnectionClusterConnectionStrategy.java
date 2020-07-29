package scratch.UCERF3.inversion.ruptures.strategies;

import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class MaxDistSingleConnectionClusterConnectionStrategy implements ClusterConnectionStrategy {
	
	private double maxJumpDist;

	public MaxDistSingleConnectionClusterConnectionStrategy(double maxJumpDist) {
		this.maxJumpDist = maxJumpDist;
	}

	@Override
	public int addConnections(List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc) {
		int count = 0;
		for (int c1=0; c1<clusters.size(); c1++) {
			FaultSubsectionCluster cluster1 = clusters.get(c1);
			for (int c2=c1+1; c2<clusters.size(); c2++) {
				FaultSubsectionCluster cluster2 = clusters.get(c2);
				Jump jump = null;
				for (FaultSection s1 : cluster1.subSects) {
					for (FaultSection s2 : cluster2.subSects) {
						double dist = distCalc.getDistance(s1, s2);
						if (dist <= maxJumpDist && (jump == null || dist < jump.distance))
							jump = new Jump(s1, cluster1, s2, cluster2, dist);
					}
				}
				if (jump != null) {
					cluster1.addConnection(jump);
					cluster2.addConnection(jump.reverse());
					count++;
				}
			}
		}
		return count;
	}

}
