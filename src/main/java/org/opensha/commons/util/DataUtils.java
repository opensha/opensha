/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.util;

import static com.google.common.base.Preconditions.*;
import static java.lang.Double.*;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.DefaultXY_DataSet;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Utilities for operating on {@code double}-valued data. This class: <ul>
 * <li>should probably be enhanced to work with {@code Double}
 * {@code Collection}s</li> <li>could be renamed to DoubleUtils or something
 * funny like Dubbles</li> </p>
 * 
 * <p>See {@link Doubles}  minimum, maximum, sum,
 * mean, product, etc... of {@code double} arrays as well as other
 * properties</p>
 * 
 * <p>Transformations of {@code double} arrays or {@code List}s may be
 * performed on empty data sets; {@code null} data sets throw an exception.</p>
 * 
 * <p>Class designed to reduce data copying hence why List variants do not
 * call toArray() and delegate to varargs variant.</p>
 * 
 * @author Peter Powers
 * @author Kevin Milner
 * @version $Id: DataUtils.java 11413 2016-10-14 19:02:45Z kmilner $
 */
public class DataUtils {

	private DataUtils() {}

	/*
	 * Some quick tests of abs() and scale() using a non-Function based approach
	 * and hence no autoboxing showed only marginal slowdown over a 10^8 sized
	 * array of random values. If profiling shows that in practice the function
	 * based approach of transforming arrays is slow, primitive implementations
	 * may be substituted. See DubblesTest for speed test.
	 * 
	 * Similarly, boolean tests such as isSorted() could be short-circuited to
	 * return at the first failure. However, there is more reusable code in the
	 * current implementation that is easier to follow. Again, this will may be
	 * changed if there is a demonstrable performance hit.
	 * 
	 * We could probably intern commonly used scale functions.
	 */
	
	private static final Range<Double> POS_RANGE = Range.open(0d,
		Double.POSITIVE_INFINITY);

	/**
	 * Returns the difference between {@code test} and {@code target}, relative
	 * to {@code target}, as a percent. If {@code target} is 0, method returns 0
	 * if {@code test} is also 0, otherwise {@code Double.POSITIVE_INFINITY}. If
	 * either value is {@code Double.NaN}, method returns {@code Double.NaN}.
	 * @param test value
	 * @param target value
	 * @return the percent difference
	 */
	public static double getPercentDiff(double test, double target) {
		if (isNaN(target) || isNaN(test)) return NaN;
		if (target == 0) return test == 0 ? 0 : POSITIVE_INFINITY;
		return Math.abs(test - target) / target * 100d;
	}

	/**
	 * Returns whether the supplied {@code data} are all positive.
	 * @param data to check
	 * @return {@code true} if all values are &ge;0
	 */
	public static boolean isPositive(double... data) {
		checkNotNull(data);
		checkArgument(data.length > 0, "data is empty");
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}
	
	/**
	 * Returns whether the supplied {@code data} are all positive.
	 * @param data to check
	 * @return {@code true} if all values are &ge;0
	 */
	public static boolean isPositive(List<Double> data) {
		checkNotNull(data);
		checkArgument(data.size() > 0, "data is empty");
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}
	
	/**
	 * Returns whether the elements of the supplied {@code data} increase or
	 * decrease monotonically, with a flag indicating if duplicate elements are
	 * permitted. The {@code repeats} flag could be {@code false} if checking
	 * the x-values of a function for any steps, or {@code true} if checking the
	 * y-values of a cumulative distribution function, which are commonly
	 * constant.
	 * @param ascending if {@code true}, descending if {@code false}
	 * @param repeats whether repeated adjacent elements are allowed
	 * @param data to validate
	 * @return {@code true} if monotonic, {@code false} otherwise
	 */
	public static boolean isMonotonic(boolean ascending, boolean repeats,
			double... data) {
		double[] diff = diff(data);
		if (!ascending) flip(diff);
		double min = Doubles.min(diff);
		return (repeats) ? min >= 0 : min > 0;
	}

	/**
	 * Returns the difference of adjacent elements in the supplied {@code data}.
	 * Method returns results in a new array that has {@code data.length - 1}
	 * where differences are computed per {@code data[i+1] - data[i]}.
	 * @param data to difference
	 * @return the differences between adjacent values
	 */
	public static double[] diff(double... data) {
		checkNotNull(data);
		checkArgument(data.length > 1);
		int size = data.length - 1;
		double[] diff = new double[size];
		for (int i = 0; i < size; i++) {
			diff[i] = data[i + 1] - data[i];
		}
		return diff;
	}
	
