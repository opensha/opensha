/**
 * 
 */
package org.opensha.commons.data.function;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This class sets the tolerance high so that it can be used to construct histograms.
 * 
 * It also provides some methods relevant to a histogram.
 * 
 * @author field
 *
 */
public class HistogramFunction extends EvenlyDiscretizedFunc {

	/**
	 * @param min
	 * @param num
	 * @param delta
	 */
	public HistogramFunction(double min, int num, double delta) {
		super(min, num, delta);
		this.setTolerance(getDelta());
	}

	/**
	 * @param min
	 * @param max
	 * @param num
	 */
	public HistogramFunction(double min, double max, int num) {
		super(min, max, num);
		this.setTolerance(getDelta());
	}

	/**
	 * This normalized the function so that the sum of Y valaues is 1.0
	 */
	public void normalizeBySumOfY_Vals() {
		scale(1.0/calcSumOfY_Vals());
	}
	
	/**
	 * This returns the cumulative distribution function (total number less than and equal to each x-axis value)
	 * @return
	 */
	public HistogramFunction getCumulativeDistFunction() {
		HistogramFunction cumHist = new HistogramFunction(getMinX(), size(), getDelta());
		double sum=0;
		for(int i=0;i<size();i++) {
			sum+=getY(i);
			cumHist.set(i,sum);
		}
		return cumHist;
	}
	
	/**
	 * This returns the cumulative distribution function (total number less than and equal to each x-axis value),
	 * where the bins are offset on the x-axis by a half width to be more accurate (this has one more point than
	 * what's returned by getCumulativeDistFunction())
	 * @return
	 */
	public HistogramFunction getCumulativeDistFunctionWithHalfBinOffset() {
		HistogramFunction cumHist = new HistogramFunction(getMinX()-getDelta()/2d, size()+1, getDelta());
		double sum=0;
		cumHist.set(0,0d);
		for(int i=1;i<cumHist.size();i++) {
			sum+=getY(i-1);
			cumHist.set(i,sum);
		}
		return cumHist;
	}
	
	/**
	 * This returns the x-axis value corresponding to the maximum y-axis value
	 * @return
	 */
	public double getMode() {
		double mode=Double.NaN, max=-1;;
		for(int i=0;i<size();i++) {
			if(getY(i)>max) {
				max = getY(i);
				mode = getX(i);
			}
		}
		return mode;
	}
	
	public double computeMean() {
		double sum = calcSumOfY_Vals();
		double mean = 0;
		for(int i=0;i<size();i++) {
			mean+=getX(i)*getY(i)/sum;
		}
		return mean;
	}
	
	
	public double computeStdDev() {
		double sum = calcSumOfY_Vals();
		double mean = computeMean();
		double var = 0;
		for(int i=0;i<size();i++) {
			var+=(getX(i)-mean)*(getX(i)-mean)*getY(i)/sum;
		}
		return Math.sqrt(var);
	}
	
	public double computeCOV() {
		return computeStdDev()/computeMean();
	}
	
	public static HistogramFunction fromData(double[] data, double minBin, int num, double delta) {
		HistogramFunction hist = new HistogramFunction(minBin, num, delta);
		
		for (double val : data)
			hist.add(val, 1d);
		
		return hist;
	}
	
