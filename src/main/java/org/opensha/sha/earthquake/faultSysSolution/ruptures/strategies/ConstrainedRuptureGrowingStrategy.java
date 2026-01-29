package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Growing strategy that takes an upstream (likely-exhaustive) strategy and filters it according to the given list of
 * {@link VariationConstraint}.
 */
public class ConstrainedRuptureGrowingStrategy implements RuptureGrowingStrategy {

	private RuptureGrowingStrategy exhaustiveStrategy;
	private String name;
	private List<VariationConstraint> constraints;

	/**
	 * Constraint interface used to filter candidate variations
	 */
	public static interface VariationConstraint {
		
		/**
		 * 
		 * @param candidate candidate variation
		 * @param fullCluster full cluster for this fault section
		 * @param existingRupture existing rupture to which the candidate would be added, or null if none
		 * @return true if the candidate passes this constraint
		 */
		boolean apply(FaultSubsectionCluster candidate, FaultSubsectionCluster fullCluster,
				ClusterRupture existingRupture);
	}
	
	public ConstrainedRuptureGrowingStrategy(RuptureGrowingStrategy exhaustiveStrategy, String name, VariationConstraint... constraints) {
		this(exhaustiveStrategy, name, List.of(constraints));
	}
	
	public ConstrainedRuptureGrowingStrategy(RuptureGrowingStrategy exhaustiveStrategy, String name, List<VariationConstraint> constraints) {
		this.exhaustiveStrategy = exhaustiveStrategy;
		this.name = name;
		this.constraints = constraints;
		
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		return getVariations(null, fullCluster, firstSection);
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(ClusterRupture currentRupture, FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		List<FaultSubsectionCluster> ret = new ArrayList<>();
		for (FaultSubsectionCluster candidate : exhaustiveStrategy.getVariations(fullCluster, firstSection)) {
			boolean pass = true;
			for (VariationConstraint constraint : constraints) {
				if (!constraint.apply(candidate, fullCluster, null)) {
					pass = false;
					break;
				}
			}
			if (pass)
				ret.add(candidate);
		}
		
		return ret;
	}

}
