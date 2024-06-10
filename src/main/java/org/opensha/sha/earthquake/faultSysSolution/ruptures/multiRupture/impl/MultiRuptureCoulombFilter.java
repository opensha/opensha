package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

/**
 * This tests the Coulomb compatibility of two separate ruptures
 */
public class MultiRuptureCoulombFilter implements MultiRuptureCompatibilityFilter {
	
	private AggregatedStiffnessCalculator aggCalc;
	private float threshold;
	private Directionality directionality;

	/**
	 * 
	 * @param aggCalc stiffness calculator from all of the sections of the nucleation rupture to all of the sections of the target rupture
	 * @param threshold threshold below which indicates a failure 
	 * @param directionality directionality enum specifying if the test must pass in one or both directions
	 */
	public MultiRuptureCoulombFilter(AggregatedStiffnessCalculator aggCalc, float threshold,
			Directionality directionality) {
		this.aggCalc = aggCalc;
		this.threshold = threshold;
		this.directionality = directionality;
	}

	@Override
	public PlausibilityResult apply(MultiRuptureJump jump, boolean verbose) {
		ClusterRupture fromRup = jump.fromRupture;
		List<FaultSection> fromSects = new ArrayList<>(fromRup.getTotalNumSects());
		for (FaultSubsectionCluster cluster : fromRup.getClustersIterable())
			fromSects.addAll(cluster.subSects);
		ClusterRupture toRup = jump.toRupture;
		List<FaultSection> toSects = new ArrayList<>(toRup.getTotalNumSects());
		for (FaultSubsectionCluster cluster : toRup.getClustersIterable())
			toSects.addAll(cluster.subSects);
		float forward = (float)aggCalc.calc(fromSects, toSects);
		if (!verbose && directionality == Directionality.EITHER && forward >= threshold)
			// shortcut
			return PlausibilityResult.PASS;
		if (!verbose && directionality == Directionality.BOTH && forward < threshold)
			// shortcut
			return PlausibilityResult.FAIL_HARD_STOP;
		float reversed = (float)aggCalc.calc(toSects, fromSects);
		boolean result;
		switch (directionality) {
		case BOTH:
			result = forward >= threshold && reversed >= threshold;
			break;
		case EITHER:
			result = forward >= threshold || reversed >= threshold;
			break;
		case SUM:
			result = (forward+reversed) >= threshold;
			break;

		default:
			throw new IllegalStateException();
		}
		PlausibilityResult ret = result ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose) System.out.println("MultiRuptureCoulombFilter: "+ret
				+" (forward="+forward+", reversed="+reversed+")\n\tFromRup: "+fromRup+"\n\tToRup: "+toRup);
		return ret;
	}

}