	/**
	 * Creates a sequence of values starting at {@code min} and ending at
	 * {@code max}, the log of which are evenly spaced.
	 * @param min sequence value
	 * @param max sequence value
	 * @param step sequence spacing
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return a monotonically increasing or decreasing sequence where the log
	 *         of the values are evenly spaced
	 * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0}
	 *         , or any arguments are {@code Double.NaN},
	 *         {@code Double.POSITIVE_INFINITY}, or
	 *         {@code Double.NEGATIVE_INFINITY}
	 * 
	 */
	public static double[] buildLogSequence(double min, double max,
			double step, boolean ascending) {
		double[] seq = buildSequence(Math.log(min), Math.log(max),
			Math.log(step), ascending);
		return exp(seq);
	}

	/**
	 * Creates a sequence of evenly spaced values starting at {@code min} and
	 * ending at {@code max}. If {@code (max - min) / step} is not integer
	 * valued, the last step in the sequence will be {@code &lt;step}.
	 * @param min sequence value
	 * @param max sequence value
	 * @param step sequence spacing
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return a monotonically increasing or decreasing sequence of values
	 * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0}
	 *         , or any arguments are {@code Double.NaN},
	 *         {@code Double.POSITIVE_INFINITY}, or
	 *         {@code Double.NEGATIVE_INFINITY}
	 */
	public static double[] buildSequence(double min, double max, double step,
			boolean ascending) {
		// if passed in arguments are NaN, +Inf, or -Inf, and step <= 0,
		// then capacity [c] will end up 0 because (int) NaN = 0, or outside the
		// range 1:10000
		checkArgument(min <= max, "min-max reversed");
		int c = (int) Math.floor((max - min) / step);
		checkArgument(c > 0 && c < MAX_SEQ_LEN, "sequence size");
		if (ascending) return buildSequence(min, max, step, c + 2);
		double[] descSeq = buildSequence(-max, -min, step, c + 2);
		return flip(descSeq);
	}

	private static final int MAX_SEQ_LEN = 10001;

	private static double[] buildSequence(double min, double max, double step,
			int capacity) {
		List<Double> seq = Lists.newArrayListWithCapacity(capacity);
		for (double val = min; val < max; val += step) {
			seq.add(val);
		}
		// TODO given the val < max check above, max will never be reached, therefore
		// we could simply blindly add max and skip the check below
		if (seq.get(seq.size() - 1) != max) seq.add(max);
		return Doubles.toArray(seq);
	}	

	/**
	 * Scales (multiplies) the elements of the supplied {@code data} in place
	 * by {@code value}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to scale
	 * @param value to scale by
	 * @return a reference to the supplied data
	 */
	public static double[] scale(double value, double... data) {
		return transform(new Scale(value), data);
	}

	/**
	 * Scales (multiplies) the elements of the supplied {@code List} in place
	 * by {@code value}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param list of {@code Double}s to scale
	 * @param value to scale by
	 * @return a reference to the supplied data
	 */
	public static List<Double> scale(double value, List<Double> list) {
		return transform(new Scale(value), list);
	}

	/**
	 * Adds the {@code value} to the supplied {@code data} in place.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underrun.</p>
	 * @param data to add to
	 * @param value to add
	 * @return a reference to the supplied data
	 */
	public static double[] add(double value, double... data) {
		return transform(new Add(value), data);
	}
	
