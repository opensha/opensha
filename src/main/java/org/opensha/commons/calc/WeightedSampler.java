package org.opensha.commons.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import scratch.UCERF3.erf.ETAS.IntegerPDF_FunctionSampler;

import static com.google.common.base.Preconditions.*;

/**
 * This class allows you to sample items randomly according to their rates
 * (or any arbitrary positive scalar). It can also generate a random series
 * of items. 
 * 
 * @author kevin
 *
 * @param <T>
 */
public class WeightedSampler<T> {
	
	// left as default for in-package testing
	private List<T> objects;
	private Random r;
	private IntegerPDF_FunctionSampler sampler;
	
	/**
	 * Creates a PoissonSampler with the given list of objects and rates. Rates will be normalized
	 * and sorted internally.
	 * 
	 * @param objects list of objects of which to sample
	 * @param rates list of rates (or any arbitrary scalar) to be used for sampling. each rate must
	 * be >=0, and there must be at least one non zero rate.
	 * @throws NullPointerException if any arguments are null
	 * @throws IllegalArgumentException if lists of objects and rates are empty, or of different sizes.
	 * Will also be thrown if any rates are less than zero, or if all rates are equal to zero.
	 */
	public WeightedSampler(List<T> objects, List<Double> rates)
	throws NullPointerException, IllegalArgumentException {
		this(objects, rates, new Random());
	}
	
	/**
	 * Creates a PoissonSampler with the given list of objects and rates. Rates will be normalized
	 * and sorted internally.
	 * 
	 * @param objects list of objects of which to sample
	 * @param rates list of rates (or any arbitrary scalar) to be used for sampling. each rate must
	 * be >=0, and there must be at least one non zero rate.
	 * @param r random number generator to be used
	 * @throws NullPointerException if any arguments are null
	 * @throws IllegalArgumentException if lists of objects and rates are empty, or of different sizes.
	 * Will also be thrown if any rates are less than zero, or if all rates are equal to zero.
	 */
	public WeightedSampler(List<T> objects, List<Double> rates, Random r)
	throws NullPointerException, IllegalArgumentException {
		checkNotNull(objects, "objects cannot be null!");
		checkArgument(!objects.isEmpty(), "objects cannot be empty!");
		checkNotNull(rates, "rates cannot be null!");
		checkArgument(!rates.isEmpty(), "rates cannot be empty!");
		checkArgument(objects.size() == rates.size(), "items and rates must be of the same size");
		boolean hasNonZero = false;
		for (double rate : rates) {
			checkArgument(rate >= 0, "rates must be >= 0");
			if (rate > 0)
				hasNonZero = true;
		}
		checkArgument(hasNonZero, "must be at least one non zero rate");
		checkNotNull(r, "random cannot be null!");
		
		this.r = r;
		
		this.objects = Collections.unmodifiableList(objects);
		
		sampler = new IntegerPDF_FunctionSampler(objects.size());
		for (int i=0; i<objects.size(); i++) {
			sampler.set(i, rates.get(i));
		}
	}
	
	/**
	 * Returns the item at the given normalized (between 0 and 1) cumulative rate. Note that
	 * all items are sorted, so <code>getItemForNormCumRate(0)</code> would return the first item,
	 * and <code>getItemForNormCumRate(1)</code> would return the last item with a non-zero rate
	 * 
	 * @param rate
	 * @return
	 */
	public T getItemForNormCumRate(double rate) {
		checkState(rate >= 0d && rate <= 1d, "rate must be within zero and one");
		int ind = sampler.getInt(rate);
		return objects.get(ind);
	}
	
	/**
	 * 
	 * @return the next randomly selected item
	 */
	public T nextItem() {
		return getItemForNormCumRate(r.nextDouble());
	}
	
	/**
	 * Generates a series of randomly selected items.
	 * 
	 * @param size the number of items in the generated series
	 * @return series of randomly selected items
	 * @throws IllegalArgumentException if size is less than or equal to zero
	 */
	public List<T> generateSeries(int size) throws IllegalArgumentException {
		checkArgument(size > 0, "size must be > 0!");
		ArrayList<T> series = new ArrayList<T>();
		
		while (series.size() < size)
			series.add(nextItem());
		
		return series;
	}

}
