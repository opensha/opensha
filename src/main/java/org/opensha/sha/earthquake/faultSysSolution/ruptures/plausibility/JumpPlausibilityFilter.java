package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

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
	
	/**
	 * Apply the plausibility filter to the given jump only
	 * 
	 * @param rupture
	 * @param newJump
	 * @param verbose
	 * @return
	 */
	public abstract PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose);

}
