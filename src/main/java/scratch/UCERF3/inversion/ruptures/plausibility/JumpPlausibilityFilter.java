package scratch.UCERF3.inversion.ruptures.plausibility;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;

public abstract class JumpPlausibilityFilter implements PlausibilityFilter {

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (Jump jump : rupture.internalJumps) {
			if (!result.canContinue())
				return result;
			result = result.logicalAnd(testJump(rupture, jump, verbose));
//			if (verbose)
//				System.out.println("\t"+getShortName()+" applied at jump: "+jump+", result="+result);
		}
		for (Jump jump : rupture.splays.keySet()) {
			if (!result.canContinue())
				return result;
			result = result.logicalAnd(testJump(rupture, jump, verbose));
//			if (verbose)
//				System.out.println("\t"+getShortName()+" applied at splay jump: "+jump+", result="+result);
			if (!result.canContinue())
				return result;
			result = result.logicalAnd(apply(rupture.splays.get(jump), verbose));
//			if (verbose)
//				System.out.println("\t"+getShortName()+" applied to splay from "+jump+", result="+result);
		}
		return result;
	}

}
