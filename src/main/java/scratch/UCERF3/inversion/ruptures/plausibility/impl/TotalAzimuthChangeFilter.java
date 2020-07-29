package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class TotalAzimuthChangeFilter implements PlausibilityFilter {
	
	private SectionDistanceAzimuthCalculator calc;
	private double threshold;
	private boolean flipLeftLateral;

	public TotalAzimuthChangeFilter(SectionDistanceAzimuthCalculator calc, double threshold, boolean flipLeftLateral) {
		this.calc = calc;
		this.threshold = threshold;
		this.flipLeftLateral = flipLeftLateral;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture) {
		if (rupture.sectsSet.size() < 3)
			return PlausibilityResult.PASS;
		PlausibilityResult result = apply(rupture.primaryStrand[0],
				rupture.primaryStrand[rupture.primaryStrand.length-1]);
		for (FaultSubsectionCluster[] strand : rupture.splays)
			result = result.logicalAnd(apply(rupture.primaryStrand[0], strand[strand.length-1]));
		return result;
	}

	@Override
	public PlausibilityResult test(ClusterRupture rupture, Jump jump) {
		return apply(rupture.primaryStrand[0], jump.toCluster);
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster startCluster, FaultSubsectionCluster endCluster) {
		if (startCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_HARD_STOP;
		if (endCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		FaultSection before1 = startCluster.firstSect;
		FaultSection before2 = startCluster.subSects.get(1);
		double beforeAz = JumpAzimuthChangeFilter.calcAzimuth(calc, before1, before2, flipLeftLateral);
		
		FaultSection after1 = endCluster.subSects.get(endCluster.subSects.size()-2);
		FaultSection after2 = endCluster.subSects.get(endCluster.subSects.size()-1);
		double afterAz = JumpAzimuthChangeFilter.calcAzimuth(calc, after1, after2, flipLeftLateral);
		
		double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
//		System.out.println(beforeAz+" => "+afterAz+" = "+diff);
		if (Math.abs(diff) <= threshold)
			return PlausibilityResult.PASS;
		
		return PlausibilityResult.FAIL_HARD_STOP;
	}

}
