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

package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import org.opensha.commons.data.Point2DToleranceComparator;
import org.opensha.commons.exceptions.InvalidRangeException;

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
     * (since there is nothing below to interpolate with)
     * @param fraction - a value between 0 and 1.
     * @return
     */
    public double getInterpolatedFractile(double fraction) {

      if(fraction < 0 || fraction > 1)
        throw new InvalidRangeException("fraction value must be between 0 and 1");

      ArbitrarilyDiscretizedFunc tempCumDist = getNormalizedCumDist();

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

      ArbitrarilyDiscretizedFunc tempCumDist = getNormalizedCumDist();

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
    public ArbitrarilyDiscretizedFunc getNormalizedCumDist() {
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
    private ArbitrarilyDiscretizedFunc getCumDist(double totSum) {

      ArbitrarilyDiscretizedFunc cumDist = new ArbitrarilyDiscretizedFunc();
      Point2D dp;
      double sum = 0;
      Iterator<Point2D> it = iterator();
      while (it.hasNext()) {
        dp = it.next();
        sum += dp.getY();
        Point2D dpNew = new Point2D.Double(dp.getX(),sum/totSum);
        cumDist.set(dpNew);
      }
      return cumDist;
    }
    
    

    /**
     * This returns an ArbitrarilyDiscretizedFunc representing the cumulative
     * distribution (sum of Y values less than and equal te each X value).
     * @return
     */
    public ArbitrarilyDiscretizedFunc getCumDist() {
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
