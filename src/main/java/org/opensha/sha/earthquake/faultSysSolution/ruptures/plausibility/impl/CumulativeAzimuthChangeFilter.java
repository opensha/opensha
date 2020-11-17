package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class CumulativeAzimuthChangeFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private AzimuthCalc azCalc;
	private float threshold;

	public CumulativeAzimuthChangeFilter(AzimuthCalc calc, float threshold) {
		this.azCalc = calc;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() < 3) {
			if (verbose)
				System.out.println(getShortName()+": passing with <3 sects");
			return PlausibilityResult.PASS;
		}
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		double tot = calc(navigator, rupture.clusters[0].startSect, null, null, verbose);
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(RuptureTreeNavigator navigator, FaultSection sect1, FaultSection sect2,
			FaultSection sect3, boolean verbose) {
		Preconditions.checkNotNull(sect1);
		if (sect2 == null) {
			double tot = 0d;
			for (FaultSection descendant : navigator.getDescendants(sect1)) {
				tot += calc(navigator, sect1, descendant, null, verbose);
			}
			return tot;
		}
		if (sect3 == null) {
			double tot = 0d;
			for (FaultSection descendant : navigator.getDescendants(sect2)) {
				tot += calc(navigator, sect1, sect2, descendant, verbose);
				if ((float)tot > threshold && !verbose)
					return tot;
			}
			return tot;
		}
		double tot = doCalc(sect1, sect2, sect3, verbose);
		if ((float)tot > threshold)
			return tot;
		for (FaultSection descendant : navigator.getDescendants(sect3)) {
			tot += calc(navigator, sect2, sect3, descendant, verbose);
			if ((float)tot > threshold && !verbose)
				return tot;
		}
		return tot;
	}
	
	private double doCalc(FaultSection sect1, FaultSection sect2, FaultSection sect3, boolean verbose) {
		double beforeAz = azCalc.calcAzimuth(sect1, sect2);
		double afterAz = azCalc.calcAzimuth(sect2, sect3);
		
		double val = Math.abs(JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
		
		if (verbose && (float)val > 0f)
			System.out.println(getShortName()+": ["+sect1.getSectionId()+"=>"+sect2.getSectionId()
				+"]="+beforeAz+"\t["+sect2.getSectionId()+"=>"+sect3.getSectionId()
				+"]="+afterAz+",\tdiff="+val);
		
		return val;
	}

	@Override
	public String getShortName() {
		return "CumAzimuth";
	}

	@Override
	public String getName() {
		return "Cumulative Azimuth Filter";
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumSects() < 3) {
			return 0f;
		}
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		return (float)calc(navigator, rupture.clusters[0].startSect, null, null, false);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost(threshold);
	}

	@Override
	public String getScalarName() {
		return "Cumulative Azimuth Change";
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
