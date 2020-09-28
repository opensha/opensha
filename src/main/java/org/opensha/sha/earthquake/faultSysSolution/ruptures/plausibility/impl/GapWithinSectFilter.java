package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This ensures that clusters never jump to themselves (which would skip a section)
 * @author kevin
 *
 */
public class GapWithinSectFilter extends JumpPlausibilityFilter {

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		if (newJump.fromCluster.parentSectionID == newJump.toCluster.parentSectionID)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

	@Override
	public String getShortName() {
		return "GapWithinSect";
	}

	@Override
	public String getName() {
		return "Gap Within Fault Section";
	}

}
