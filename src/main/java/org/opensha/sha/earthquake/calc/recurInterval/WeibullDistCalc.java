package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;

import cern.jet.stat.tdouble.Gamma;


/**
 * <b>Title:</b> WeibullDistCalc.java <p>
 * <b>Description:</p>.
 * Based on the equations given at http://en.wikipedia.org/wiki/Weibull_distribution
 <p>
 *
 * @author Edward Field
 * @created    Dec, 2012
 * @version 1.0
 */

public final class WeibullDistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	 
	
	
	public WeibullDistCalc() {
		NAME = "Weibull";
		super.initAdjParams();
	}
	
	
	/*
	 * This computes the PDF and then the cdf from the pdf using 
	 * Trapezoidal integration. 
	 */
	protected void computeDistributions() {
		
		// make these null
		integratedCDF = null;
		integratedOneMinusCDF = null;

		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		// set first y-values to zero
		pdf.set(0,0);
		cdf.set(0,0);
		
		// convert aperiodicity to little gamma
		double k = getShapeParameter(aperiodicity);
		double gamma1 = Math.exp(Gamma.logGamma(1d+1d/k));
		double lambda = mean/gamma1;
// System.out.println("k="+k+"\nlambda="+lambda+"\ngamma1="+gamma1);
		
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
			pd = (k/lambda)*Math.pow(t/lambda,k-1)*Math.exp(-1*Math.pow(t/lambda, k));
			if(Double.isNaN(pd)){
				pd=0;
				System.out.println("pd=0 for i="+i);
			}
			cd += deltaX*(pd+pdf.getY(i-1))/2;  // Trapizoidal integration
			pdf.set(i,pd);
			cdf.set(i,cd);
		}
		upToDate = true;
	}

	/**
	 * This assumes the shape parameter (k) is between 1 (exponential distribution) and 5 (COV = 0.052).
	 * Final value has better than 1% accuracy.
	 * @param cov - coefficient of variation
	 * @return
	 */
	private static double getShapeParameter(double cov) {
		
		double best_k = Double.NaN;
		double minDiff = Double.POSITIVE_INFINITY;
		for(int i=0; i<400; i++) {
			double test_k = 1.0+i*0.01;
			double bigGamma1 = Math.exp(Gamma.logGamma(1d+1d/test_k));
			double bigGamma2 = Math.exp(Gamma.logGamma(1d+2d/test_k));
			double testCOV = Math.sqrt(bigGamma2/(bigGamma1*bigGamma1) -1);
			double diff = Math.abs(testCOV-cov);
			if(diff<minDiff) {
				minDiff = diff;
				best_k = test_k;
			}
		}
		return best_k;
	}
	
	

	
	/**
	 *  Main method for running tests.
	 */
	public static void main(String args[]) {
		
	}
}

