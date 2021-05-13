package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Jump distance filter. Not needed when building (assuming it's applied by the cluster connection
 * strategy), but useful when comparing with extranlly built ruptures.
 * @author kevin
 *
 */
public class JumpDistFilter extends JumpPlausibilityFilter implements ScalarValuePlausibiltyFilter<Float> {
	
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

	@Override
	public Float getValue(ClusterRupture rupture) {
		float max = 0f;
		for (Jump jump : rupture.getJumpsIterable())
			max = Float.max(max, (float)jump.distance);
		return max;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost((float)maxDist);
	}
	
	@Override
	public String getScalarName() {
		return "Jump Distance";
	}

	@Override
	public String getScalarUnits() {
		return "km";
	}

}
