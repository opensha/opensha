package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Cumulative rake change filter which is applied at jumping points
 * (as in UCERF3, but without some of the peculiarities: see U3CompatibleCumulativeRakeChangeFilter)
 * 
 * @author kevin
 *
 */
public class JumpCumulativeRakeChangeFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private float threshold;

	public JumpCumulativeRakeChangeFilter(float threshold) {
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double tot = calc(rupture, verbose, !verbose);
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(ClusterRupture rupture, boolean verbose, boolean shortCircuit) {
		double tot = 0d;
		for (Jump jump : rupture.getJumpsIterable()) {
			double diff = calc(jump, verbose);
			tot += diff;
			if ((float)tot > threshold && shortCircuit)
				return tot;
		}
		return tot;
	}
	
	private double calc(Jump jump, boolean verbose) {
		FaultSection sect1 = jump.fromSection;
		FaultSection sect2 = jump.toSection;
		double rake1 = sect1.getAveRake();
		double rake2 = sect2.getAveRake();
		double diff = CumulativeRakeChangeFilter.rakeDiff(rake1, rake2);
		if (verbose && diff != 0d)
			System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
						+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
		return diff;
	}

	@Override
	public String getShortName() {
		return "JumpCumRake";
	}

	@Override
	public String getName() {
		return "Jump Cumulative Rake Filter";
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return (float)calc(rupture, false, false);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost(threshold);
	}
	
	@Override
	public String getScalarName() {
		return "Cumulative Rake Change";
	}

	@Override
	public String getScalarUnits() {
		return "Degrees";
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

}