	/**
	 * Adds the values of {@code data2} to {@code data1} and returns a reference
	 * to {@code data1}.
	 * @param data1 
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] add(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i=0; i<data1.length; i++) {
			data1[i] += data2[i];
		}
		return data1; 
	}
	
	/**
	 * Adds the values of {@code data2} to {@code data1} and returns a reference
	 * to {@code data1}.
	 * @param data1 
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> add(List<Double> data1, List<Double> data2) {
		checkArgument(checkNotNull(data1).size() == checkNotNull(data2).size());
		for (int i=0; i<data1.size(); i++) {
			data1.set(i, data1.get(i) + data2.get(i));
		}
		return data1;
	}

	/**
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] subtract(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i = 0; i < data1.length; i++) {
			data1[i] -= data2[i];
		}
		return data1;
	}

	/**
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> subtract(List<Double> data1, List<Double> data2) {
		checkArgument(checkNotNull(data1).size() == checkNotNull(data2).size());
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) - data2.get(i));
		}
		return data1;
	}

	/**
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] multiply(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/**
	 * Sets every element of the supplied {@code data} to its absolute value.
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] abs(double... data) {
		return transform(ABS, data);
	}
	
	/**
	 * Applies the exponential function to every element of the supplied 
	 * {@code data}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] exp(double... data) {
		return transform(EXP, data);
	}

	/**
	 * Applies the natural log function to every element of the supplied 
	 * {@code data}.
	 * 
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] ln(double... data) {
		return transform(LN, data);
	}

	/**
	 * Applies the base-10 log function to every element of the supplied 
	 * {@code data}.
	 * 
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] log(double... data) {
		return transform(LOG, data);
	}

	/**
	 * Flips the sign of every element in the supplied {@code data}.
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] flip(double... data) {
		return transform(new Scale(-1), data);
	}
	
	/**
	 * Returns the minimum of the supplied values. Method delegates to
	 * {@link Doubles#min(double...)}. Method returns {@code Double.NaN} if
	 * {@code data} contains {@code Double.NaN}.
	 * 
	 * @param data array to search
	 * @return the minimum of the supplied values
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @see Doubles#min(double...)
	 */
	public static double min(double... data) {
		return Doubles.min(data);
	}
	
	/**
	 * Returns the maximum of the supplied values. Method delegates to
	 * {@link Doubles#max(double...)}. Method returns {@code Double.NaN} if
	 * {@code data} contains {@code Double.NaN}.
	 * 
	 * @param data array to search
	 * @return the maximum of the supplied values
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @see Doubles#max(double...)
	 */
	public static double max(double... data) {
		return Doubles.max(data);
	}

