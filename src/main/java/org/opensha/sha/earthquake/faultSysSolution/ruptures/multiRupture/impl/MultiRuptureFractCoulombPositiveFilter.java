package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

/**
 * Version of {@link MultiRuptureCoulombFilter} that is specific to the fraction of positive Coulomb interactions
 */
public class MultiRuptureFractCoulombPositiveFilter extends MultiRuptureCoulombFilter {
	
	public MultiRuptureFractCoulombPositiveFilter(SubSectStiffnessCalculator stiffnessCalc, float threshold) {
		this(stiffnessCalc, threshold, Directionality.EITHER);
	}
	
	public MultiRuptureFractCoulombPositiveFilter(SubSectStiffnessCalculator stiffnessCalc, float threshold,
			Directionality directionality) {
		super(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT),
				threshold, directionality);
	}

}
