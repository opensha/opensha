package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.util.ComparablePairing;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.gson.annotations.JsonAdapter;

/**
 * This class stores integer values in the X axis and the relative probability associated with each integer
 * in the Y axis (representing a PDF of integer values). The x-axis ranges from zero to the number of points 
 * minus one (i.e, the x-axis values are the same as the x-axis indices).  Once the relative probabilities have
 * been populated in the y-axis, random samples can be obtained from the distribution using the getRandomInt()
 * method.
 * @author field
 *
 */
@JsonAdapter(IntegerPDF_FunctionSampler.Adapter.class)
public class IntegerPDF_FunctionSampler extends EvenlyDiscretizedFunc {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	boolean dataChange = true;
	double[] cumDistVals;
	double sumOfYvals;
	
	/**
	 * 
	 * @param numInts - the number of integers
	 */
	public IntegerPDF_FunctionSampler(int numInts) {
		super(0.0, numInts, 1.0);
		cumDistVals = new double[numInts];
	}
	
	/**
	 * 
	 * @param values - the Y values for the sampler
	 */
	public IntegerPDF_FunctionSampler(float[] values) {
		super(0.0, values.length, 1.0);
		cumDistVals = new double[values.length];
		for(int i=0;i<values.length;i++)
			set(i, (float)values[i]);
	}

	
	/**
	 * 
	 * @param values - the Y values for the sampler
	 */
	public IntegerPDF_FunctionSampler(double[] values) {
		super(0.0, values.length, 1.0);
		cumDistVals = new double[values.length];
		for(int i=0;i<values.length;i++)
			set(i, values[i]);
	}

	
	/**
	 * 
	 * @param xyDataSet - contains the Y values for the sampler
	 */
	public IntegerPDF_FunctionSampler(XY_DataSet xyDataSet) {
		super(0.0, xyDataSet.size(), 1.0);
		cumDistVals = new double[xyDataSet.size()];
		for(int i=0;i<xyDataSet.size();i++)
			set(i, xyDataSet.getY(i));
	}

	
	/**
	 * This makes the cumulative dist function
	 * synchronized externally
	 */
	private void updateCumDistVals() {
		// update with local variables first, then set globally, in case multiple threads try this at once
		double sumOfYvals=0;
		double[] cumDistVals = new double[this.cumDistVals.length];
		for(int i=0;i<size();i++) {
			sumOfYvals += getY(i);
			cumDistVals[i]=sumOfYvals;
		}
		for(int i=0;i<size();i++) cumDistVals[i] /= sumOfYvals;
		this.sumOfYvals = sumOfYvals;
		this.cumDistVals = cumDistVals;
	}
	
	public double getSumOfY_vals() {
		if (dataChange) {
			updateCumDistVals();
			dataChange=false;
		}
		return sumOfYvals;
	}
	
	
	/**
	 * This returns a random integer based on the probabilities of each
	 * @return
	 */
	public int getRandomInt() {
		return getInt(Math.random());
	}
	
	/**
	 * This returns a random integer based on the probabilities of each, and
	 * using the supplied random number (supplied in cases where reproducibility is important)
	 * @param randDouble - a value between 0 (inclusive) and 1 (exclusive)
	 * @return
	 */
	public int getRandomInt(double randDouble) {
		return getInt(randDouble);
	}

	
	
	/**
	 * This returns the integer value corresponding to the given probability (between 0 and 1).
	 * @param prob - a value between 0 and 1.
	 * @return
	 */
	public int getInt(double prob) {
		// update if needed
		if (dataChange) {
			updateCumDistVals();
			dataChange=false;
		}
		
		
		// faster version which uses Java's built in binary search. 20% faster than the original algorithm commented out below
		// 
		// it is also slightly more accurate. if there is a cum dist value exactly at the given probability, it will return
		// that index, rather than the index above. this case is rarely if ever encountered
		int ind = Arrays.binarySearch(cumDistVals, prob);
		if (ind < 0) {
			// ind = (-insertion_point - 1)
			// ind + 1 = -insertion_point
			// insertion_point = -(ind + 1)
			ind = -(ind + 1);
		}
		
		if (ind >= size()) {
			boolean containsNaNs=false;
			for(int i=0;i<this.size();i++) {
				if(Double.isNaN(getY(i))) {
					containsNaNs=true;
					break;
				}
			}
			throw new RuntimeException("Problem: chosen int above x-axis bounds; Y-axis contain NaNs? = "
					+containsNaNs+"\tsumOfAllYVals="+this.getSumOfY_vals());
		}
		return ind;
//		
//		
//		// this is needed because the first one is never accessed in the algorithm below
//		if(prob<cumDistVals[0]) return 0;
//		
//		// search for the index
//		int indexLow=0;
//		int indexHigh=size();
////		long st = System.currentTimeMillis();
//		while (indexHigh-indexLow > 1) {
//			int testIndex = (int)Math.floor((indexHigh+indexLow)/2);
//			if (prob<cumDistVals[testIndex]) {
//				indexHigh=testIndex;
//			} else {
//				indexLow=testIndex;
//			}
////			if(System.currentTimeMillis()-st > 10000) {	// 100 sec
////				System.out.println("prob="+prob+"\tindexLow="+indexLow+"\tindexHigh="+indexHigh+"\ttestIndex="+testIndex);
////				try{
////					FileWriter fw1 = new FileWriter("tempIntPDF_Data");
////					fw1.write("prob="+prob+"\tindexLow="+indexLow+"\tindexHigh="+indexHigh+"\ttestIndex="+testIndex+"\n");
////					for(int i=0;i<this.getNum();i++) {
////						fw1.write(i+"\t"+(float)getX(i)+"\t"+getY(i)+"\t"+cumDistVals[i]+"\n");
////					}
////					fw1.close();
////				}catch(Exception e) {
////					e.printStackTrace();
////
////				}
////				System.exit(0);
////
////			}
//		}
//		if(indexHigh == this.size()) {
//			boolean containsNaNs=false;
//			for(int i=0;i<this.size();i++)
//				if(Double.isNaN(getY(i))) {
//						containsNaNs=true;
//						break;
//				}
////			System.out.println(this);
//			throw new RuntimeException("Problem: chosen int above x-axis bounds; Y-axis contain NaNs? = "+containsNaNs+"\tsumOfAllYVals="+this.getSumOfY_vals());
//		}
//
//		/*
//		if(indexHigh == this.getNum()) {
//			System.out.println("Error: "+prob+"\n");
//			ArrayList funcs = new ArrayList();
//			funcs.add(this);
//			GraphWindow sr_graph = new GraphWindow(funcs, "");  
//		}
//		*/
//		return indexHigh;
	}
	
	
	// override the following to record that data has changed
	public void set(Point2D point) {
		super.set(point);
		dataChange = true;
	}
	public void set(double x, double y) {
		super.set(x,y);
		dataChange = true;
	}
	public void add(double x, double y) {
		super.add(x, y);
		dataChange = true;
	}
	public void set(int index, double y) {
		super.set(index, y);
		dataChange = true;
	}
	public void add(int index, double y) {
		super.add(index, y);
		dataChange = true;
	}
	public void set(double min, int num, double delta) {
		super.set(min, num, delta);
		dataChange = true;
	}
	public void set(double min, double max, int num) {
		super.set(min,max,num);
		dataChange = true;
	}
	
