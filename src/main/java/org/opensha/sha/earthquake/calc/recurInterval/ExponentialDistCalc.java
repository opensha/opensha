package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;


/**
 * <b>Title:</b> ExponentialDistCalc.java <p>
 * <b>Description:</p>.
 <p>
 *
 * @author Edward Field
 * @created    July, 2007
 * @version 1.0
 */

public final class ExponentialDistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	
	
	
	public ExponentialDistCalc() {
		NAME = "Exponential";
		super.initAdjParams();
		adjustableParams.removeParameter(aperiodicityParam);	// hide aperiodicity because Exponential does not use this
	}
	
	/**
	 * Alternative without aperiodicity (which this distribution does not depend on)
	 * @param mean
	 * @param deltaX
	 * @param numPoints
	 */
	public void setAll(double mean, double deltaX, int numPoints) {
		super.setAll(mean, Double.NaN, deltaX, numPoints);
	}
	
	/**
	 * Alternative without aperiodicity (which this distribution does not depend on)
	 * @param mean
	 */
	public void setAll(double mean) {
		super.setAll(mean, Double.NaN, DELTA_X_DEFAULT*mean, (int)Math.round(10*mean/deltaX)+1);
	}
	
	
	/*
	 * This computes the PDF and then the cdf from the pdf using 
	 * Trapezoidal integration. 
	 */
	protected void computeDistributions() {
		// clear cached values
		clearCachedDistributions();

		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		
		double rate= 1/mean;
		double t;
		
		for(int i=0; i< pdf.size(); i++) { // skip first point because it's NaN
			t = pdf.getX(i);
			pdf.set(i,rate*Math.exp(-t*rate));
			cdf.set(i,1-Math.exp(-t*rate));
		}
	}
	
	
	/**
	 *  Main method for running tests.
	 */
	public static void main(String args[]) {
		
	}
}

