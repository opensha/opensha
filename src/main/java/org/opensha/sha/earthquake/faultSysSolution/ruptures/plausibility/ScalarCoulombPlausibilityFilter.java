package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

public interface ScalarCoulombPlausibilityFilter extends ScalarValuePlausibiltyFilter<Float> {
	
	public SubSectStiffnessCalculator getStiffnessCalc();
	
	public default String getScalarName() {
		return StiffnessType.CFF.getName();
	}
	
	public default String getScalarUnits() {
		return StiffnessType.CFF.getUnits();
	}

}
