package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import java.util.Comparator;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Interface for a plausibility filter which is produces a scalar value (e.g., integer, double, float)
 * for each rupture. This is mostly a helper interface to allow diagnostic plots when comparing rupture
 * sets.
 * 
 * @author kevin
 *
 * @param <E>
 */
public interface ScalarValuePlausibiltyFilter<E extends Number & Comparable<E>> extends PlausibilityFilter {
	
	/**
	 * @param rupture
	 * @return scalar value for the given rupture
	 */
	public E getValue(ClusterRupture rupture);
	
	/**
	 * @return acceptable range of values, or null if rules are more complex
	 */
	public Range<E> getAcceptableRange();
	
	/**
	 * @return name of this scalar value
	 */
	public String getScalarName();
	
	/**
	 * @return units of this scalar value
	 */
	public String getScalarUnits();
	
	/**
	 * @param val1
	 * @param val2
	 * @return the value that is worse. see isValueBetter for implementation details
	 */
	public default E getWorseValue(E val1, E val2) {
		if (isValueBetter(val1, val2))
			return val2;
		return val1;
	}

	/**
	 * 
	 * @return a comparator to sort values from worst to best (see isValueBetter for implementation details)
	 */
	public default Comparator<E> worstToBestComparator() {
		return new Comparator<E>() {

			@Override
			public int compare(E val1, E val2) {
				if (val1.equals(val2))
					return 0;
				if (isValueBetter(val1, val2))
					return 1;
				return -1;
			}
		};
	}
	
	/**
	 * 
	 * @param val1
	 * @param val2
	 * @return the better of the two given values. see isValueBetter for implementation details
	 */
	public default E getBestValue(E val1, E val2) {
		if (isValueBetter(val1, val2))
			return val1;
		return val2;
	}
	
	/**
	 * This tests two values to determine if the test value (argument 1) is better than the reference value (argument 2).
	 * 
	 * It starts by computing the distance (to double precision) from the acceptable range for each value. This will be 0
	 * if the acceptable range contains the value. If the distance for either value is nonzero, then the value with the
	 * smallest distance is taken to be the better value. If that distance is zero for both values, then the algorithm
	 * is as follows: if there is an upper bound, choose the smallest value as best. if there is no upper bound, choose
	 * the largest
	 * 
	 * @param testVal
	 * @param refVal
	 * @range
	 * @return true if testVal is better than refVal
	 */
	public default boolean isValueBetter(E testVal, E refVal) {
		return isValueBetter(testVal, refVal, getAcceptableRange());
	}
	
	/**
	 * This tests two values to determine if the test value (argument 1) is better than the reference value (argument 2).
	 * 
	 * It starts by computing the distance (to double precision) from the acceptable range for each value. This will be 0
	 * if the acceptable range contains the value. If the distance for either value is nonzero, then the value with the
	 * smallest distance is taken to be the better value. If that distance is zero for both values, then the algorithm
	 * is as follows: if there is an upper bound, choose the smallest value as best. if there is no upper bound, choose
	 * the largest
	 * 
	 * @param testVal
	 * @param refVal
	 * @param range
	 * @return true if testVal is better than refVal
	 */
	public static <E extends Number & Comparable<E>> boolean isValueBetter(E testVal, E refVal, Range<E> range) {
		double dist1 = distFromRange(testVal, range);
		double dist2 = distFromRange(refVal, range);
		if ((float)dist1 == 0f && (float)dist2 == 0f) {
			// both are acceptable, choose better
			if (!range.hasLowerBound())
//				return testVal < refVal;
				return testVal.compareTo(refVal) < 0;
			return testVal.compareTo(refVal) > 0;
		}
		if (dist1 < dist2)
			return true;
		return false;
	}
	
	/**
	 * The distance (always positive) of the given value from the given range, or zero if the range contains the value.
	 * @param val
	 * @param range
	 * @return
	 */
	public static <E extends Number & Comparable<E>> double distFromRange(E val, Range<E> range) {
		if (range.contains(val))
			return 0d;
		if (range.hasUpperBound() && val.compareTo(range.upperEndpoint()) > 0)
			return val.doubleValue() - range.upperEndpoint().doubleValue();
		return range.lowerEndpoint().doubleValue() - val.doubleValue();
	}
	
	/**
	 * 
	 * @return string representation of the acceptable range
	 */
	public default String getRangeStr() {
		return getRangeStr(getAcceptableRange());
	}
	
	/**
	 * 
	 * @return string representation of the acceptable range
	 */
	public static <E extends Number & Comparable<E>> String getRangeStr(Range<E> acceptableRange) {
		Preconditions.checkState(acceptableRange.hasLowerBound() || acceptableRange.hasUpperBound());
		if (!acceptableRange.hasLowerBound()) {
			E upper = acceptableRange.upperEndpoint();
			char ineq = acceptableRange.upperBoundType() == BoundType.CLOSED ? '\u2264' : '<'; // u2264 is less than or equal
			if (upper.floatValue() == 0f)
				return ineq+"0";
			return ineq+(upper+"");
		} else if (!acceptableRange.hasUpperBound()) {
			E lower = acceptableRange.lowerEndpoint();
			char ineq = acceptableRange.lowerBoundType() == BoundType.CLOSED ? '\u2265' : '>'; // u2265 is greater than or equal
			if (lower.floatValue() == 0f)
				return ineq+"0";
			return ineq+(lower+"");
		}
		String ret = "∈";
		ret += (acceptableRange.lowerBoundType() == BoundType.CLOSED) ? "[" : "(";
		ret += acceptableRange.lowerEndpoint()+","+acceptableRange.upperEndpoint();
		ret += (acceptableRange.upperBoundType() == BoundType.CLOSED) ? "]" : ")";
		return ret;
	}
	
	public static class DoubleWrapper implements ScalarValuePlausibiltyFilter<Double> {
		
		private ScalarValuePlausibiltyFilter<?> filter;
		private Range<Double> doubleRange;

		public DoubleWrapper(ScalarValuePlausibiltyFilter<?> filter) {
			this.filter = filter;
			Range<?> origRange = filter.getAcceptableRange();
			if (origRange.hasLowerBound() && origRange.hasUpperBound())
				doubleRange = Range.range(numberToDouble(origRange.lowerEndpoint()), origRange.lowerBoundType(),
						numberToDouble(origRange.upperEndpoint()), origRange.upperBoundType());
			else if (origRange.hasLowerBound())
				doubleRange = Range.downTo(numberToDouble(origRange.lowerEndpoint()), origRange.lowerBoundType());
			else
				doubleRange = Range.upTo(numberToDouble(origRange.upperEndpoint()), origRange.upperBoundType());
		}
		
		private double numberToDouble(Object obj) {
			return ((Number)obj).doubleValue();
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			return filter.apply(rupture, verbose);
		}

		@Override
		public String getShortName() {
			return filter.getShortName();
		}

		@Override
		public String getName() {
			return filter.getName();
		}

		@Override
		public Double getValue(ClusterRupture rupture) {
			Number value = filter.getValue(rupture);
			if (value == null)
				return null;
			return value.doubleValue();
		}

		@Override
		public Range<Double> getAcceptableRange() {
			return doubleRange;
		}

		@Override
		public String getScalarName() {
			return filter.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			return filter.getScalarUnits();
		}
		
	}

}
