package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;


/**
 * <b>Title:</b> LognormalDistCalc.java <p>
 * <b>Description:</p>.
 <p>
 *
 * @author Edward Field
 * @created    July, 2007
 * @version 1.0
 */

public final class LognormalDistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	 
	
	
	public LognormalDistCalc() {
		NAME = "Lognormal";
		super.initAdjParams();
	}
	

	/**
	 * This computes the fractional uncertainty, fu, for the given confidence bounds, which only depends
	 * on aperiodicity (COV) for a lognormal distribution.  For example, fu = ??? for aperiodicity=0.3 and 95% 
	 * confidence bounds, which means the 95% confidence bounds are mean/fu and mean*fu.
	 * 
	 * @param confBoundsFraction - e.g., set as 0.95 for 95% confidence bounds
	 * @return
	 */
	public double getFractionalUncertaintyForConfBounds(double confBoundsFraction) {
		double fractUncert = 0;
		this.getCDF(); // make sure it's freshly computed
		for(double factor=1.001; factor<9.999; factor+=0.001) {
			fractUncert = cdf.getInterpolatedY(mean*factor) - cdf.getInterpolatedY(mean/factor);
			if(fractUncert>confBoundsFraction) {
				fractUncert = factor;
				break;
			}
		}
		return fractUncert;
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
		
		// convert mean and aperiodicity to mu and sigma
		double sigma = Math.sqrt(Math.log(aperiodicity*aperiodicity+1));
//		double mu = Math.log(mean/Math.exp(sigma*sigma/2));
		double mu = Math.log(mean)-(sigma*sigma/2);
		
		double temp1 = sigma*Math.sqrt(2.0*Math.PI);
		double temp2 = 2.0*sigma*sigma;
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
			pd = Math.exp(-(Math.log(t)-mu)*(Math.log(t)-mu)/temp2)/(temp1*t);
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
	 *  Main method for running tests.
	 */
	public static void main(String args[]) {
		
	}
}

