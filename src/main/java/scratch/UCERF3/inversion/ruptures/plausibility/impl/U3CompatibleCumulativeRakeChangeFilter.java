package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;

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
public class U3CompatibleCumulativeRakeChangeFilter implements PlausibilityFilter {
	
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
		double tot = calc(rupture, rupture.clusters[0].startSect, stopAt, 0d, verbose);
		if (tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		return apply(rupture.take(newJump), verbose);
	}
	
	private double calc(ClusterRupture rupture, FaultSection sect1, FaultSection stopAt,
			double tot, boolean verbose) {
		double rake1 = sect1.getAveRake();
		for (FaultSection sect2 : rupture.sectDescendantsMap.get(sect1)) {
			double rake2 = sect2.getAveRake();
			double diff = rakeDiff(rake1, rake2);
			tot += diff;
			if (verbose && diff != 0d)
				System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
							+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
			if (tot > threshold && !verbose)
				return tot;
			if (sect2 == stopAt)
				// UCERF3 compatibility: don't check the full last section
				break;
			tot = calc(rupture, sect2, stopAt, tot, verbose);
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

}
