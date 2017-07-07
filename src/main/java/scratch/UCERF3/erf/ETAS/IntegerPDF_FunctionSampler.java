package scratch.UCERF3.erf.ETAS;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.util.ComparablePairing;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * This class stores integer values in the X axis and the relative probability associated with each integer
 * in the Y axis (representing a PDF of integer values). The x-axis ranges from zero to the number of points 
 * minus one (i.e, the x-axis values are the same as the x-axis indices).  Once the relative probabilities have
 * been populated in the y-axis, random samples can be obtained from the distribution using the getRandomInt()
 * method.
 * @author field
 *
 */
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
	 */
	private void updateCumDistVals() {
		sumOfYvals=0;
		for(int i=0;i<size();i++) {
			sumOfYvals += getY(i);
			cumDistVals[i]=sumOfYvals;
		}
		for(int i=0;i<size();i++) cumDistVals[i] /= sumOfYvals;
//		for(int i=0;i<getNum();i++) System.out.println(i+"\t"+cumDistVals[i]);
	}
	
	public double getSumOfY_vals() {
		if(dataChange) {
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
		if(dataChange) {
			updateCumDistVals();
			dataChange=false;
		}
//		System.out.println("prob="+prob);
		
		// this is needed because the first one is never accessed in the algorithm below
		if(prob<cumDistVals[0]) return 0;
		
		// search for the index
		int indexLow=0;
		int indexHigh=size();
		long st = System.currentTimeMillis();
		while(indexHigh-indexLow > 1) {
			int testIndex = (int)Math.floor((indexHigh+indexLow)/2);
			if(prob<cumDistVals[testIndex]) {
				indexHigh=testIndex;
			}
			else {
				indexLow=testIndex;
			}
//			if(System.currentTimeMillis()-st > 10000) {	// 100 sec
//				System.out.println("prob="+prob+"\tindexLow="+indexLow+"\tindexHigh="+indexHigh+"\ttestIndex="+testIndex);
//				try{
//					FileWriter fw1 = new FileWriter("tempIntPDF_Data");
//					fw1.write("prob="+prob+"\tindexLow="+indexLow+"\tindexHigh="+indexHigh+"\ttestIndex="+testIndex+"\n");
//					for(int i=0;i<this.getNum();i++) {
//						fw1.write(i+"\t"+(float)getX(i)+"\t"+getY(i)+"\t"+cumDistVals[i]+"\n");
//					}
//					fw1.close();
//				}catch(Exception e) {
//					e.printStackTrace();
//
//				}
//				System.exit(0);
//
//			}
		}
		if(indexHigh == this.size()) {
			boolean containsNaNs=false;
			for(int i=0;i<this.size();i++)
				if(Double.isNaN(getY(i))) {
						containsNaNs=true;
						break;
				}
//			System.out.println(this);
			throw new RuntimeException("Problem: chosen int above x-axis bounds; Y-axis contain NaNs? = "+containsNaNs+"\tsumOfAllYVals="+this.getSumOfY_vals());
		}

		/*
		if(indexHigh == this.getNum()) {
			System.out.println("Error: "+prob+"\n");
			ArrayList funcs = new ArrayList();
			funcs.add(this);
			GraphWindow sr_graph = new GraphWindow(funcs, "");  
		}
		*/
		return indexHigh;
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
		double[] values = { 1d, 10d, 0.01d, 5d, 100d, 6d, 0.1d };
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(values);
		System.out.println("Data: "+Joiner.on(",").join(Doubles.asList(values)));
		System.out.println("0.5: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.5)));
		System.out.println("0.9: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.9)));
		System.out.println("0.99: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.99)));
		System.out.println("0.999: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(0.999)));
		System.out.println("1.0: "+Joiner.on(",").join(sampler.getOrderedIndicesOfHighestXFract(1d)));
	}

}
