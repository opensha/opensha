package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Total azimuthal change filter from the start of the rupture to the end (or ends in the case of
 * a splayed rupture).
 * 
 * @author kevin
 *
 */
public class TotalAzimuthChangeFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private AzimuthCalc azCalc;
	private float threshold;
	private boolean multiFaultOnly;
	private boolean testFullEnd;

	/**
	 * 
	 * @param calc azimuth calculator
	 * @param threshold maximum allowed azimuthal change
	 * @param multiFaultOnly only apply to multifault ruptures (i.e., don't test a single cluster
	 * rupture which could theoretically bend more than the threshold). true in UCERF3
	 * @param testFullEnd UCERF3 tested total azimuth to the full end of each rupture. e.g., imagine a rupture
	 * with 4 sbusections on the last cluster: it would test total azimuth to sections pairs [0,1], [1,2],
	 * [2,3], and [3,4]. This was unintended, and should probably be disabled for new models
	 */
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
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		PlausibilityResult result = apply(rupture.clusters[0],
				rupture.clusters[rupture.clusters.length-1], navigator, verbose);
		if (verbose)
			System.out.println(getShortName()+": primary starnd result="+result);
		for (ClusterRupture splay : rupture.splays.values())
			result = result.logicalAnd(apply(rupture.clusters[0],
					splay.clusters[splay.clusters.length-1], navigator, verbose));
		return result;
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster startCluster,
			FaultSubsectionCluster endCluster, RuptureTreeNavigator navigator, boolean verbose) {
		double maxDiff = getValue(startCluster, endCluster, navigator, verbose);
		if ((float)maxDiff <= threshold)
			return PlausibilityResult.PASS;
		if (verbose)
			System.out.println(getShortName()+": failing with diff="+maxDiff);
		
		if (endCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double getValue(FaultSubsectionCluster startCluster,
			FaultSubsectionCluster endCluster, RuptureTreeNavigator navigator, boolean verbose) {
		FaultSection before1 = startCluster.startSect;
		List<FaultSection> before2s = new ArrayList<>();
		if (startCluster.subSects.size() == 1) {
			// use the first section of the next cluster
			before2s.addAll(navigator.getDescendants(before1));
		} else {
			before2s.add(startCluster.subSects.get(1));
		}
		Preconditions.checkState(!before2s.isEmpty());
		double maxDiff = 0d;
		for (FaultSection before2 : before2s) {
			double beforeAz = azCalc.calcAzimuth(before1, before2);
			
			if (endCluster.subSects.size() == 1) {
				// need to use the last section of the previous cluster
				
				FaultSection after2 = endCluster.subSects.get(0);
				FaultSection after1 = navigator.getPredecessor(after2);
				double afterAz = azCalc.calcAzimuth(after1, after2);
				
				double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
//				System.out.println(beforeAz+" => "+afterAz+" = "+diff);
				if (verbose)
					System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
							+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
				maxDiff = Math.max(Math.abs(diff), maxDiff);
			}
			int startIndex = testFullEnd ? 0 : endCluster.subSects.size()-2;
			for (int i=startIndex; i<endCluster.subSects.size()-1; i++) {
				FaultSection after1 = endCluster.subSects.get(i);
				FaultSection after2 = endCluster.subSects.get(i+1);
				double afterAz = azCalc.calcAzimuth(after1, after2);
				
				double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
//				System.out.println(beforeAz+" => "+afterAz+" = "+diff);
				if (verbose)
					System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
							+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
				maxDiff = Math.max(Math.abs(diff), maxDiff);
			}
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
	
	public Float getValue(FaultSubsectionCluster startCluster, ClusterRupture rupture,
			RuptureTreeNavigator navigator) {
		if (rupture.getTotalNumSects() < 3)
			return null;
		float maxVal = (float)getValue(startCluster,
				rupture.clusters[rupture.clusters.length-1], navigator, false);
		for (ClusterRupture splay : rupture.splays.values()) {
			Float splayVal = getValue(startCluster, splay, navigator);
			if (splayVal == null)
				return null;
			maxVal = Float.max(maxVal, splayVal);
		}
		return maxVal;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return getValue(rupture.clusters[0], rupture, rupture.getTreeNavigator());
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost(threshold);
	}
	
	@Override
	public String getScalarName() {
		return "Total Azimuth Change";
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
