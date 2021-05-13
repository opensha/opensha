package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import java.util.Comparator;

import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

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
