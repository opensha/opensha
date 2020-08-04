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
 * Cumulative rake change filter which is applied at the subsection level.
 * 
 * @author kevin
 *
 */
public class CumulativeRakeChangeFilter implements PlausibilityFilter {
	
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
		double tot = calc(rupture, rupture.clusters[0].startSect, verbose);
		if ((float)tot <= threshold) {
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
		double tot = calc(rupture, rupture.clusters[0].startSect, verbose);
		if ((float)tot <= threshold || verbose) {
			List<FaultSection> subSects = new ArrayList<>(newJump.toCluster.subSects.size()+2);
			subSects.add(newJump.fromSection);
			subSects.addAll(newJump.toCluster.subSects);
			tot += calc(subSects, verbose);
		}
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(ClusterRupture rupture, FaultSection sect1, boolean verbose) {
		double tot = 0d;
		double rake1 = sect1.getAveRake();
		for (FaultSection sect2 : rupture.sectDescendantsMap.get(sect1)) {
			double rake2 = sect2.getAveRake();
			double diff = rakeDiff(rake1, rake2);
			if (verbose && diff != 0d)
				System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
							+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
			tot += diff;
			if ((float)tot > threshold && !verbose)
				return tot;
			tot += calc(rupture, sect2, verbose);
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

}
