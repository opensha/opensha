package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class TotalAzimuthChangeFilter implements PlausibilityFilter {
	
	private AzimuthCalc calc;
	private float threshold;
	private boolean multiFaultOnly;
	private boolean testFullEnd;

	public TotalAzimuthChangeFilter(AzimuthCalc calc, float threshold, boolean multiFaultOnly,
			boolean testFullEnd) {
		this.calc = calc;
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
		if (startCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_HARD_STOP;
		if (endCluster.subSects.size() < 2)
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		FaultSection before1 = startCluster.firstSect;
		FaultSection before2 = startCluster.subSects.get(1);
		double beforeAz = calc.calcAzimuth(before1, before2);
		
		int startIndex = testFullEnd ? 0 : endCluster.subSects.size()-2;
		double maxDiff = 0d;
		for (int i=startIndex; i<endCluster.subSects.size()-1; i++) {
			FaultSection after1 = endCluster.subSects.get(i);
			FaultSection after2 = endCluster.subSects.get(i+1);
			double afterAz = calc.calcAzimuth(after1, after2);
			
			double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
//			System.out.println(beforeAz+" => "+afterAz+" = "+diff);
			if (verbose)
				System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
						+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
			maxDiff = Math.max(Math.abs(diff), maxDiff);
		}
		if ((float)maxDiff <= threshold)
			return PlausibilityResult.PASS;
		if (verbose)
			System.out.println(getShortName()+": failing with diff="+maxDiff);
		
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public String getShortName() {
		return "TotalAz";
	}

	@Override
	public String getName() {
		return "Total Azimuth Change Filter";
	}

}
