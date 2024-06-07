package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityResult;
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
	public MultiRuptureCompatibilityResult apply(MultiRuptureCompatibilityResult previousResult,
			ClusterRupture nucleation, ClusterRupture target, boolean verbose) {
		List<FaultSection> nuclSects = new ArrayList<>(nucleation.getTotalNumSects());
		for (FaultSubsectionCluster cluster : nucleation.getClustersIterable())
			nuclSects.addAll(cluster.subSects);
		List<FaultSection> targetSects = new ArrayList<>(target.getTotalNumSects());
		for (FaultSubsectionCluster cluster : target.getClustersIterable())
			targetSects.addAll(cluster.subSects);
		float nuclToTarget = (float)aggCalc.calc(nuclSects, targetSects);
		if (!verbose && directionality == Directionality.EITHER && nuclToTarget >= threshold)
			// shortcut
			return MultiRuptureCompatibilityResult.PASS;
		if (!verbose && directionality == Directionality.BOTH && nuclToTarget < threshold)
			// shortcut
			return MultiRuptureCompatibilityResult.FAIL;
		float targetToNucl = (float)aggCalc.calc(targetSects, nuclSects);
		boolean result;
		switch (directionality) {
		case BOTH:
			result = nuclToTarget >= threshold && targetToNucl >= threshold;
			break;
		case EITHER:
			result = nuclToTarget >= threshold || targetToNucl >= threshold;
			break;
		case SUM:
			result = (nuclToTarget+targetToNucl) >= threshold;
			break;

		default:
			throw new IllegalStateException();
		}
		MultiRuptureCompatibilityResult ret = result ? MultiRuptureCompatibilityResult.PASS : MultiRuptureCompatibilityResult.FAIL;
		if (verbose) System.out.println("MultiRuptureCoulombFilter: "+ret.plausibilityResult
				+" (nuclToTarget="+nuclToTarget+", targetToNucl="+targetToNucl+")\n\tNucl: "+nucleation+"\n\tTarget: "+target);
		return ret;
	}

}
