package scratch.UCERF3.inversion.ruptures.strategies;

import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;

public interface ClusterPermutationStrategy {
	
	public List<FaultSubsectionCluster> getPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection);
	
	public boolean arePermutationsIncremental();
}
