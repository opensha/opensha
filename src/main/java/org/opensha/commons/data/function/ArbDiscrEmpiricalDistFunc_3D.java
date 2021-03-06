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
	
	/**
	 * This is for setting the values
	 * @param xInde
	 * @param yVal
	 * @param weight
	 */
	public void set(int xIndex, double yVal, double weight) {
		arbDiscrEmpDistFuncArray[xIndex].set(yVal,weight);
	}
	
	public void set(EvenlyDiscretizedFunc func, double weight) {
		if(func.getMinX() != xAxisHist.getMinX() || func.getDelta() != xAxisHist.getDelta() || func.size() != xAxisHist.size()) {
			throw new RuntimeException("Functions are not consistent");
		}
		for(int i=0;i<func.size();i++)
			set(func.getX(i), func.getY(i), weight);
		
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
     * This returns a curve for Mean + x*StdDev
     * @param x
     * @return
     */
    public EvenlyDiscretizedFunc getMeanPlusXstdDevCurve(double x) {
        EvenlyDiscretizedFunc mean = getMeanCurve();
        EvenlyDiscretizedFunc stdDev = getStdDevCurve();
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,mean.getY(i)+x*stdDev.getY(i));
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
     * This gets the minimum value curve (ignoring weights)
     * @return
     */
    public EvenlyDiscretizedFunc getMinCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getMinX());
        }
        return func;
    }
    
    /**
     * This gets the maximum value curve (ignoring weights)
     * @return
     */
    public EvenlyDiscretizedFunc getMaxCurve() {
        EvenlyDiscretizedFunc func = getBaseXaxisFunc();
        for(int i=0; i<func.size(); i++) {
      	  func.set(i,arbDiscrEmpDistFuncArray[i].getMaxX());
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
