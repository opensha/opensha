package org.opensha.commons.data;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import org.apache.commons.math3.util.Precision;
import org.dom4j.Element;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;

public class WeightedList<E> extends AbstractList<WeightedValue<E>> implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "WeightedList";
	
	protected List<WeightedValue<E>> list;
	
	private boolean forceNormalization = false;
	
	private double weightValueMin = 0;
	private double weightValueMax = 1;
	
	private IntegerPDF_FunctionSampler sampler = null;
	
	public static class Unmodifiable<E> extends WeightedList<E> {
		
		public Unmodifiable(WeightedList<E> list) {
			super(list, false);
		}
		
		public Unmodifiable(List<WeightedValue<E>> list, boolean validate) {
			super(list, validate);
		}

		@Override
		public void add(E object, double weight) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeights(List<Double> newWeights) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setValues(List<E> values) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeight(int i, double weight) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAll(List<WeightedValue<E>> list) {
			if (this.list == null) {
				super.setAll(list);
			} else {
				throw new UnsupportedOperationException();
			}
		}

		@Override
		public void setAll(List<E> objects, List<Double> weights) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public WeightedValue<E> set(int index, WeightedValue<E> element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(int index, WeightedValue<E> element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeightsEqual() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeightsToConstant(double weight) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void normalize() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setForceNormalization(boolean forceNormalization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeightValueMin(double weightValueMin) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeightValueMax(double weightValueMax) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setWeightsFromXMLMetadata(Element el) {
			throw new UnsupportedOperationException();
		}
		
	}

	/**
	 * Convenience method for pre-specified evenly-weighted values. Note that, when using this method,
	 * the returned list is unmodifiable.
	 * 
	 * @param evenlyWeighted varargs of evenly-weighted values
	 */
	@SafeVarargs
	public static <E> Unmodifiable<E> evenlyWeighted(E... values) {
		List<WeightedValue<E>> list;
		switch (values.length) {
		// more memory-efficient common special cases
		case 0:
			list = List.of();
			break;
		case 1:
			list = List.of(
					new WeightedValue<>(values[0], 1d));
			break;
		case 2:
			list = List.of(
					new WeightedValue<>(values[0], 0.5d),
					new WeightedValue<>(values[1], 0.5d));
			break;

		// default case when size > 2
		default:
			list = new ArrayList<>(values.length);
			double weightEach = 1d/(double)values.length;
			for (E val : values)
				list.add(new WeightedValue<>(val, weightEach));
			list = Collections.unmodifiableList(list);
			break;
		}
		
		return new Unmodifiable<>(list, false);
	}
	
	/**
	 * Convenience method for pre-specified varags values. Note that, when using this method,
	 * the returned list is unmodifiable.
	 * 
	 * @param values varargs of values
	 */
	@SafeVarargs
	public static <E> Unmodifiable<E> of(WeightedValue<E>... values) {
		return new Unmodifiable<>(List.of(values), false);
	}
	
	public WeightedList(List<WeightedValue<E>> list) {
		setAll(list);
	}
	
	private WeightedList(List<WeightedValue<E>> list, boolean validate) {
		setAll(list, validate);
	}
	
	public WeightedList(int initialCapacity) {
		setAll(new ArrayList<>(initialCapacity), false);
	}
	
	public WeightedList() {
		setAll(new ArrayList<>(), false);
	}
	
	public WeightedList(List<E> objects, List<Double> weights) {
		List<WeightedValue<E>> list = buildList(objects, weights);
		setAll(list);
	}
	
	private static <E> List<WeightedValue<E>> buildList(List<E> values, List<Double> weights) {
		if (values == null)
			throw new IllegalArgumentException("values cannot be null!");
		if (weights == null)
			throw new IllegalArgumentException("weights list cannot be null!");
		if (values.size() != weights.size())
			throw new IllegalStateException("object and weight lists must be the same size!");
		List<WeightedValue<E>> ret = new ArrayList<>(values.size());
		for (int i=0; i<values.size(); i++)
			ret.add(new WeightedValue<>(values.get(i), weights.get(i)));
		return ret;
	}
	
	/**
	 * This checks that the weight and object lists are non null and have the same number of items.
	 * If normalization is forced, then it is checked here.
	 * 
	 * @param values
	 * @param weights
	 * @throws IllegalStateException if lists are of different sizes
	 * @throws IllegalArgumentException if lists are null
	 */
	private void validate(List<? extends WeightedValue<?>> list)
	throws IllegalStateException, IllegalArgumentException {
		if (list == null)
			throw new IllegalArgumentException("list cannot be null!");
		
		if (forceNormalization && list.size() > 0) {
			if (!isNormalized(list))
				throw new IllegalStateException("weights must sum to 1 (current sum: "+getWeightSum()+")");
		}
		
		for (WeightedValue<?> value : list) {
			double weight = value.weight;
			if (!isWeightWithinRange(weight))
				throw new IllegalArgumentException("weight of '"+weight+"' is outside of range " +
						+weightValueMin+" <= weight <= "+weightValueMax);
		}
	}
	
	public void add(E object, double weight) throws IllegalStateException {
		this.add(new WeightedValue<E>(object, weight));
	}
	
	/**
	 * Sets and validates the weights
	 * 
	 * @param newWeights
	 * @throws IllegalStateException if the weights are invalid
	 */
	public void setWeights(List<Double> newWeights) throws IllegalStateException {
		Preconditions.checkState(newWeights.size() == this.list.size(), "Passed in weights is of unexpected size");
		List<WeightedValue<E>> modList = new ArrayList<>();
		for (int i=0; i<list.size(); i++)
			modList.add(new WeightedValue<>(this.list.get(i).value, newWeights.get(i)));
		setAll(modList);
	}
	
	/**
	 * Set the list of values
	 * 
	 * @param values
	 * @throws IllegalStateException if the objects and weights are invalid
	 * @throws IllegalArgumentException if the passed in values are null
	 */
	public void setValues(List<E> values) throws IllegalStateException {
		if (values == null)
			throw new IllegalArgumentException("Values cannot be null");
		Preconditions.checkState(values.size() == this.list.size(), "Passed in values is of unexpected size");
		List<WeightedValue<E>> modList = new ArrayList<>();
		for (int i=0; i<list.size(); i++)
			modList.add(new WeightedValue<>(values.get(i), this.list.get(i).weight));
		setAll(modList);
	}
	
	public void setWeight(int i, double weight) {
		WeightedValue<E> orig = list.get(i);
		this.list.set(i, new WeightedValue<>(orig.value, weight));
		try {
			validate(list);
			sampler = null;
		} catch (RuntimeException e) {
			this.list.set(i, orig);
			throw e;
		}
	}
	
	public void setAll(List<WeightedValue<E>> list) {
		this.setAll(list, true);
	}
	
	private void setAll(List<WeightedValue<E>> list, boolean validate) {
		if (validate)
			validate(list);
		this.list = list;
		this.sampler = null;
	}
	
	/**
	 * Set both the objects and the weights
	 * 
	 * @param objects
	 * @param weights
	 * @throws IllegalStateException if the objects and weights are invalid
	 */
	public void setAll(List<E> objects, List<Double> weights) throws IllegalStateException {
		List<WeightedValue<E>> list = buildList(objects, weights);
		setAll(list);
		
//		System.out.println("***** Set called *****");
//		for (double weight : weights)
//			System.out.println(weight);
	}
	
	@Override
	public int size() {
		return list.size();
	}
	
	public double getWeight(int i) {
		return list.get(i).weight;
	}
	
	public double getWeight(E object) {
		if (object == null)
			throw new NoSuchElementException();
		for (int i=0; i<list.size(); i++)
			if (object.equals(list.get(i).value))
				return getWeight(i);
		throw new NoSuchElementException();
	}
	
	public E getValue(int i) {
		return list.get(i).value;
	}

	@Override
	public WeightedValue<E> get(int index) {
		return list.get(index);
	}
	
	@Override
	public WeightedValue<E> set(int index, WeightedValue<E> element) {
		WeightedValue<E> prev = list.set(index, element);
		try {
			validate(list);
			sampler = null;
		} catch (RuntimeException e) {
			// roll back
			list.set(index, prev);
			throw e;
		}
		return prev;
	}

	@Override
	public void add(int index, WeightedValue<E> element) {
		list.add(index, element);
		try {
			validate(list);
			sampler = null;
		} catch (RuntimeException e) {
			// roll back
			list.remove(index);
			throw e;
		}
	}

	/**
	 * 
	 * @return true if list is empty or all weights are equal, false otherwise
	 */
	public boolean areWeightsEqual() {
		if (list.size() == 0)
			return true;
		double wt0 = list.get(0).weight;
		
		for (WeightedValue<E> val : list)
			if (val.weight != wt0)
				return false;
		return true;
	}
	
	public void setWeightsEqual() {
		if (areWeightsEqual())
			return;
		
		double wt = 1d / (double)size();
		
		setWeightsToConstant(wt);
	}
	
	public void setWeightsToConstant(double weight) {
		ArrayList<Double> newWeights = new ArrayList<Double>();
		for (int i=0; i<size(); i++)
			newWeights.add(weight);
		
		setWeights(newWeights);
	}
	
	public void normalize() {
		double sum = getWeightSum();
		if (Precision.equals(sum, 1d, 0.0001))
			return; // already normalized
		Preconditions.checkState(sum > 0d && Double.isFinite(sum),
				"Cannot normalize, weight sum must be finite and positive: %s", sum);
		
		List<WeightedValue<E>> normalized = new ArrayList<>(list.size());
		for (int i=0; i<list.size(); i++) {
			WeightedValue<E> orig = list.get(i);
			normalized.add(new WeightedValue<>(orig.value, orig.weight/sum));
		}
		setAll(normalized);
	}
	
	
	public double getWeightSum() {
		return getWeightSum(list);
	}
	
	private static double getWeightSum(List<? extends WeightedValue<?>> list) {
		double sum = 0;
		for (WeightedValue<?> val: list)
			sum += val.weight;
		return sum;
	}
	
	public boolean isNormalized() {
		return isNormalized(list);
	}
	
	private static boolean isNormalized(List<? extends WeightedValue<?>> list) {
		float sum = (float)getWeightSum(list);
		return sum == 1f;
	}
	
	public void setForceNormalization(boolean forceNormalization) {
		this.forceNormalization = forceNormalization;
		if (forceNormalization)
			normalize();
	}
	
	public boolean isForceNormalization() {
		return forceNormalization;
	}
	
	public static String getName(Object obj) {
		if (obj instanceof Named)
			return ((Named)obj).getName();
		else
			return obj.toString();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("WeightedList[");
		for (int i=0; i<size(); i++) {
			WeightedValue<E> val = get(i);
			if (i > 0)
				str.append("; ");
			str.append(getName(val.value)).append(", ").append((float)val.weight);
		}
		str.append("]");
		return str.toString();
	}

	/**
	 * Get the minumum weight value allowed.
	 * 
	 * @return minumum weight value allowed.
	 */
	public double getWeightValueMin() {
		return weightValueMin;
	}

	/**
	 * Set the minimum weight value allowed.
	 * 
	 * @param weightValueMax
	 */
	public void setWeightValueMin(double weightValueMin) {
		if (weightValueMin > weightValueMax)
			throw new IllegalArgumentException("min cannot be greater than max!");
		double oldVal = this.weightValueMin;
		this.weightValueMin = weightValueMin;
		try {
			validate(list);
		} catch (RuntimeException e) {
			this.weightValueMin = oldVal;
			throw e;
		}
	}

	/**
	 * Get the maximum weight value allowed.
	 * 
	 * @return maximum weight value allowed.
	 */
	public double getWeightValueMax() {
		return weightValueMax;
	}

	/**
	 * Set the maximum weight value allowed.
	 * 
	 * @param listValueMax
	 */
	public void setWeightValueMax(double weightValueMax) {
		if (weightValueMax < weightValueMin)
			throw new IllegalArgumentException("max cannot be less than min!");
		double oldVal = this.weightValueMax;
		this.weightValueMax = weightValueMax;
		try {
			validate(list);
		} catch (RuntimeException e) {
			this.weightValueMax = oldVal;
			throw e;
		}
	}
	
	public boolean isWeightWithinRange(double weight) {
		return (float)weight <= (float)weightValueMax && (float)weight >= (float)weightValueMin;
	}
	
	/**
	 * Returns the sum of each value multiplied by its respective weight.
	 * 
	 * @param values values to return the weighted average
	 * @return weighted average
	 * @throws IllegalArgumentException if the size of <code>values</code> doesn't match
	 * the size of this list.
	 */
	public double getWeightedAverage(List<Double> values) {
		if (values.size() != list.size())
			throw new IllegalArgumentException("values.size() != weights.size()");
		double weighted = 0;
		for (int i=0; i<values.size(); i++) {
			double val = values.get(i);
			double weight = list.get(i).weight;
			weighted += val * weight;
		}
		return weighted;
	}
	
	/**
	 * Returns the sum of each value multiplied by its respective weight.
	 * 
	 * @param values values to return the weighted average
	 * @return weighted average
	 * @throws IllegalArgumentException if the size of <code>values</code> doesn't match
	 * the size of this list.
	 */
	public double getWeightedAverage(double[] values) {
		if (values.length != list.size())
			throw new IllegalArgumentException("values.size() != weights.size()");
		double weighted = 0;
		for (int i=0; i<values.length; i++) {
			double val = values[i];
			double weight = list.get(i).weight;
			weighted += val * weight;
		}
		return weighted;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		for (int i=0; i<size(); i++) {
			Element valEl = el.addElement("Element");
			valEl.addAttribute("index", i+"");
			valEl.addAttribute("name", getName(getValue(i)));
			valEl.addAttribute("weight", getWeight(i)+"");
		}
		
		return root;
	}
	
	public void setWeightsFromXMLMetadata(Element el) {
		ArrayList<Double> weights = new ArrayList<Double>();
		for (int i=0; i<size(); i++) {
			weights.add(null);
		}
		for (Element valEl : (List<Element>)el.elements()) {
			int valI = Integer.parseInt(valEl.attributeValue("index"));
			double weight = Double.parseDouble(valEl.attributeValue("weight"));
			
			weights.set(valI, weight);
		}
		for (Double weight : weights)
			if (weight == null)
				throw new IllegalArgumentException("Given XML element doesn't have a mapping for each element!");
		setWeights(weights);
	}
	
	public E sample() {
		return sample(Math.random());
	}
	
	public E sample(Random rand) {
		return sample(rand.nextDouble());
	}
	
	public E sample(double randDouble) {
		Preconditions.checkState(size() > 0);
		if (size() == 1)
			return list.get(0).value;
		if (sampler == null) {
			double[] weights = new double[list.size()];
			for (int i=0; i<weights.length; i++)
				weights[i] = list.get(i).weight;
			sampler = new IntegerPDF_FunctionSampler(weights);
		}
		int index = sampler.getRandomInt(randDouble);
		return list.get(index).value;
	}

}
