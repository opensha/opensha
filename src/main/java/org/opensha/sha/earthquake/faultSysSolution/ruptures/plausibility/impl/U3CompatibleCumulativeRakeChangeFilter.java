package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Cumulative rake change filter which matches UCERF3. This is done in 3 ways:
 * 
 * 1) it preserves the precision issues in UCERF3 where something double precision errors 
 * can accumulate and cause failures with, e.g., 180.00000000000003 > 180
 * 2) rake changes can accumulate within sections, but are only tested at jumps. so if a
 * change happens during the last section which brings it over the threshold, that's OK
 * 3) Keep running total, don't sum recursively. this is necessary as a running total must 
 * be kept in order to have the same floating point error propagation.
 * 
 * @author kevin
 *
 */
public class U3CompatibleCumulativeRakeChangeFilter implements ScalarValuePlausibiltyFilter<Double> {
	
	private double threshold;

	public U3CompatibleCumulativeRakeChangeFilter(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() < 2) {
			if (verbose)
				System.out.println(getShortName()+": passing with <2 sects");
			return PlausibilityResult.PASS;
		}
		FaultSection stopAt = rupture.clusters[rupture.clusters.length-1].startSect;
		double tot = calc(rupture.getTreeNavigator(), rupture.clusters[0].startSect,
				stopAt, 0d, verbose, !verbose);
		if (tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(RuptureTreeNavigator navigator, FaultSection sect1, FaultSection stopAt,
			double tot, boolean verbose, boolean shortCircuit) {
		double rake1 = sect1.getAveRake();
		for (FaultSection sect2 : navigator.getDescendants(sect1)) {
			double rake2 = sect2.getAveRake();
			double diff = rakeDiff(rake1, rake2);
			tot += diff;
			if (verbose && diff != 0d)
				System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
							+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
			if (tot > threshold && shortCircuit)
				return tot;
			if (sect2 == stopAt)
				// UCERF3 compatibility: don't check the full last section
				break;
			tot = calc(navigator, sect2, stopAt, tot, verbose, shortCircuit);
		}
		return tot;
	}
	
	static double rakeDiff(double rake1, double rake2) {
		double rakeDiff = Math.abs(rake1 - rake2);
		if (rakeDiff > 180)
			rakeDiff = 360-rakeDiff; // Deal with branch cut (180deg = -180deg)
		Preconditions.checkState(rakeDiff >= 0);
		return rakeDiff;
	}

	@Override
	public String getShortName() {
		return "U3CumRake";
	}

	@Override
	public String getName() {
		return "UCERF3 Cumulative Rake Filter";
	}

	@Override
	public Double getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumSects() < 2) {
			return 0d;
		}
		FaultSection stopAt = rupture.clusters[rupture.clusters.length-1].startSect;
		return calc(rupture.getTreeNavigator(), rupture.clusters[0].startSect, stopAt, 0d, false, false);
	}

	@Override
	public Range<Double> getAcceptableRange() {
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