	public double[] getY_valuesArray() {
		return points;
	}
	
	/**
	 * Sorts all data by it's contribution and retuns the sorted indexes of the data points
	 * representing the given fraction of the total sum.
	 * @param fract fraction between 0 and 1, e.g. 0.999
	 * @return
	 */
	public List<Integer> getOrderedIndicesOfHighestXFract(double fract) {
		Preconditions.checkArgument(fract > 0d && fract <= 1d, "Fract must be between 0 and 1: %s", fract);
		
		// need indexes and data in list form
		List<Integer> indexes = Lists.newArrayList();
		for (int i=0; i<size(); i++)
			indexes.add(i);
		List<Double> values = Doubles.asList(points);
		// this will build sortable pairings of values to indexes, which can be sorted by value
		List<ComparablePairing<Double, Integer>> pairings = ComparablePairing.build(values, indexes);
		// sort by value low to high
		Collections.sort(pairings);
		// reverse to make it high to low
		Collections.reverse(pairings);
		
		// list of indexes to return
		List<Integer> ret = Lists.newArrayList();
		
		double sum = getSumOfY_vals();
		double sumTarget = sum*fract; // we'll stop when the running total reaches this
		double runningTotal = 0;
		
		for (ComparablePairing<Double, Integer> pairing : pairings) {
			runningTotal += pairing.getComparable();
			ret.add(pairing.getData());
			if (runningTotal >= sumTarget)
				break;
		}
		
		return ret;
	}
	
	public static void main(String[] args) {
		double[] testVals = new double[250000];
		for (int i=0; i<testVals.length; i++)
			testVals[i] = Math.random();
		IntegerPDF_FunctionSampler benchSampler = new IntegerPDF_FunctionSampler(testVals);
		System.out.println("Benchmarking...");
		long st = System.currentTimeMillis();
		double sum = 0;
		for (int i=0; i<1000000000; i++)
			sum += benchSampler.getInt(Math.random());
		long elapsed = System.currentTimeMillis() - st;
		System.out.println("benchmark test sum of "+sum+" after "+elapsed+" ms");
		
		double[] values = { 1d, 10d, 0.01d, 5d, 100d, 6d, 0.1d };
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(values);
		System.out.println("Data: "+Joiner.on(",").join(Doubles.asList(values)));
		sampler.updateCumDistVals();
		System.out.println("Sorted data: "+Joiner.on(",").join(Doubles.asList(sampler.cumDistVals)));
		System.out.println(sampler.getInt(0));	// 0
		System.out.println(sampler.getInt(0.00818933748259765));	// 0
		System.out.println(sampler.getInt(0.00818933748259766));	// 1
		System.out.println(sampler.getInt(0.089));	// 1
		System.out.println(sampler.getInt(0.0901));	// 2
		System.out.println(sampler.getInt(0.092));	// 3
		System.out.println(sampler.getInt(0.1));	// 3
		try {
			System.out.println(sampler.getInt(1d));	// exception
		} catch (Exception e) {
			e.printStackTrace();
		}
		sum = 0;
		st = System.currentTimeMillis();
		for (int i=0; i<1000000000; i++)
			sum += sampler.getInt(Math.random());
		elapsed = System.currentTimeMillis() - st;
		System.out.println("sum of "+sum+" after "+elapsed+" ms");
//		System.out.println("0.5: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.5)));
//		System.out.println("0.9: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.9)));
//		System.out.println("0.99: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.99)));
//		System.out.println("0.999: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.999)));
//		System.out.println("1.0: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(1d)));
	}
	
	public static class Adapter extends DiscretizedFunc.AbstractAdapter<IntegerPDF_FunctionSampler> {

		@Override
		protected IntegerPDF_FunctionSampler instance(Double minX, Double maxX, Integer size) {
			Preconditions.checkNotNull(size, "size must be supplied before values to deserialize IntegerPDF_FunctionSampler");
			return new IntegerPDF_FunctionSampler(size);
		}

		@Override
		protected Class<IntegerPDF_FunctionSampler> getType() {
			return IntegerPDF_FunctionSampler.class;
		}
		
	}

}
