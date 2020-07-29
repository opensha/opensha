package scratch.UCERF3.inversion.ruptures.plausibility;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;

public interface PlausibilityFilter {
	
	public PlausibilityResult apply(ClusterRupture rupture);
	
	public PlausibilityResult test(ClusterRupture rupture, Jump jump);

}
