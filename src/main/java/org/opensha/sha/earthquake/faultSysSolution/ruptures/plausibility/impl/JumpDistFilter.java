package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Jump distance filter. Not needed when building (assuming it's applied by the cluster connection
 * strategy), but useful when comparing with extranlly built ruptures.
 * @author kevin
 *
 */
public class JumpDistFilter extends JumpPlausibilityFilter {
	
	private double maxDist;
	
	public JumpDistFilter(double maxDist) {
		this.maxDist = maxDist;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		if ((float)newJump.distance > (float)maxDist) {
			if (verbose) System.out.println("Failing for jump over maxDist: "+newJump);
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}

	@Override
	public String getShortName() {
		return "JumpDist";
	}

	@Override
	public String getName() {
		return "Maximum Jump Dist";
	}

}
