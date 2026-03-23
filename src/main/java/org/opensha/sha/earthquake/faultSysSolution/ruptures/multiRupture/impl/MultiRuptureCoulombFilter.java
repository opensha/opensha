package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
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

	public AggregatedStiffnessCalculator getAggCalc() {
		return aggCalc;
	}

	public float getThreshold() {
		return threshold;
	}

	public List<FaultSection> getSects(ClusterRupture rupture) {
		List<FaultSection> fromSects = new ArrayList<>(rupture.getTotalNumSects());
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			fromSects.addAll(cluster.subSects);
		return fromSects;
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
	
	/**
	 * Pre-cache stiffness in parallel all sections in the given fromRup to all sections in any of the given list of
	 * toRups
	 * 
	 * @param fromRup
	 * @param toRups
	 * @param distAzCalc
	 * @param maxDist
	 */
	public void parallelCacheStiffness(ClusterRupture fromRup, List<ClusterRupture> toRups,
			SectionDistanceAzimuthCalculator distAzCalc, double maxDist) {
		List<FaultSection> fromSects = new ArrayList<>(fromRup.getTotalNumSects());
		for (FaultSubsectionCluster cluster : fromRup.getClustersIterable())
			fromSects.addAll(cluster.subSects);
		HashSet<FaultSection> toSects = new HashSet<>();
		for (ClusterRupture toRup : toRups) {
			boolean withinDist = false;
			for (FaultSubsectionCluster cluster : toRup.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					for (FaultSection fromSect : fromSects) {
						if (distAzCalc.getDistance(fromSect, sect) <= maxDist) {
							withinDist = true;
							break;
						}
					}
				}
			}
			if (withinDist)
				for (FaultSubsectionCluster cluster : toRup.getClustersIterable())
					toSects.addAll(cluster.subSects);
		}
		parallelCacheStiffness(fromSects, toSects);
	}
	
	/**
	 * Pre-cache stiffness in parallel between all sections of any fromRup to all sections of any toRup. This will likely
	 * calculate some that are not needed, but it's tricky to figure out the distinct list of actually used section pairs
	 * @param fromRups
	 * @param toRups
	 */
	public void parallelCacheStiffness(List<ClusterRupture> fromRups, List<ClusterRupture> toRups) {
		HashSet<FaultSection> fromSects = new HashSet<>();
		for (ClusterRupture fromRup : fromRups)
			for (FaultSubsectionCluster cluster : fromRup.getClustersIterable())
				fromSects.addAll(cluster.subSects);
		HashSet<FaultSection> toSects = new HashSet<>();
		for (ClusterRupture toRup : toRups)
			for (FaultSubsectionCluster cluster : toRup.getClustersIterable())
				toSects.addAll(cluster.subSects);
		parallelCacheStiffness(fromSects, toSects);
	}
	
	/**
	 * Pre-calculates (and thus caches) stiffness between each of the given source and receiver sections. Can be useful
	 * to speed up merging operations.
	 * 
	 * @param fromSects
	 * @param toSects
	 */
	public void parallelCacheStiffness(Collection<? extends FaultSection> fromSects,
			Collection<? extends FaultSection> toSects) {
		System.out.println("Pre-calculating stiffness between "+fromSects.size()+" source and "+toSects.size()+" receiver sections.");
		fromSects.parallelStream().flatMap(fromSect -> toSects.parallelStream()
				.map(toSect -> new FaultSectionPair(fromSect, toSect)))
				.forEach(pair -> aggCalc.calc(pair.fromSect, pair.toSect));
		System.out.println("Pre-calculating reversed stiffness between "+toSects.size()+" source and "+fromSects.size()+" receiver sections.");
		fromSects.parallelStream().flatMap(fromSect -> toSects.parallelStream()
				.map(toSect -> new FaultSectionPair(fromSect, toSect)))
				.forEach(pair -> aggCalc.calc(pair.toSect, pair.fromSect));
		System.out.println("DONE Pre-calculating stiffness.");
	}
	
	// A helper class to hold pairs of FaultSections
    private static class FaultSectionPair {
        FaultSection fromSect;
        FaultSection toSect;

        FaultSectionPair(FaultSection fromSect, FaultSection toSect) {
            this.fromSect = fromSect;
            this.toSect = toSect;
        }
    }

	public double[] statsData(MultiRuptureJump jump) {
		List<FaultSection> fromSections = jump.fromRupture.buildOrderedSectionList();
		List<FaultSection> toSections = jump.toRupture.buildOrderedSectionList();
		List<FaultSection> allSections = new ArrayList<>(fromSections);
		allSections.addAll(toSections);

		double from = aggCalc.calc(allSections, fromSections);
		double to = aggCalc.calc(allSections, toSections);
		//double self = aggCalc.calc(allSections, allSections);

		return new double[]{from, to};
	}

}
