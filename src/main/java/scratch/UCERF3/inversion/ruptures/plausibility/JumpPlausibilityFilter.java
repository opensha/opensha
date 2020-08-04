package scratch.UCERF3.inversion.ruptures.plausibility;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;

/**
 * Plausibility filter which is applied independently at each jump
 * 
 * @author kevin
 *
 */
public abstract class JumpPlausibilityFilter implements PlausibilityFilter {

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (Jump jump : rupture.getJumpsIterable()) {
			if (!result.canContinue())
				return result;
			result = result.logicalAnd(testJump(rupture, jump, verbose));
//			if (verbose)
//				System.out.println("\t"+getShortName()+" applied at jump: "+jump+", result="+result);
		}
		return result;
	}

}
