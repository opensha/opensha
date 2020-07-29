package scratch.UCERF3.inversion.ruptures.plausibility;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;

public abstract class JunctionPlausibiltyFilter implements PlausibilityFilter {

	@Override
	public PlausibilityResult apply(ClusterRupture rupture) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (Jump jump : rupture.jumps) {
			result = test(rupture, jump);
			if (!result.canContinue())
				break;
		}
		return result;
	}

}
