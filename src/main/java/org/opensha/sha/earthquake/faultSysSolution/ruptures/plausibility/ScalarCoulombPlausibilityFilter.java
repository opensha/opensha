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
	
	public default float getWorseValue(float val1, float val2) {
		if (isValueBetter(val1, val2))
			return val2;
		return val1;
	}
	
	public default Comparator<Float> worstToBestComparator() {
		final Range<Float> range = getAcceptableRange(); 
		return new Comparator<Float>() {

			@Override
			public int compare(Float val1, Float val2) {
				if (val1.equals(val2))
					return 0;
				if (isValueBetter(val1, val2))
					return 1;
				return -1;
			}
		};
	}
	
	public default float getBestValue(float val1, float val2) {
		if (isValueBetter(val1, val2))
			return val1;
		return val2;
	}
	
	public default boolean isValueBetter(float testVal, float refVal) {
		Range<Float> range = getAcceptableRange();
		double dist1 = distFromRange(testVal, range);
		double dist2 = distFromRange(refVal, range);
		if (dist1 == 0f && dist2 == 0f) {
			// both are acceptable, choose better
			if (range.hasUpperBound())
				return testVal < refVal;
			return testVal > refVal;
		}
		if (dist1 < dist2)
			return true;
		return false;
	}
	
	static float distFromRange(float val, Range<Float> range) {
		if (range.contains(val))
			return 0f;
		if (range.hasUpperBound() && val > range.upperEndpoint())
			return val - range.upperEndpoint();
		return range.lowerEndpoint() - val;
	}
	
	public default String getRangeStr() {
		Range<Float> acceptableRange = getAcceptableRange();
		Preconditions.checkState(acceptableRange.hasLowerBound() || acceptableRange.hasUpperBound());
		if (!acceptableRange.hasLowerBound()) {
			float upper = acceptableRange.upperEndpoint();
			char ineq = acceptableRange.upperBoundType() == BoundType.CLOSED ? '≤' : '<';
			if (upper == 0f)
				return ineq+"0";
			return ineq+(upper+"");
		} else if (!acceptableRange.hasUpperBound()) {
			float lower = acceptableRange.lowerEndpoint();
			char ineq = acceptableRange.lowerBoundType() == BoundType.CLOSED ? '≥' : '>';
			if (lower == 0f)
				return ineq+"0";
			return ineq+(lower+"");
		}
		String ret = "∈";
		ret += (acceptableRange.lowerBoundType() == BoundType.CLOSED) ? "[" : "(";
		ret += acceptableRange.lowerEndpoint()+","+acceptableRange.upperEndpoint();
		ret += (acceptableRange.upperBoundType() == BoundType.CLOSED) ? "]" : ")";
		return ret;
	}

}
