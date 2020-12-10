package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

public interface ScalarCoulombPlausibilityFilter extends ScalarValuePlausibiltyFilter<Float> {
	
	public AggregatedStiffnessCalculator getAggregator();
	
	public default String getScalarName() {
		return getAggregator().getScalarName();
	}
	
	public default String getScalarUnits() {
		if (getAggregator().hasUnits())
			return getAggregator().getType().getUnits();
		return null;
	}

}