	/**
	 * Returns the sum of the supplied values. Method returns {@code Double.NaN}
	 * if {@code data} contains {@code Double.NaN}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to add together
	 * @return the sum of the supplied values
	 */
	public static double sum(double... data) {
		checkNotNull(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}
	
	/**
	 * Returns the sum of the supplied values. Method returns {@code Double.NaN}
	 * if {@code data} contains {@code Double.NaN}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to add together
	 * @return the sum of the supplied values
	 */
	public static double sum(List<Double> data) {
		checkNotNull(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}

	public static void main(String[] args) {
//		double infTest = 1d/0;
//		System.out.println(infTest);
//		System.out.println(sum(new double[] {0,Double.NaN,2,3,4,5}));
//		System.out.println("===");
//		System.out.println(Double.POSITIVE_INFINITY + 3);
//		System.out.println(Double.NEGATIVE_INFINITY < 0);
//		System.out.println("===");
//		Range<Double> range = Range.open(0d, Double.POSITIVE_INFINITY);
//		System.out.println(range.contains(Double.POSITIVE_INFINITY));
		
		double[] vals = new double[] {4, 1e-6, -3.0, 8d, 0.0, 1e8, Double.POSITIVE_INFINITY};
		List<Double> data = Doubles.asList(vals);
		List<Integer> indices = sortedIndices(data, true);
		System.out.println(indices);
		
	}
	
	/**
	 * Converts the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * @param data to convert
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY)}
	 */
	public static double[] asWeights(double... data) {
		checkArgument(isPositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return scale(scale, data);
	}

	/**
	 * Converts the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * @param data to convert
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY)}
	 */
	public static List<Double> asWeights(List<Double> data) {
		checkArgument(isPositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return scale(scale, data);
	}

	/**
	 * Transforms the supplied {@code data} in place as per the supplied
	 * {@code function}'s {@link Function#apply(Object)} method.
	 * @param function to apply to data elements
	 * @param data to operate on
	 * @return a reference to the supplied {@code data} array
	 */
	private static double[] transform(Function<Double, Double> function,
			double... data) {
		checkNotNull(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = function.apply(data[i]);
		}
		return data;
	}	
	
	/**
	 * Transforms the supplied {@code data} in place as per the supplied
	 * {@code function}'s {@link Function#apply(Object)} method.
	 * @param function to apply to data elements
	 * @param data to operate on
	 * @return a reference to the supplied {@code data} array
	 */
	private static List<Double> transform(Function<Double, Double> function,
			List<Double> data) {
		checkNotNull(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, function.apply(data.get(i)));
		}
		return data;
	}	
	
	// @formatter:off
	
	private static final Function<Double, Double> ABS = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.abs(in); }
	}; 
	
	private static final Function<Double, Double> EXP = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.exp(in); }
	}; 

	private static final Function<Double, Double> LN = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.log(in); }
	}; 

	private static final Function<Double, Double> LOG = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.log10(in); }
	}; 

	private static class Scale implements Function<Double, Double> {
		private final double scale;
		private Scale(final double scale) { this.scale = scale; }
		@Override public Double apply(Double d) { return d * scale; }
	}

	private static class Add implements Function<Double, Double> {
		private final double term;
		private Add(final double term) { this.term = term; }
		@Override public Double apply(Double d) { return d + term; }
	}
	
	// @formatter:on

	
	/**
	 * Validates the domain of a {@code double} data set. Method verifies
	 * that data values all fall between {@code min} and {@code max} range
	 * (inclusive). Empty arrays are ignored. If {@code min} is
	 * {@code Double.NaN}, no lower limit is imposed; the same holds true
	 * for {@code max}. {@code Double.NaN} values in {@code array}
	 * will validate.
	 * 
	 * @param min minimum range value
	 * @param max maximum range value
	 * @param array to validate
	 * @throws IllegalArgumentException if {@code min > max}
	 * @throws IllegalArgumentException if any {@code array} value is out of
	 *         range
	 */
	public final static void validate(double min, double max, double... array) {
		checkNotNull(array, "array");
		for (int i = 0; i < array.length; i++) {
			validate(min, max, array[i]);
		}
	}
		
	/**
	 * Verifies that a {@code double} data value falls within a specified
	 * minimum and maximum range (inclusive). If {@code min} is 
	 * {@code Double.NaN}, no lower limit is imposed; the same holds true
	 * for {@code max}. A value of {@code Double.NaN} will always
	 * validate.
	 * 
	 * @param min minimum range value
	 * @param max minimum range value
	 * @param value to check
	 * @throws IllegalArgumentException if {@code min > max}
	 * @throws IllegalArgumentException if value is out of range
	 */
	public final static void validate(double min, double max, double value) {
		boolean valNaN = isNaN(value);
		boolean minNaN = isNaN(min);
		boolean maxNaN = isNaN(max);
		boolean both = minNaN && maxNaN;
		boolean neither = !(minNaN || maxNaN);
		if (neither) checkArgument(min <= max, "min-max reversed");
		boolean expression = valNaN || both ? true : minNaN
			? value <= max : maxNaN ? value >= min : value >= min &&
				value <= max;
		checkArgument(expression, "Value [%s] out of range [%s ,%s]", value, min, max);
	}
	
	/**
	 * Creates a new array from the values in a source array at the specified
	 * indices. Returned array is of same type as source.
	 * 
	 * @param array array source
	 * @param indices index values of items to select
	 * @return a new array of values at indices in source
	 * @throws NullPointerException if {@code array} or
	 *         {@code indices} are {@code null}
	 * @throws IllegalArgumentException if data object is not an array or if
	 *         data array is empty
	 * @throws IndexOutOfBoundsException if any indices are out of range
	 */
	public static Object arraySelect(Object array, int[] indices) {
		checkNotNull(array, "Supplied data array is null");
		checkNotNull(indices, "Supplied index array is null");
		checkArgument(array.getClass().isArray(),
			"Data object supplied is not an array");
		int arraySize = Array.getLength(array);
		checkArgument(arraySize != 0, "Supplied data array is empty");

		// validate indices
		for (int i = 0; i < indices.length; i++) {
			checkPositionIndex(indices[i], arraySize, "Supplied index");
		}

		Class<? extends Object> srcClass = array.getClass().getComponentType();
		Object out = Array.newInstance(srcClass, indices.length);
		for (int i = 0; i < indices.length; i++) {
			Array.set(out, i, Array.get(array, indices[i]));
		}
		return out;
	}

	/**
	 * Sorts the supplied data array in place and returns an {@code int[]}
	 * array of the original indices of the data values. For example, if the
	 * supplied array is [3, 1, 8], the supplied array will be sorted to [1, 3,
	 * 8] and the array [2, 1, 3] will be returned.
	 * 
	 * @param data array to sort
	 * @return the inidices of the unsorted array values
	 * @throws NullPointerException if source array is {@code null}
	 */
	@Deprecated
	public static int[] indexAndSort(final double[] data) {
		checkNotNull(data, "Source array is null");
		List<Integer> indices = Ints.asList(new int[data.length]);
		for (int i = 0; i < indices.size(); i++) {
			indices.set(i, i);
		}
		Collections.sort(indices, new Comparator<Integer>() {
			@Override
			public int compare(Integer i1, Integer i2) {
				double d1 = data[i1];
				double d2 = data[i2];
				return (d1 < d2) ? -1 : (d1 == d2) ? 0 : 1;
			}
		});
		Arrays.sort(data);
		return Ints.toArray(indices);
	}
	
	/**
	 * Returns an index {@code List} that provides a pointers to sorted
	 * {@code data}. Let's say you have a number of {@code List<Double>}s and
	 * want to sort them all according to one of your choosing. Supply this
	 * method with the desired {@code data} and use the returned indices view
	 * any of your arrays according to the sort order of the supplied
	 * {@code data}.
	 * 
	 * <p> <b>Notes:</b> <ul> <li>The supplied data should not be sorted</li>
	 * <li>This method does not modify the supplied {@code data} in any way</li>
	 * <li>Any {@code NaN}s in {@code data} are placed at the start of the sort
	 * order, regardless of sort direction</li> <ul> </p>
	 * 
	 * @param data to provide sort indices for
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return an index {@code List}
	 */
	public static List<Integer> sortedIndices(List<Double> data,
			boolean ascending) {
		checkNotNull(data);
		List<Integer> indices = Ints.asList(indices(data.size()));
		Collections.sort(indices, new IndexComparator(data, ascending));
		return indices;
	}
	
	/*
	 * A comparator for ascending sorting of an index array based on the
	 * supplied double array of data.
	 */
	private static class IndexComparator implements Comparator<Integer> {
		List<Double> data;
		boolean ascending;
		IndexComparator(List<Double> data, boolean ascending) {
			this.data = data;
			this.ascending = ascending;
		}
		@Override
		public int compare(Integer i1, Integer i2) {
			double d1 = data.get(ascending ? i1 : i2);
			double d2 = data.get(ascending ? i2 : i1);
			return (d1 < d2) ? -1 : (d1 == d2) ? 0 : 1;
		}
	}
	
	/**
	 * Returns an {@code int[]} of values ascending from {@code 0} to
	 * {@code 1-length} that can be used for sorting.
	 * @param length
	 * @return an index array
	 * @see DataUtils#sortedIndices(List, boolean)
	 */
	public static int[] indices(int length) {
		int[] indices = new int[length];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = i;
		}
		return indices;
	}

    /**
     * Creates an array of random {@code double} values.
     * @param length of output array
     * @return the array of random {@code double}s
     */
    public static double[] randomValues(int length) {
    	Random random = new Random();
        double[] values = new double[length];
        for (int i=0; i<length; i++) {
        	values[i] = random.nextDouble();
        }
        return values;
    }
    
    /**
     * Returns the index of the minimum value in {@code data}.
     * @param data
     * @return the index of the minimum value
     */
    public static int minIndex(double... data) {
        int idx = -1;
        double d = Double.POSITIVE_INFINITY;
        for(int i = 0; i < data.length; i++)
            if(data[i] < d) {
                d = data[i];
                idx = i;
            }
        return idx;
    }

    /**
     * Returns the index of the maximum value in {@code data}.
     * @param data
     * @return the index of the maximum value
     */
    public static int maxIndex(double... data) {
        int idx = -1;
        double d = Double.NEGATIVE_INFINITY;
        for(int i = 0; i < data.length; i++)
            if(data[i] > d) {
                d = data[i];
                idx = i;
            }
        return idx;
    }

    /**
	 * Nearest neighbor binning algorithm after Silverman, B. W. (1986),
	 * <em>Density Estimation for Statistics and Data Analysis</em>, Chapman
	 * &amp; Hall, New York. This method is a density estimator that uses
	 * variable width binning with a fixed sample size per bin that better
	 * reflects the distribution of the underlying data. It is particularly
	 * useful when workgin with power-law distributed data. Bin widths are
	 * computed as the difference between the last values in adjacent bins. In
	 * the case of the 1st bin, the supplied origin is taken as the "last value"
	 * of the previous bin. Bin positions are set from the median value in each
	 * bin. Note that the supplied {@code data} is not modified; this
	 * method uses a copy internally. In most cases, data will be fairly
	 * continuous in X, however, for small {@code size}s it's possible to
	 * have bins of identical values such that corresponding bin value is
	 * Infinity. Such values are not included in the resultant data set.
	 * 
	 * @param data to be binned (does not need to be sorted)
	 * @param origin for binning
	 * @param size of each bin
	 * @return an {@code XY_DataSet} of the binned distribution or
	 *         {@code null} if the binned distribution is empty
	 * @throws NullPointerException if the supplied {@code data} is
	 *         {@code null}
	 * @throws IllegalArgumentException if supplied {@code data} is empty,
	 *         the bin {@code size} is &lt;1, or the {@code origin} is
	 *         greater than all {@code data} values
	 */
	public static DefaultXY_DataSet nearestNeighborHist(double[] data, double origin,
			int size) {
		checkNotNull(data, "Supplied data is null");
		checkArgument(data.length > 0, "Supplied data is empty");
		checkArgument(size > 0, "Bin size can't be less than 1");
		double[] localData = Arrays.copyOf(data, data.length);
		Arrays.sort(localData);
		int startIdx = Arrays.binarySearch(localData, origin);
		checkArgument(startIdx < localData.length,
			"Origin is greater than all data values");
		startIdx = (startIdx > 0) ? startIdx : -startIdx - 1;
		// for multipe identical values, binary search may not return
		// the lowest index so walk down
		while (startIdx > 0 && origin == localData[startIdx-1]) startIdx--;
		// trim data
		localData = Arrays.copyOfRange(localData, startIdx, localData.length);
		int binCount = (int) Math.floor(localData.length / size);
		// bail on an empty distribution
		if (binCount == 0) return null;
		List<Double> x = new ArrayList<Double>();
		List<Double> y = new ArrayList<Double>();
		double binLo, binHi, binDelta;
		for (int i = 0; i < binCount; i++) {
			int datIdx = i * size;
			binLo = (i == 0) ? origin : localData[datIdx-1];
			binHi = localData[datIdx + size - 1];
			binDelta = binHi - binLo;
			// bail on intervals of identical values
			if (binDelta == 0) continue;
			y.add(size / (binHi - binLo));
			x.add(StatUtils.percentile(localData, datIdx, size, 50.0));
		}
		// bail on empty distribution
		return (x.isEmpty()) ? null : new DefaultXY_DataSet(x, y);
	}


	// TODO test; instances should be replaced with statistical summaries
	/**
	 * Class for tracking the minimum and maximum values of a set of data.
	 */
	public static class MinMaxAveTracker {
		private double min = Double.POSITIVE_INFINITY;
		private double max = Double.NEGATIVE_INFINITY;
		private double tot = 0;
		private int num = 0;

		/**
		 * Add a new value to the tracker. Min/Max/Average will be updated.
		 * 
		 * @param val value to be added
		 */
		public void addValue(double val) {
			if (val < min) min = val;
			if (val > max) max = val;
			tot += val;
			num++;
		}
		
		public void addFrom(MinMaxAveTracker o) {
			min = Math.min(min, o.min);
			max = Math.max(max, o.max);
			tot += o.tot;
			num += o.num;
		}

		/**
		 * Returns the minimum value that has been added to this tracker, or positive infinity if
		 * no values have been added.
		 * 
		 * @return minimum value
		 */
		public double getMin() {
			return min;
		}

		/**
		 * Returns the maximum value that has been added to this tracker, or negative infinity if
		 * no values have been added.
		 * 
		 * @return maximum value
		 */
		public double getMax() {
			return max;
		}
		
		/**
		 * Returns the length of the range
		 * 
		 * @return getMax()-getMin()
		 */
		public double getLength() {
			return max-min;
		}

		/**
		 * Computes the average of all values that have been added to this tracker.
		 * 
		 * @return the average of all values that have been added to this tracker.
		 */
		public double getAverage() {
			return tot / (double) num;
		}

		/**
		 * 
		 * @return total number of values added to this tracker.
		 */
		public int getNum() {
			return num;
		}
		
		/**
		 * 
		 * @return sum of all values added to this tracker
		 */
		public double getSum() {
			return tot;
		}

		@Override
		public String toString() {
			return "min: " + min + ", max: " + max + ", avg: " + getAverage() + ", tot: "+tot;
		}
	}
	
	/**
	 * 
	 * @param unsorted
	 * @return median of the array of values. if values are already sorted, use median_sorted
	 */
	public static double median(double[] unsorted) {
		double[] sorted = Arrays.copyOf(unsorted, unsorted.length);
		Arrays.sort(sorted);
		return median_sorted(sorted);
	}
	
	/**
	 * @param sorted
	 * @return median of the sorted array of values
	 */
	public static double median_sorted(double[] sorted) {
		Preconditions.checkState(sorted.length > 0, "must have at least one value");
		if (sorted.length % 2 == 1)
			return sorted[(sorted.length+1)/2-1];
		else
		{
			double lower = sorted[sorted.length/2-1];
			double upper = sorted[sorted.length/2];

			return (lower + upper) * 0.5;
		}	
	}
	
	public static List<Double> commaDoubleSplit(String str) {
		ArrayList<Double> vals = new ArrayList<Double>();
		
		for (String val : commaSplit(str)) {
			vals.add(Double.parseDouble(val));
		}
		
		return vals;
	}
	
	public static List<String> commaSplit(String str) {
		str = str.trim();
		ArrayList<String> vals = new ArrayList<String>();
		for (String val : str.split(",")) {
			val = val.trim();
			vals.add(val);
		}
		return vals;
	}
	
	/**
	 * Round a double to a specified number of decimal places according to
	 * {@link RoundingMode#HALF_UP}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Note that a negative
	 * {@code scale} will round {@code value} to the specified number of places
	 * above the decimal. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param scale the number of decimal places in the result
	 */
	public static double roundFixed(double value, int scale) {
		return roundFixed(value, scale, RoundingMode.HALF_UP);
	}
	
	/**
	 * Round a double to a specified number of decimal places according to the
	 * supplied {@link RoundingMode}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Note that a negative
	 * {@code scale} will round {@code value} to the specified number of places
	 * above the decimal. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param scale the number of decimal places in the result
	 * @param mode rounding mode to use
	 */
	public static double roundFixed(double value, int scale, RoundingMode mode) {
		if (!Double.isFinite(value))
			return value;
		return BigDecimal.valueOf(value).setScale(scale, mode).doubleValue();
	}
	
	/**
	 * Round a double to a specified number of significant figures according to
	 * {@link RoundingMode#HALF_UP}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param sigFigs the number of significant figures in the result
	 */
	public static double roundSigFigs(double value, int sigFigs) {
		return roundSigFigs(value, sigFigs, RoundingMode.HALF_UP);
	}
	
	/**
	 * Round a double to a specified number of decimal places according to the
	 * supplied {@link RoundingMode}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Note that a negative
	 * {@code scale} will round {@code value} to the specified number of places
	 * above the decimal. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param sigFigs the number of significant figures in the result
	 * @param mode rounding mode to use
	 */
	public static double roundSigFigs(double value, int sigFigs, RoundingMode mode) {
		if (!Double.isFinite(value))
			return value;
		return BigDecimal.valueOf(value).round(new MathContext(sigFigs, mode)).doubleValue();
	}
	
	/**
	 * Round a double to a specified number of significant figures according to
	 * {@link RoundingMode#HALF_UP}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param sigFigs the number of significant figures in the result
	 */
	public static double[] roundSigFigs(double[] values, int sigFigs) {
		return roundSigFigs(values, sigFigs, RoundingMode.HALF_UP);
	}
	
	/**
	 * Round a double to a specified number of decimal places according to the
	 * supplied {@link RoundingMode}. Internally this method uses the scaling and
	 * rounding capabilities of {@link BigDecimal}. Non-finite values will be returned as is.
	 *
	 * @param value to round
	 * @param sigFigs the number of significant figures in the result
	 * @param mode rounding mode to use
	 */
	public static double[] roundSigFigs(double[] values, int sigFigs, RoundingMode mode) {
		MathContext context = new MathContext(sigFigs, mode);
		double[] ret = new double[values.length];
		for (int i=0; i<ret.length; i++) {
			if (Double.isFinite(values[i]))
				ret[i] = BigDecimal.valueOf(values[i]).round(context).doubleValue();
			else
				ret[i] = values[i];
		}
		return ret;
	}

}
