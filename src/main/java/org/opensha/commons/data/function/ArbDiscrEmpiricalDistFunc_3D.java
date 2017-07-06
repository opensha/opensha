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

import java.io.Serializable;
import org.opensha.commons.exceptions.InvalidRangeException;



/**
 * <b>Title:</b> ArbDiscrEmpiricalDistFunc<p>
 *
 * <b>Description:</b>  This represents a list of ArbDiscrEmpiricalDistFunc objects,
 * where the indices are represented by a HistogramFunction.  That is, we have a separate
 * ArbDiscrEmpiricalDistFunc object for each x-axis value (the latter in the histogram).
 * 
  *
 * @author Edward H. Field
 * @version 1.0
 */

public class ArbDiscrEmpiricalDistFunc_3D implements Serializable {

    /* Class name Debbuging variables */
    protected final static String C = "ArbDiscrEmpiricalDistFunc_3D";

    /* Boolean debugging variable to switch on and off debug printouts */
    protected final static boolean D = true;
    
    HistogramFunction xAxisHist;
    ArbDiscrEmpiricalDistFunc[] arbDiscrEmpDistFuncArray;

	/**
	 * @param minX
	 * @param numX
	 * @param deltaX
	 */
	public ArbDiscrEmpiricalDistFunc_3D(double min, int num, double delta) {
		this(min, min + (num-1)*delta, num);

	}

	/**
	 * @param minX
	 * @param maxX
	 * @param numX
	 */
	public ArbDiscrEmpiricalDistFunc_3D(double min, double max, int num) {
		xAxisHist = new HistogramFunction(min, max, num);
		arbDiscrEmpDistFuncArray = new ArbDiscrEmpiricalDistFunc[num];
		for(int i=0;i<num;i++) {
			arbDiscrEmpDistFuncArray[i] = new ArbDiscrEmpiricalDistFunc();
		}
	}


	/**
	 * This is for setting the values
	 * @param xVal
	 * @param yVal
	 * @param weight
	 */
	public void set(double xVal, double yVal, double weight) {
		arbDiscrEmpDistFuncArray[xAxisHist.getXIndex(xVal)].set(yVal,weight);
	}

	public HistogramFunction getXaxisHist() {
		return xAxisHist;
	}
	
	public ArbDiscrEmpiricalDistFunc[] getArbDiscrEmpDistFuncArray() {
		return arbDiscrEmpDistFuncArray;
	}

	public double getMinX() {
		return xAxisHist.getMinX();
	}

	public double getMaxX() {
		return xAxisHist.getMaxX();
	}

	public int getNumX() {
		return xAxisHist.size();
	}


	private EvenlyDiscretizedFunc getBaseXaxisFunc() {
		return new EvenlyDiscretizedFunc(getMinX(),getMaxX(),getNumX());
	}
	
	
    /**
     * This returns a curve of the values returned by 
     * ArbDiscrEmpiricalDistFunc.getInterpolatedFractile(double).
      * @param fraction - a value between 0 and 1.
    * @return
     */
    public EvenlyDiscretizedFunc getInterpolatedFractileCurve(double fraction) {

      if(fraction < 0 || fraction > 1)
        throw new InvalidRangeException("fraction value must be between 0 and 1");
      
      EvenlyDiscretizedFunc func = getBaseXaxisFunc();
      for(int i=0; i<func.size(); i++) {
    	  func.set(i,arbDiscrEmpDistFuncArray[i].getInterpolatedFractile(fraction));
      }
      return func;
    }


    /**
     * This returns a curve of the values returned by 
     * ArbDiscrEmpiricalDistFunc.getDiscreteFractile(double).
     * @param fraction - a value between 0 and 1.
     * @return
     */
    public EvenlyDiscretizedFunc getDiscreteFractileCurve(double fraction) {

      if(fraction < 0 || fraction > 1)
        throw new InvalidRangeException("fraction value must be between 0 and 1");

      EvenlyDiscretizedFunc func = getBaseXaxisFunc();
      for(int i=0; i<func.size(); i++) {
    	  func.set(i,arbDiscrEmpDistFuncArray[i].getDiscreteFractile(fraction));
      }
      return func;
    }


    
    
    /**
     * calculates the mean curve for normalized distribution (done simply as a weight average)
     * @return
     */
    public EvenlyDiscretizedFunc getMeanCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getMean());
        }
        return func;
    }
    
    /**
     * Calculates the standard deviation curve for normalized distribution
     * 
     * @return
     */
    public EvenlyDiscretizedFunc getStdDevCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getStdDev());
        }
        return func;
    }
    
    
    /**
     * This returns the coefficient of variation curve (standard deviation divided by the mean)
     * @return
     */
    public EvenlyDiscretizedFunc getCOV_Curve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getStdDev()/arbDiscrEmpDistFuncArray[i].getMean());
        }
        return func;

    }
    
    
    /**
     * Get the apparent mode curve (maximum values).
     * Throws a runtime exception in the case of a multi-modal distribution
     * @return
     */
    public EvenlyDiscretizedFunc getApparentModeCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getApparentMode());
        }
        return func;
    }
    
    /**
     *  Get the most central mode curve in the case of a multi-modal distribution.  
     *  If there is an even number of modes (two central modes) we give back 
     *  the larger of the two.
     * @return
     */
    public EvenlyDiscretizedFunc getMostCentralModeCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getMostCentralMode());
        }
        return func;
    }
    
    
    /**
     * Get the median curve (the  interpolated fractile curve at 0.5 for normalized distribution).
     * @return
     */
    public EvenlyDiscretizedFunc getMedianCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getMedian());
        }
        return func;
    }

    



    public static void main( String[] args ) {
    	
    	ArbDiscrEmpiricalDistFunc_3D test = new ArbDiscrEmpiricalDistFunc_3D(0d,10d,11);
    	System.out.println(test.getMeanCurve());
	
    }


}
