package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.Interpolate;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> BPT_DistCalc.java <p>
 * <b>Description:</p>.
 * This represents the Brownian Passage Time renewal model as described by Matthews et al. (2002, BSSA, vol 92, pp. 2223-2250).
 <p>
 *
 * @author Edward Field
 * @created    July, 2007
 * @version 1.0
 */

public final class BPT_DistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	
		
	public BPT_DistCalc() {
		NAME = "BPT";
		super.initAdjParams();
	}
	
	
	/*
	 * This computes the PDF and then the cdf from the pdf using 
	 * Trapezoidal integration. 
	 */
	@Override
	protected void computeDistributions() {
		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		// set first y-value to zero
		pdf.set(0,0);
		
		double temp1 = mean/(2.*Math.PI*(aperiodicity*aperiodicity));
		double temp2 = 2.*mean*(aperiodicity*aperiodicity);
		double t,pd;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = pdf.getX(i);
			pd = Math.sqrt(temp1/(t*t*t)) * Math.exp(-(t-mean)*(t-mean)/(temp2*t));
			if(Double.isNaN(pd)){
				pd=0;
//				System.out.println("pd=0 for i="+i);
			}
			pdf.set(i,pd);
		}
		cdf = computeCDFfromPDF(pdf);
	}
	


	/**
	 * This computed the conditional probability using Trapezoidal integration (slightly more
	 * accurate that the WGCEP-2002 code, which this method is modeled after). Although this method 
	 * is static (doesn't require instantiation), it is less efficient than the non-static version 
	 * here (it is also very slightly less accurate because the other interpolates the cdf). 
	 * Note also that if timeSinceLast/mean > aperiodicity*10, timeSinceLast is changed to equal
	 * mean*aperiodicity*10 (to avoid numerical problems at high timeSinceLast). 
	 * @param timeSinceLast - time since last event
	 * @param rate - average rate of events
	 * @param alpha - coefficient of variation (technically corrrect??)
	 * @param duration - forecast duration
	 * @return
	 */
	public static double getCondProb(double mean, double aperiodicity, double timeSinceLast, double duration) {
		validateDuration(duration);
		double step = 0.001;
		double cdf=0, pdf, pdf_last=0, t, temp1, temp2, x, cBPT1=0, cBPT2;
		int i, i1, i2;
		
		// avoid numerical problems when too far out on tails
		if ( timeSinceLast/mean > aperiodicity*10 )
			x = 10.*aperiodicity*mean;
		else
			x = timeSinceLast;
		
		// find index of the two points in time
		i1 = Math.round((float)((x/mean)/step));
		i2 = Math.round((float)(((x+duration)/mean)/step));
		
		temp1 = 1/(2.*Math.PI*(aperiodicity*aperiodicity));
		temp2 = 2.*(aperiodicity*aperiodicity)*1;
		t = step*1.;
		for(i=1; i<=i2; i++) {
			pdf = Math.sqrt(temp1/(t*t*t)) * Math.exp(-(t-1)*(t-1) / (temp2*t) );
			
			cdf += step*(pdf+pdf_last)/2;
			if ( i == i1 ) cBPT1 = cdf;
/*
			if ( i == i1 || i == i2) {
				System.out.println("time = "+t);
			}
			System.out.println(i+"\t"+t+"\t"+pdf+"\t"+cdf);
*/
			t += step;
			pdf_last=pdf;
		}
		cBPT2 = cdf;
		
		if ( cBPT1 >= 1.0 )
			return Double.NaN;
		else
			return (cBPT2-cBPT1)/( 1.-cBPT1);
		
	}	
	
	/**
	 *  Main method for running tests.  
	 *  
	 */
	public static void main(String args[]) {
		
		BPT_DistCalc calcBPT = new BPT_DistCalc();
		

	}
	

}