	/**
	 * This returns stacked histograms for display. Histograms will be stacked in the order that they
	 * are passed in, with the last one on top, and must all have the same x values.
	 * @param hists
	 * @param normalize
	 * @return
	 */
	public static List<HistogramFunction> getStackedHists(List<HistogramFunction> hists, boolean normalize) {
		Preconditions.checkArgument(hists.size() > 1, "Must supply at least 2 histograms");
		hists = Lists.newArrayList(hists);
		// flip it so that last is on top
		Collections.reverse(hists);
		
		double overallTot = 0d;
		for (HistogramFunction hist : hists)
			overallTot += hist.calcSumOfY_Vals();
		
		List<HistogramFunction> stacked = Lists.newArrayList();
		
		// make sure they're all the same x values
		for (int i=1; i<hists.size(); i++) {
			HistogramFunction h1 = hists.get(i-1);
			HistogramFunction h2 = hists.get(i);
			
			Preconditions.checkArgument(h1.size() == h2.size(),
					"Histogram x values inconsistent!");
			Preconditions.checkArgument((float)h1.getMinX() == (float)h2.getMinX(),
					"Histogram x values inconsistent!");
			Preconditions.checkArgument((float)h1.getDelta() == (float)h2.getDelta(),
					"Histogram x values inconsistent!");
		}
		
		int numX = hists.get(0).size();
		
		// this keeps track of the running total
		double[] binTots = new double[numX];
		for (HistogramFunction hist : hists) {
			HistogramFunction stackHist = new HistogramFunction(hist.getMinX(), hist.size(), hist.getDelta());
			stackHist.setName(hist.getName());
			String info = hist.getInfo();
			if (info == null || info.isEmpty())
				info = "";
			else
				info += "\n";
			info += "(stacked histogram, y value data relative to histogram below, not zero)";
			if (normalize)
				info += "\nSum of unstacked y values: "+(float)(hist.calcSumOfY_Vals()/overallTot);
			else
				info += "\nSum of unstacked y values: "+(float)hist.calcSumOfY_Vals();
			stackHist.setInfo(info);
			
			for (int i=0; i<hist.size(); i++) {
				double y = hist.getY(i);
				stackHist.set(i, y+binTots[i]);
				binTots[i] += y;
			}
			
			stacked.add(stackHist);
		}
		
		if (normalize) {
			double ratio = 1d/overallTot;
			for (HistogramFunction hist : stacked)
				for (int i=0; i<hist.size(); i++)
					hist.set(i, hist.getY(i)*ratio);
		}
		
		// flip it back
		Collections.reverse(stacked);
		return stacked;
	}
	
	/**
	 * Creates a histogram that encompasses the given range with the given delta, with well placed
	 * bin edges.
	 * @param minValue
	 * @param maxValue
	 * @param delta
	 * @return
	 */
	public static HistogramFunction getEncompassingHistogram(double minValue, double maxValue, double delta) {
		Preconditions.checkState(minValue < maxValue);
		double halfDelta = 0.5*delta;
		double numBinsAwayFromZero = Math.floor(minValue / delta);
		double minX = numBinsAwayFromZero * delta + halfDelta;
		// handle edge cases
		if (minValue < minX-halfDelta)
			minValue -= delta;
		else if (minValue > minX+halfDelta)
			minValue += delta;
		Preconditions.checkState(minValue <= minX + halfDelta && minValue >= minX - halfDelta);
		
		double maxDelta = maxValue - minX;
		int numBins = (int)(maxDelta / delta + 0.5)+1;
		double maxX = minX + (numBins-1)*delta;
		Preconditions.checkState(maxValue <= maxX + halfDelta);
		Preconditions.checkState(maxValue >= maxX - halfDelta);
		
//		System.out.println("minX: "+minX+", maxX: "+maxX+", num: "+numBins);
		
		return new HistogramFunction(minX, numBins, delta);
	}
	
//	// test of compute methods
//	public static void main(String[] args) {
//		// should not reference sha classes like the below code does
//		BPT_DistCalc bpt_calc = new BPT_DistCalc();
//		bpt_calc.setAll(110, 0.25, 1, 600);
//		EvenlyDiscretizedFunc func = bpt_calc.getPDF();
//		GraphWindow graph = new GraphWindow(func, "Test BPT"); 
//		HistogramFunction hist = new HistogramFunction(func.getMinX(),func.getMaxX(), func.size());
//		for(int i=0;i<hist.size();i++)
//			hist.set(i, func.getY(i));
//		System.out.println("mean="+hist.computeMean());
//		System.out.println("std="+hist.computeStdDev());
//		System.out.println("cov="+hist.computeCOV());
//
//	}

}
