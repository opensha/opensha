package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;

/**
 * Cumulative rake change filter which is applied at jumping points
 * (as in UCERF3, but without some of the peculiarities: see U3CompatibleCumulativeRakeChangeFilter)
 * 
 * @author kevin
 *
 */
public class JumpCumulativeRakeChangeFilter implements PlausibilityFilter {
	
	private float threshold;

	public JumpCumulativeRakeChangeFilter(float threshold) {
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double tot = calc(rupture, verbose);
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
		double tot = calc(rupture, verbose);
		if (verbose)
			System.out.println(getShortName()+": orig rup was "+tot+", now testing jump "+newJump);
		if ((float)tot <= threshold || verbose)
			tot += calc(newJump, verbose);
		if ((float)tot <= threshold) {
			if (verbose)
				System.out.println(getShortName()+": passing with tot="+tot);
			return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": failing with tot="+tot);
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(ClusterRupture rupture, boolean verbose) {
		double tot = 0d;
		for (Jump jump : rupture.internalJumps) {
			double diff = calc(jump, verbose);
			tot += diff;
			if ((float)tot > threshold && !verbose)
				return tot;
		}
		for (Jump jump : rupture.splays.keySet()) {
			double diff = calc(jump, verbose);
			tot += diff;
			if ((float)tot > threshold && !verbose)
				return tot;
			tot += calc(rupture.splays.get(jump), verbose);
		}
		return tot;
	}
	
	private double calc(Jump jump, boolean verbose) {
		FaultSection sect1 = jump.fromSection;
		FaultSection sect2 = jump.toSection;
		double rake1 = sect1.getAveRake();
		double rake2 = sect2.getAveRake();
		double diff = CumulativeRakeChangeFilter.rakeDiff(rake1, rake2);
		if (verbose && diff != 0d)
			System.out.println(getShortName()+": "+sect1.getSectionId()+"="+(float)rake1+" => "
						+sect2.getSectionId()+"="+(float)rake2+" = "+diff);
		return diff;
	}

	@Override
	public String getShortName() {
		return "JumpCumRake";
	}

	@Override
	public String getName() {
		return "Jump Cumulative Rake Filter";
	}

}
