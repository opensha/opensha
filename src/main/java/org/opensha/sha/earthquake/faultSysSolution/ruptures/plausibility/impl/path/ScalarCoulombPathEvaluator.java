package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.collect.Range;

/**
 * 
 * Interface for a Coulomb path evaluator
 * 
 * @author kevin
 *
 */
public abstract class ScalarCoulombPathEvaluator extends PathEvaluator.Scalar<Float> {

	protected AggregatedStiffnessCalculator aggCalc;

	public ScalarCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			PlausibilityResult failureType) {
		super(acceptableRange, failureType);
		this.aggCalc = aggCalc;
	}
	
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

	@Override
	public String getScalarName() {
		return aggCalc.getScalarName();
	}

	@Override
	public String getScalarUnits() {
		if (aggCalc.hasUnits())
			return aggCalc.getType().getUnits();
		return null;
	}
	
}