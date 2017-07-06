package org.opensha.commons.data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

public class WeightedList<E> implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "WeightedList";
	
	private ArrayList<E> objects;
	private ArrayList<Double> weights;
	
	private boolean forceNormalization = false;
	
	private double weightValueMin = 0;
	private double weightValueMax = 1;
	
	public WeightedList() {
		this(new ArrayList<E>(), new ArrayList<Double>());
	}
	
	public WeightedList(ArrayList<E> objects, ArrayList<Double> weights) {
		set(objects, weights);
	}
	
	/**
	 * This checks that the weight and object lists are non null and have the same number of items.
	 * If normalization is forced, then it is checked here.
	 * 
	 * @param objects
	 * @param weights
	 * @throws IllegalStateException if lists are of different sizes
	 * @throws IllegalArgumentException if lists are null
	 */
	private void validate(ArrayList<?> objects, ArrayList<Double> weights)
	throws IllegalStateException, IllegalArgumentException {
		if (objects == null)
			throw new IllegalArgumentException("object list cannot be null!");
		if (weights == null)
			throw new IllegalArgumentException("weights list cannot be null!");
		
		if (objects.size() != weights.size())
			throw new IllegalStateException("object and weight lists must be the same size!");
		
		if (forceNormalization && weights.size() > 0) {
			if (!isNormalized(weights))
				throw new IllegalStateException("wights must sum to 1 (current sum: "+getWeightSum()+")");
		}
		
		for (double weight : weights) {
			if (!isWeightWithinRange(weight))
				throw new IllegalArgumentException("weight of '"+weight+"' is outside of range " +
						+weightValueMin+" <= weight <= "+weightValueMax);
		}
	}
	
	public void add(E object, double weight) throws IllegalStateException {
		this.objects.add(object);
		this.weights.add(weight);
		
		try {
			validate(objects, weights);
		} catch (RuntimeException e) {
			this.objects.remove(objects.size()-1);
			this.weights.remove(weights.size()-1);
			throw e;
		}
	}
	
	/**
	 * Sets and validates the weights
	 * 
	 * @param newWeights
	 * @throws IllegalStateException if the weights are invalid
	 */
	public void setWeights(ArrayList<Double> newWeights) throws IllegalStateException {
		set(objects, newWeights);
	}
	
	/**
	 * Set the list of objects
	 * 
	 * @param objects
	 * @throws IllegalStateException if the objects and weights are invalid
	 */
	public void setObjects(ArrayList<E> objects) throws IllegalStateException {
		set(objects, weights);
	}
	
	public void setWeight(int i, double weight) {
		double orig = weights.get(i);
		this.weights.set(i, weight);
		try {
			validate(objects, weights);
		} catch (RuntimeException e) {
			this.weights.set(i, orig);
			throw e;
		}
	}
	
	/**
	 * Set both the objects and the weights
	 * 
	 * @param objects
	 * @param weights
	 * @throws IllegalStateException if the objects and weights are invalid
	 */
	public void set(ArrayList<E> objects, ArrayList<Double> weights) throws IllegalStateException {
		validate(objects, weights);
		
		this.objects = objects;
		this.weights = weights;
		
//		System.out.println("***** Set called *****");
//		for (double weight : weights)
//			System.out.println(weight);
	}
	
	public int size() {
		return objects.size();
	}
	
	public double getWeight(int i) {
		return weights.get(i);
	}
	
	public double getWeight(E object) {
		int ind = objects.indexOf(object);
		if (ind < 0)
			throw new NoSuchElementException();
		return getWeight(ind);
	}
	
	public E get(int i) {
		return objects.get(i);
	}
	
	public boolean areWeightsEqual() {
		if (weights.size() == 0)
			return false;
		double wt0 = weights.get(0);
		
		for (double weight : weights)
			if (weight != wt0)
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
		if (isNormalized())
			return;
		
		double sum = getWeightSum();
		ArrayList<Double> newWeights = new ArrayList<Double>();
		
		for (double weight : weights) {
			double newWeight = weight / sum;
			newWeights.add(newWeight);
		}
		
		setWeights(newWeights);
	}
	
	
	public double getWeightSum() {
		return getWeightSum(weights);
	}
	
	private static double getWeightSum(ArrayList<Double> weights) {
		double sum = 0;
		for (double weight : weights)
			sum += weight;
		return sum;
	}
	
	public boolean isNormalized() {
		return isNormalized(weights);
	}
	
	private static boolean isNormalized(ArrayList<Double> weights) {
		float sum = (float)getWeightSum(weights);
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
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		for (int i=0; i<size(); i++) {
			Element valEl = el.addElement("Element");
			valEl.addAttribute("index", i+"");
			valEl.addAttribute("name", getName(objects.get(i)));
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

	@Override
	public String toString() {
		String str = "Weighted List: "+size()+" elements";
		for (int i=0; i<size(); i++) {
			str += "\n* "+getName(get(i))+":\t"+getWeight(i);
		}
		return str;
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
			validate(objects, weights);
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
			validate(objects, weights);
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
	public double getWeightedAverage(ArrayList<Double> values) {
		if (values.size() != weights.size())
			throw new IllegalArgumentException("values.size() != weights.size()");
		double weighted = 0;
		for (int i=0; i<values.size(); i++) {
			double val = values.get(i);
			double weight = weights.get(i);
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
		if (values.length != weights.size())
			throw new IllegalArgumentException("values.size() != weights.size()");
		double weighted = 0;
		for (int i=0; i<values.length; i++) {
			double val = values[i];
			double weight = weights.get(i);
			weighted += val * weight;
		}
		return weighted;
	}

}
