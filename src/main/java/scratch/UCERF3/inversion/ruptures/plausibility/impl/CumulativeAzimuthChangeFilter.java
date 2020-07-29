package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class CumulativeAzimuthChangeFilter implements PlausibilityFilter {
	
	private SectionDistanceAzimuthCalculator calc;
	private double threshold;
	private boolean flipLeftLateral;
	private boolean applyAtJumpsOnly;

	public CumulativeAzimuthChangeFilter(SectionDistanceAzimuthCalculator calc,
			double threshold, boolean flipLeftLateral, boolean applyAtJumpsOnly) {
		this.calc = calc;
		this.threshold = threshold;
		this.flipLeftLateral = flipLeftLateral;
		this.applyAtJumpsOnly = applyAtJumpsOnly;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture) {
		double tot = calcForRup(rupture);
		if (Double.isNaN(tot))
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		if (tot < threshold)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	public double calcForRup(ClusterRupture rupture) {
		double tot = 0d;
		for (Jump jump : rupture.jumps) {
			tot += calcForJump(jump);
			if (Double.isNaN(tot) || tot > threshold)
				// stop;
				return tot;
		}
		if (!applyAtJumpsOnly) {
			for (FaultSubsectionCluster cluster : rupture.primaryStrand) {
				tot += calcForCluster(cluster);
				if (Double.isNaN(tot) || tot > threshold)
					// stop;
					return tot;
			}
		}
		Preconditions.checkState(Double.isFinite(tot));
		return tot;
	}

	@Override
	public PlausibilityResult test(ClusterRupture rupture, Jump jump) {
		double tot = calcForRup(rupture);
		tot += calcForJump(jump);
		if (Double.isNaN(tot))
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		if (tot < threshold)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calcForCluster(FaultSubsectionCluster cluster) {
		Preconditions.checkState(!applyAtJumpsOnly);
		double tot = 0d;
		for (int i=1; i<cluster.subSects.size()-1; i++) {
			FaultSection s0 = cluster.subSects.get(i-1);
			FaultSection s1 = cluster.subSects.get(i);
			FaultSection s2 = cluster.subSects.get(i+1);
			double az1 = JumpAzimuthChangeFilter.calcAzimuth(calc, s0, s1, flipLeftLateral);
			double az2 = JumpAzimuthChangeFilter.calcAzimuth(calc, s1, s2, flipLeftLateral);
			tot += Math.abs(JumpAzimuthChangeFilter.getAzimuthDifference(az1, az2));
		}
		Preconditions.checkState(Double.isFinite(tot));
		return tot;
	}
	
	private double calcForJump(Jump jump) {
		Preconditions.checkNotNull(jump.leadingSections, "Jump doesn't have leading sections populated");
		if (jump.leadingSections.size() < 2)
			// fewer than 2 sections before the first jump, will never work
			return Double.POSITIVE_INFINITY;
		if (jump.toCluster.subSects.size() < 2)
			// can't evaluate now, but could be a single section connection to another fault
			return Double.NaN;
		
		FaultSection before1 = jump.leadingSections.get(jump.leadingSections.size()-2);
		FaultSection before2 = jump.leadingSections.get(jump.leadingSections.size()-1);
		Preconditions.checkState(before2.equals(jump.fromSection));
		double beforeAz = JumpAzimuthChangeFilter.calcAzimuth(calc, before1, before2, flipLeftLateral);
		
		FaultSection after1 = jump.toCluster.subSects.get(0);
		Preconditions.checkState(after1.equals(jump.toSection));
		FaultSection after2 = jump.toCluster.subSects.get(1);
		double afterAz = JumpAzimuthChangeFilter.calcAzimuth(calc, after1, after2, flipLeftLateral);
		
		return JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
	}

}
