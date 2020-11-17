package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Cumulative rake change filter which is applied at the subsection level.
 * 
 * @author kevin
 *
 */
public class CumulativeRakeChangeFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private float threshold;

	public CumulativeRakeChangeFilter(float threshold) {
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() < 1) {
			if (verbose)
				System.out.println(getShortName()+": passing with <3 sects");
			return PlausibilityResult.PASS;
		}
		double tot = calc(rupture.getTreeNavigator(), rupture.clusters[0].startSect, verbose, !verbose);
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(RuptureTreeNavigator navigator, FaultSection sect1,
			boolean verbose, boolean shortCircuit) {
		double tot = 0d;
		double rake1 = sect1.getAveRake();
		for (FaultSection sect2 : navigator.getDescendants(sect1)) {
			double rake2 = sect2.getAveRake();
			double diff = rakeDiff(rake1, rake2);
			if (verbose && diff != 0d)
				System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
							+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
			tot += diff;
			if ((float)tot > threshold && shortCircuit)
				return tot;
			tot += calc(navigator, sect2, verbose, shortCircuit);
		}
		return tot;
	}
	
	private double calc(List<FaultSection> sects, boolean verbose) {
		double tot = 0d;
		for (int i=1; i<sects.size(); i++) {
			FaultSection sect1 = sects.get(i-1);
			FaultSection sect2 = sects.get(i);
			double rake1 = sect1.getAveRake();
			double rake2 = sect2.getAveRake();
			double diff = rakeDiff(rake1, rake2);
			if (verbose && diff != 0d)
				System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
							+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
			tot += diff;
			if ((float)tot > threshold && !verbose)
				return tot;
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
		return "CumRake";
	}

	@Override
	public String getName() {
		return "Cumulative Rake Filter";
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return (float)calc(rupture.getTreeNavigator(), rupture.clusters[0].startSect, false, false);
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
