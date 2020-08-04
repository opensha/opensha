package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class CumulativeAzimuthChangeFilter implements PlausibilityFilter {
	
	private AzimuthCalc calc;
	private float threshold;

	public CumulativeAzimuthChangeFilter(AzimuthCalc calc, float threshold) {
		this.calc = calc;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() < 3) {
			if (verbose)
				System.out.println(getShortName()+": passing with <3 sects");
			return PlausibilityResult.PASS;
		}
		double tot = calc(rupture, rupture.clusters[0].startSect, null, null, verbose);
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
		if (rupture.getTotalNumSects() < 2) {
			// need at least 2 sections on the first cluster
			if (verbose)
				System.out.println(getShortName()+": failing with <2 sects on first cluster");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		double tot = calc(rupture, rupture.clusters[0].startSect, null, null, verbose);
		if ((float)tot < threshold || verbose) {
			List<FaultSection> subSects = new ArrayList<>(newJump.toCluster.subSects.size()+2);
			subSects.add(rupture.sectPredecessorsMap.get(newJump.fromSection));
			subSects.add(newJump.fromSection);
			subSects.addAll(newJump.toCluster.subSects);
			for (int i=0; i<subSects.size()-2; i++) {
				tot += doCalc(subSects.get(i), subSects.get(i+1), subSects.get(i+2));
				if ((float)tot > threshold && !verbose)
					return PlausibilityResult.FAIL_HARD_STOP;
			}
		}
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+".testJump: passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+".testJump: failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(ClusterRupture rupture, FaultSection sect1, FaultSection sect2,
			FaultSection sect3, boolean verbose) {
		Preconditions.checkNotNull(sect1);
		if (sect2 == null) {
			double tot = 0d;
			for (FaultSection descendent : rupture.sectDescendantsMap.get(sect1)) {
				tot += calc(rupture, sect1, descendent, null, verbose);
			}
			return tot;
		}
		if (sect3 == null) {
			double tot = 0d;
			for (FaultSection descendent : rupture.sectDescendantsMap.get(sect2)) {
				tot += calc(rupture, sect1, sect2, descendent, verbose);
				if ((float)tot > threshold && !verbose)
					return tot;
			}
			return tot;
		}
		double tot = doCalc(sect1, sect2, sect3);
		if ((float)tot > threshold)
			return tot;
		for (FaultSection descendent : rupture.sectDescendantsMap.get(sect3)) {
			tot += calc(rupture, sect2, sect3, descendent, verbose);
			if ((float)tot > threshold && !verbose)
				return tot;
		}
		return tot;
	}
	
	private double doCalc(FaultSection sect1, FaultSection sect2, FaultSection sect3) {
		double beforeAz = calc.calcAzimuth(sect1, sect2);
		double afterAz = calc.calcAzimuth(sect2, sect3);
		
		return Math.abs(JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
	}

	@Override
	public String getShortName() {
		return "CumAzimuth";
	}

	@Override
	public String getName() {
		return "Cumulative Azimuth Filter";
	}

}
