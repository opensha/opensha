package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class TotalAzimuthChangeFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private AzimuthCalc azCalc;
	private float threshold;
	private boolean multiFaultOnly;
	private boolean testFullEnd;

	public TotalAzimuthChangeFilter(AzimuthCalc calc, float threshold, boolean multiFaultOnly,
			boolean testFullEnd) {
		this.azCalc = calc;
		this.threshold = threshold;
		this.multiFaultOnly = multiFaultOnly;
		this.testFullEnd = testFullEnd;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() < 3) {
			if (verbose)
				System.out.println(getShortName()+": passing because <3 sects");
			return PlausibilityResult.PASS;
		}
		if (multiFaultOnly && rupture.getTotalNumJumps() == 0) {
			if (verbose)
				System.out.println(getShortName()+": passing because no jumps");
			return PlausibilityResult.PASS;
		}
		PlausibilityResult result = apply(rupture.clusters[0],
				rupture.clusters[rupture.clusters.length-1], verbose);
		for (ClusterRupture splay : rupture.splays.values())
			result = result.logicalAnd(apply(rupture.clusters[0],
					splay.clusters[splay.clusters.length-1], verbose));
		return result;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump jump, boolean verbose) {
		return apply(rupture.clusters[0], jump.toCluster, verbose);
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster startCluster,
			FaultSubsectionCluster endCluster, boolean verbose) {
		PlausibilityResult result = testIfPossible(startCluster, endCluster);
		if (!result.canContinue())
			return result;
		double maxDiff = getValue(startCluster, endCluster, verbose);
		if ((float)maxDiff <= threshold)
			return PlausibilityResult.PASS;
		if (verbose)
			System.out.println(getShortName()+": failing with diff="+maxDiff);
		
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private PlausibilityResult testIfPossible(FaultSubsectionCluster startCluster,
			FaultSubsectionCluster endCluster) {
		if (startCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_HARD_STOP;
		if (endCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		return PlausibilityResult.PASS;
	}
	
	private double getValue(FaultSubsectionCluster startCluster,
			FaultSubsectionCluster endCluster, boolean verbose) {
		FaultSection before1 = startCluster.startSect;
		FaultSection before2 = startCluster.subSects.get(1);
		double beforeAz = azCalc.calcAzimuth(before1, before2);
		
		int startIndex = testFullEnd ? 0 : endCluster.subSects.size()-2;
		double maxDiff = 0d;
		for (int i=startIndex; i<endCluster.subSects.size()-1; i++) {
			FaultSection after1 = endCluster.subSects.get(i);
			FaultSection after2 = endCluster.subSects.get(i+1);
			double afterAz = azCalc.calcAzimuth(after1, after2);
			
			double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
//			System.out.println(beforeAz+" => "+afterAz+" = "+diff);
			if (verbose)
				System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
						+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
			maxDiff = Math.max(Math.abs(diff), maxDiff);
		}
		return maxDiff;
	}

	@Override
	public String getShortName() {
		return "TotalAz";
	}

	@Override
	public String getName() {
		return "Total Azimuth Change Filter";
	}
	
	public Float getValue(FaultSubsectionCluster startCluster, ClusterRupture rupture) {
		if (!testIfPossible(startCluster, rupture.clusters[rupture.clusters.length-1]).canContinue())
			return null;
		float maxVal = (float)getValue(startCluster, rupture.clusters[rupture.clusters.length-1], false);
		for (ClusterRupture splay : rupture.splays.values()) {
			Float splayVal = getValue(startCluster, splay);
			if (splayVal == null)
				return null;
			maxVal = Float.max(maxVal, splayVal);
		}
		return maxVal;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return getValue(rupture.clusters[0], rupture);
	}

	@Override
	public Float getValue(ClusterRupture rupture, Jump newJump) {
		if (!testIfPossible(rupture.clusters[0], newJump.toCluster).canContinue())
			return null;
		return (float)getValue(rupture.clusters[0], newJump.toCluster, false);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost(threshold);
	}

}
