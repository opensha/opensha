package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.data.Point2DToleranceComparator;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.Interpolate;

import com.google.common.base.Preconditions;



/**
 * <b>Title:</b> ArbDiscrEmpiricalDistFunc<p>
 *
 * <b>Description:</b>  This class is similar to ArbitrarilyDiscretizedFunc,
 * except that rather than replacing a point that has the same x value (or within
 * tolerance, which is required to be near zero here), the y values are added together.
 * This is useful for making an empirical distribution where the y-values represent
 * the frequency of occurrence of each x value.  In this context, a nonzero tolerance
 * would not make sense because a values might fall into different points depending
 * on what order they're added.  Due to the numerical precision of floating point
 * arithmetic, the tolerance is really about 1e-16 (multiple values within this 
 * tolerance will be added together).<p>
 *
 * The getNormalizedCumDist() method enables one to get a normalized cumulative distribution.<p>
 * The getFractile(fraction) method gets the x-axis value for the specified fraction (does so by
 * creating a NormalizedCumDist each time, so this is not efficient if several fractiles are
 * desired). <p>
  *
 * @author Edward H. Field
 * @version 1.0
 */

public class ArbDiscrEmpiricalDistFunc extends ArbitrarilyDiscretizedFunc
                                        implements Serializable {

    /* Class name Debbuging variables */
    protected final static String C = "ArbDiscrEmpiricalDistFunc";
    private final static String ERR_MSG_MULTI_MODAL = "Error: There exists more than 1 mode";

    /* Boolean debugging variable to switch on and off debug printouts */
    protected final static boolean D = true;

    /**
     * No-Arg Constructor.
     */
    public ArbDiscrEmpiricalDistFunc() {
    	super(new EmpiricalPoint2DToleranceSortedList(
    			new Point2DToleranceComparator()));
    }
    
    public ArbDiscrEmpiricalDistFunc(Collection<Point2D> initialValues) {
    	super(new EmpiricalPoint2DToleranceSortedList(
    			new Point2DToleranceComparator(), initialValues));
    }


    /**
     * This method is over ridded to throw an exception because tolerance cannot be
     * changed from zero for this class.
     */
    public void setTolerance(double newTolerance) throws InvalidRangeException {

      throw new InvalidRangeException("Cannot change the tolerance for " + C + " (it must be zero)");
    }


    /**
     * This function returns a new copy of this list, including copies
     * of all the points. A shallow clone would only create a new DiscretizedFunc
     * instance, but would maintain a reference to the original points. <p>
     *
     * Since this is a clone, you can modify it without changing the original.
     * @return
     */
    public ArbDiscrEmpiricalDistFunc deepClone(){

        ArbDiscrEmpiricalDistFunc function = new ArbDiscrEmpiricalDistFunc(  );
        function.setInfo(getInfo());
        Iterator it = this.iterator();
        if( it != null ) {
            while(it.hasNext()) {
                function.set( (Point2D)((Point2D)it.next()).clone() );
            }
        }

        return function;

    }


    /**
     * This returns the x-axis value where the interpolated normalized cumulative
     * distribution equals the specified fraction.  If the fraction is below the Y-
     * value of the first point, then the X value of the first point is returned
     * (since there is nothing below to interpolate with).  If there are no points the
     * zero is returned.
     * @param fraction - a value between 0 and 1.
     * @return
     */
    public double getInterpolatedFractile(double fraction) {

      if(fraction < 0 || fraction > 1)
        throw new InvalidRangeException("fraction value must be between 0 and 1");
      
      if(size()==0)
    	  return 0.0;

      DiscretizedFunc tempCumDist = getNormalizedCumDist();

      // if desired fraction is below minimum x value, give minimum x value
      if(fraction < tempCumDist.getMinY())
        return tempCumDist.getMinX();
      else
        return tempCumDist.getFirstInterpolatedX(fraction);
    }


    /**
     * This returns the greatest uninterpolated x-axis value whose normalized
     * cumulative distribution value is less than or equal to fraction.
     * distribution.
     * @param fraction - a value between 0 and 1.
     * @return
     */
    public double getDiscreteFractile(double fraction) {

      if(fraction < 0 || fraction > 1)
        throw new InvalidRangeException("fraction value must be between 0 and 1");

      DiscretizedFunc tempCumDist = getNormalizedCumDist();

      for(int i = 0; i<tempCumDist.size();i++) {
        if(fraction <= tempCumDist.getY(i))
          return tempCumDist.getX(i);
      }

      // if desired fraction is below minimum x value, give minimum x value
      if(fraction < tempCumDist.getMinY())
        return tempCumDist.getMinX();
      else
        return tempCumDist.getFirstInterpolatedX(fraction);
    }



    /**
     * This returns an ArbitrarilyDiscretizedFunc representing the cumulative
     * distribution normalized (so that the last value is equal to one)
     * @return
     */
    public DiscretizedFunc getNormalizedCumDist() {
      return getCumDist(getSumOfAllY_Values());
    }
    
    /**
     * This returns an ArbitrarilyDiscretizedFunc representing the normalized
     * distribution (so that values sum to 1.0).  This returns a modified clone
     * (the original distribution is unmodified)
     * @return
     */
    public ArbDiscrEmpiricalDistFunc getNormalizedDist() {
    	ArbDiscrEmpiricalDistFunc func = deepClone();
    	func.scale(1.0/getSumOfAllY_Values());
      return func;
    }

    
    
    /**
     * This returns the sum of all Y values
     * @return
     */
    public double getSumOfAllY_Values() {
        double totSum = 0;
        Iterator<Point2D>it = iterator();
        while (it.hasNext()) { totSum += (it.next()).getY(); }
        return totSum;    	
    }
    
    
    /**
     * calculates the mean for normalized distribution (done simply as a weight average)
     * @return
     */
    public double getMean() {
    	//ArbitrarilyDiscretizedFunc tempCumDist = getNormalizedCumDist();
    	double sumXY=0.0, sumY=0.0;
    	
    	for(int i=0; i<size(); ++i) {
    		//System.out.println(getX(i)+","+getY(i));
    		sumXY+=getX(i)*getY(i);
    		sumY+=getY(i);
    	}
    	//System.out.println("sumXY="+sumXY+",sumY="+sumY);
    	return sumXY/sumY;
    }
    
    /**
     * Calculates the standard deviation for normalized distribution (finite number of samples not accounted for)
     * 
     * @return
     */
    public double getStdDev() {
    	double mean = getMean();
    	double stdDev=0.0, sumY=0.0;

    	for(int i=0; i<size(); ++i) {
    		double dev = mean-getX(i);
    		stdDev+=dev*dev*getY(i);
    		sumY+=getY(i);
    	}
    	// taking out the following minus 1 because sumY might be less than 1.0; we've also lost the true number 
    	// of samples if multiple values got added to the same x-axis bin
//    	return Math.sqrt(stdDev/(sumY-1.0)); 
    	return Math.sqrt(stdDev/(sumY));
    }

    
    
    /**
     * This returns the coefficient of variation (standard deviation divided by the mean)
     * @return
     */
    public double getCOV() {
    	return getStdDev()/getMean();
    }
    
    
    /**
     *  Get the apparent mode (X value where Y is maximum).  This is apparent because this assumes the distribution
     *  is sampled uniformly.
     * Returns throws a runtime exception in the case of a multi-modal distribution
     * Modified by nvdE to return the mean of two equally likely bins, if the bins are adjacent (instead of runtime exception).
     * 
     * @return
     * 
     */
    public double getApparentMode() {
 //   	throw new RuntimeException("this method is wrong (assumes even x-axis increments) and hasn't yet been fixed");
    	//if(isMultiModal()) throw new RuntimeException(ERR_MSG_MULTI_MODAL);
    	int index=-2;
    	double newY;
    	double maxY = Double.NEGATIVE_INFINITY;
    	boolean tie = false;
    	
    	for(int i=0; i<size(); ++i) {
    		newY = getY(i);
    		if(newY>maxY) {
    			maxY = newY;
    			index = i;
    			tie = false;
    		} else if(newY==maxY) {
    			if(i == index + 1)
    				tie = true;
    			else 
    				throw new RuntimeException(ERR_MSG_MULTI_MODAL);
    		}
    	}
    	
    	if(tie)
    		return (getX(index)+getX(index+1))/2;
    	else
    		return getX(index);
    }
    
    /**
     *  Get the most central mode in the case of a multi-modal distribution.  
     *  If there is an even number of modes (two central modes) we give back 
     *  the larger of the two.
     * @return
     */
    public double getMostCentralMode() {
     	double maxY = getMaxY();
    	// now create list of X-axis values where Y=Ymax
    	ArrayList<Double> xVals = new ArrayList<Double>();
    	for(int i=0; i<size(); ++i)  {
    		if (getY(i) == maxY)
    			xVals.add(getX(i));
    	}
    	Preconditions.checkState(!xVals.isEmpty());
    	int index = xVals.size()/2;
    	return xVals.get(index);
    }
    
    
    public boolean isMultiModal() {
    	int count=0;
    	double val = getMaxY();
    	for(int i=0; i<size(); ++i)  {
    		if(getY(i)==val) ++count;
    	}
    	if(count>1) return true;	
    	else return false;
    }
    
    /**
     * Get the median. It returns the  interpolated fractile at 0.5 for normalized distribution.
     * @return
     */
    public double getMedian() {
    	return getInterpolatedFractile(0.5);
    }


    /**
     * This returns an ArbitrarilyDiscretizedFunc representing the cumulative
     * distribution (sum of Y values less than and equal te each X value) normalized 
     * by the value (totSum) passed in
     * @return
     */
    private DiscretizedFunc getCumDist(double totSum) {
    	double[] xVals = new double[size()];
    	double[] yVals = new double[xVals.length];
    	
    	double sum = 0d;
    	for (int i=0; i<xVals.length; i++) {
    		xVals[i] = getX(i);
    		sum += getY(i);
    		yVals[i] = sum/totSum;
    	}
    	
    	return new LightFixedXFunc(xVals, yVals);
//      ArbitrarilyDiscretizedFunc cumDist = new ArbitrarilyDiscretizedFunc();
//      Point2D dp;
//      double sum = 0;
//      Iterator<Point2D> it = iterator();
//      while (it.hasNext()) {
//        dp = it.next();
//        sum += dp.getY();
//        Point2D dpNew = new Point2D.Double(dp.getX(),sum/totSum);
//        cumDist.set(dpNew);
//      }
//      return cumDist;
    }
    
    

    /**
     * This returns an DiscretizedFunc representing the cumulative
     * distribution (sum of Y values less than and equal te each X value).
     * @return
     */
    public DiscretizedFunc getCumDist() {
      return getCumDist(1.0);
    }
    
    /**
     * The 0.5 is needed to get this to work properly (because setting
     * the value here will add it to the old one)
     */
	@Override
	public void scale(double val) {
		for(int i=0; i<size();i++) this.set(i, 0.5*val*getY(i));
	}

	/**
	 * Quick way to get a normalized CDF if that's all you need, which will be faster for large datasets
	 * than using an actual {@link ArbDiscrEmpiricalDistFunc}.
	 * @param values
	 * @param weights
	 * @return
	 */
	public static LightFixedXFunc calcQuickNormCDF(List<Double> values, List<Double> weights) {
		Preconditions.checkState(weights == null || values.size() == weights.size());
		Preconditions.checkState(!values.isEmpty());
		
		ValWeights[] valWeights = new ValWeights[values.size()];
		double totWeight = 0d;
		for (int j=0; j<valWeights.length; j++) {
			double weight = weights == null ? 1d : weights.get(j);
			totWeight += weight;
			valWeights[j] = new ValWeights(values.get(j), weight);
		}
		
		return calcQuickNormCDF(valWeights, totWeight);
	}

	/**
	 * Quick way to get a normalized CDF if that's all you need, which will be faster for large datasets
	 * than using an actual {@link ArbDiscrEmpiricalDistFunc}.
	 * @param values
	 * @param weights (can be null for even-weighting)
	 * @return
	 */
	public static LightFixedXFunc calcQuickNormCDF(double[] values, double[] weights) {
		Preconditions.checkState(weights == null || values.length == weights.length);
		Preconditions.checkState(values.length > 0);
		
		ValWeights[] valWeights = new ValWeights[values.length];
		double totWeight = 0d;
		for (int j=0; j<valWeights.length; j++) {
			double weight = weights == null ? 1d : weights[j];
			totWeight += weight;
			valWeights[j] = new ValWeights(values[j], weight);
		}
		
		return calcQuickNormCDF(valWeights, totWeight);
	}
	
	private static LightFixedXFunc calcQuickNormCDF(ValWeights[] valWeights, double totWeight) {
		// sort ascending
		Arrays.sort(valWeights);
		int destIndex = -1;
		double[] xVals = new double[valWeights.length];
		double[] yVals = new double[valWeights.length];
		for (int srcIndex=0; srcIndex<valWeights.length; srcIndex++) {
			ValWeights val = valWeights[srcIndex];
			if (destIndex >= 0 && (float)val.val == (float)xVals[destIndex]) {
				// add it, don't increment
				yVals[destIndex] += val.weight;
			} else {
				// move to a new index
				destIndex++;
				xVals[destIndex] = val.val;
				yVals[destIndex] = val.weight;
			}
		}
		int size = destIndex+1;
		if (size < xVals.length) {
			// we have duplicates, trim them
			xVals = Arrays.copyOf(xVals, size);
			yVals = Arrays.copyOf(yVals, size);
		}

		// now convert yVals to a CDF
		double sum = 0d;
		for (int j=0; j<yVals.length; j++) {
			sum += yVals[j];
			yVals[j] = sum/totWeight;
			if (j > 0)
				Preconditions.checkState(xVals[j] > xVals[j-1]);
		}

		return new LightFixedXFunc(xVals, yVals);
	}
	
	/**
	 * Calculates the value at the given fractile from the given normalized CDF.
	 * <p>
	 * Returns NaN if the ncdf only has 1 value, unless the fractile is exactly 0.5 in which case
	 * the sole value will be returned.
	 * 
	 * @param ncdf
	 * @param fractile
	 * @return
	 */
	public static double calcFractileFromNormCDF(LightFixedXFunc ncdf, double fractile) {
		int len = ncdf.size();
		Preconditions.checkState(len > 0, "NormCDF is empty");
		if (len == 1) {
			if ((float)fractile == 0.5f)
				return ncdf.getX(0);
			return Double.NaN;
		}
		double[] yVals = ncdf.getYVals();
		if (fractile == 0d || fractile <= yVals[0]) {
			return ncdf.getX(0);
		} else if (fractile == 1d || fractile >= yVals[len-1]) {
			return ncdf.getX(len-1);
		} else {
			int index = Arrays.binarySearch(yVals, fractile);
			if (index >= 0) {
				// unlikely to actually happen with real data
				return ncdf.getX(index);
			} else {
				// insertion index, value below this will be < fractile, value at will be >
				index = -(index + 1);
				// these cases should have been taken care of above
				Preconditions.checkState(index > 0 && index < len,
						"Unexpected insertion index=%s with len=%s, fractile=%s", index, len, fractile);
				double v1 = ncdf.getX(index-1);
				double v2 = ncdf.getX(index);
				double f1 = ncdf.getY(index-1);
				double f2 = ncdf.getY(index);
				Preconditions.checkState(f1<fractile);
				Preconditions.checkState(f2>fractile);
				return Interpolate.findX(v1, f1, v2, f2, fractile);
			}
		}
	}
	
	private static class ValWeights implements Comparable<ValWeights> {
		double val;
		double weight;
		public ValWeights(double val, double weight) {
			super();
			this.val = val;
			this.weight = weight;
		}
		@Override
		public int compareTo(ValWeights o) {
			return Double.compare(val, o.val);
		}
	}


/*  temp main method to test and to investige numerical precision issues */
public static void main( String[] args ) {
	
	// Uniform distribution test of getMean() and getStdDev() using http://en.wikipedia.org/wiki/Uniform_distribution_(continuous)
	ArbDiscrEmpiricalDistFunc func = new ArbDiscrEmpiricalDistFunc();
	double a = 2;
	double b = 4;
	double lastValue=0;
	for(int i=0; i<100000; i++) {
		double value = Math.random()*(b-a)+a;	// random values between a and b
		func.set(value,1.0); 
		if(i>0) func.set(lastValue,1.0); // random values between a and b
		lastValue = value;
	}
	double trueMean = (b+a)/2;
	double trueStdDev = (b-a)/Math.sqrt(12);
	System.out.println("func.getMaxY()="+func.getMaxY()+"\tfunc.getMinY()="+func.getMinY());
	System.out.println("func.getMean()="+(float)func.getMean()+"\ttrueMean="+(float)trueMean);
	System.out.println("func.getStdDev()="+(float)func.getStdDev()+"\ttrueStdDev="+(float)trueStdDev);


//  ArbDiscrEmpiricalDistFunc func = new ArbDiscrEmpiricalDistFunc();
// /* func.set(0.0,0);
//  func.set(1.0,0);
//  func.set(1.0,1);
//  func.set(2.0,1);
//  func.set(2.0,1);
//  func.set(3.0,1);
//  func.set(3.0,1);
//  func.set(3.0,1);
//  func.set(4.0,5);
//  func.set(4.0,-1);
//  func.set(5.0,5.0);
//  func.set(5.0+1e-15,6.0);
//  func.set(5.0+1e-16,7.0);*/
//  func.set(0.0042254953,0.1);
//  func.set(0.008433135,0.3);
//  func.set(0.02094968,0.1);
//  func.set(0.002148321,.02);
//  func.set(0.0042920266,0.06);
//  func.set(0.010695551,.02);
//  func.set(0.002150044,.02);
//  func.set(0.0042954655,.06);
//  func.set(0.010704094,.02);
//  func.set(0.0021466056,.02);
//  func.set(0.0042886036,.06);
//  func.set(0.010687049,.02);
//  func.set(0.0021485311,.02);
//  func.set(0.004292446,.06);
//  func.set(0.010696594,.02);
//  func.set(0.0021486057,.02);
//  func.set(0.0042925947,.06);
//  func.set(0.0106969625,.02);
//
//  System.out.println("func:");
//  Iterator it = func.iterator();
//  Point2D point;
//  while( it.hasNext()) {
//    point = (Point2D) it.next();
//    System.out.println(point.getX()+"  "+point.getY());
//  }
//
//  System.out.println("\ncumFunc:");
//  ArbitrarilyDiscretizedFunc cumFunc = func.getNormalizedCumDist();
//  it = cumFunc.iterator();
//  while( it.hasNext()) {
//    point = (Point2D) it.next();
//    System.out.println(point.getX()+"  "+point.getY());
//  }
///* */
//  System.out.println("\nFractiles from cumFunc:");
//  System.out.println("0.25: " + cumFunc.getFirstInterpolatedX(0.25));
//  System.out.println("0.5: " + cumFunc.getFirstInterpolatedX(0.5));
//  System.out.println("0.75: " + cumFunc.getFirstInterpolatedX(0.75));
//
//  System.out.println("\nFractiles from method:");
//  System.out.println("0.0: " + func.getInterpolatedFractile(0.0));
//  System.out.println("0.05: " + func.getInterpolatedFractile(0.05));
//  System.out.println("0.25: " + func.getInterpolatedFractile(0.25));
//  System.out.println("0.5: " + func.getInterpolatedFractile(0.5));
//  System.out.println("0.75: " + func.getInterpolatedFractile(0.75));
//  System.out.println("1.0: " + func.getInterpolatedFractile(1.0));

}



    /*
    public void rebuild(){

        // make temporary storage
        ArrayList points = new ArrayList();

        // get all points
        Iterator it = getPointsIterator();
        if( it != null ) while(it.hasNext()) { points.add( (Point2D)it.next() ); }

        // get all non-log points if any
        it = getNonLogPointsIterator();
        if( it != null ) while(it.hasNext()) { points.add( (Point2D)it.next() ); }

        // clear permanent storage
        points.clear();
        nonPositivepoints.clear();

        // rebuild permanent storage
        it = points.listIterator();
        if( it != null ) while(it.hasNext()) { set( (Point2D)it.next() ); }

        if( D ) System.out.println("rebuild: " + toDebugString());
        points = null;
    }

    public boolean isYLog() { return yLog; }
    public void setYLog(boolean yLog) {

        if( yLog != this.yLog ) {
            this.yLog = yLog;
            rebuild();
        }
    }

    public boolean isXLog() { return xLog; }
    public void setXLog(boolean xLog) {
        if( xLog != this.xLog ) {
            this.xLog = xLog;
            rebuild();
        }
    }

    */


}
