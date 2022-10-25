package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;


/**
 * <b>Title:</b> BPT_DistCalc.java <p>
 * <b>Description:</p>.
 * This represents the Brownian Passage Time renewal model as described by Matthews et al. (2002, BSSA, vol 92, pp. 2223-2250).
 * This adds several "Safe" methods are also supplied to avoid numerical artifacts; see description for each method for more details
 <p>
 *
 * @author Edward Field
 * @created    July, 2007
 * @version 1.0
 */

public final class BPT_DistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	
	// this defines how closely to 0 the denominator of the conditional probability calculation can get
	final static double SAFE_ONE_MINUS_CDF = 1e-13;
	// this defines the smallest duration/mean (values below are computed approximately)
	final static double MIN_NORM_DURATION = 0.01;
	
	double safeTimeSinceLast=Double.NaN;
	
	public BPT_DistCalc() {
		NAME = "BPT";
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
		
		double temp1 = mean/(2.*Math.PI*(aperiodicity*aperiodicity));
		double temp2 = 2.*mean*(aperiodicity*aperiodicity);
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
			pd = Math.sqrt(temp1/(t*t*t)) * Math.exp(-(t-mean)*(t-mean)/(temp2*t));
			if(Double.isNaN(pd)){
				pd=0;
				System.out.println("pd=0 for i="+i);
			}
			cd += deltaX*(pd+pdf.getY(i-1))/2;  // Trapizoidal integration
			pdf.set(i,pd);
			cdf.set(i,cd);
		}
		computeSafeTimeSinceLastCutoff();
		upToDate = true;
	}

	

	/**
	 * This computed the conditional probability using Trapezoidal integration (slightly more
	 * accurrate that the WGCEP-2002 code, which this method is modeled after). Although this method 
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
	 * This is a version of the parent method getCondProb(*) that avoids numerical artifacts
	 * at high timeSinceLast (where 1-cdf gets too close to 0, and we therefore have division by 
	 * zero).
	 * 
	 * We know that at infinite time the mean residual life (expected time to next earthquake) for BPT 
	 * approaches the following asymptotically:
	 * 
	 * 			mrl = 2*mean*aperiodicity*aperiodicity
	 * 
	 * (Equation 24 of Matthews et al. (2002, BSSA, vol 92, pp. 2223-2250)), so the conditional probability
	 * becomes 1-exp(duration/mrl) at large timeSinceLast.  We also define a safeTimeSinceLast as that where 
	 * 1-cdf becomes close to zero (<= SAFE_ONE_MINUS_CDF).  Beyond this point we assume the conditional 
	 * probability varies linearly between the value at safeTimeSinceLast and 1-exp(duration/mrl), where
	 * "infinite time" is taken as timeSinceLast/mean = 10.  While this choice of 10 is somewhat arbitrary,
	 * differences are only slight, and mostly only for low aperiodicity (<0.15).  Also keep in mind that
	 * the probability of getting beyond safeTimeSinceLast is SAFE_ONE_MINUS_CDF (e.g., 1e-13), so we are
	 * way out on the tail, where application of the model will always be speculative anyway.
	 * 
	 * The above does not solve all numerical problems, as we can get instability before safeTimeSinceLast
	 * where p2-p1 in the parent method becomes too small (e.g., 1e-14), which occurs when duration/mean is
	 * <= 1e-3 (and again, only for aperiodicities <= 0.2).  To avoid this problem, the smallest duration/mean
	 * for which conditional probability is computed is MIN_NORM_DURATION, with values below this simply being
	 * the value for MIN_NORM_DURATION scaled by (duration/mean)/MIN_NORM_DURATION.  The testSafeCalcs show that
	 * this produces conditional probabilities within 0.1% of true values where these numerical artifacts are 
	 * lacking, and shows that discrepancies elsewhere are for very un-probable states (e.g., just below
	 * safeTimeSinceLast); nonetheless, this fixes these other numerical artifacts.
	 * 
	 * @param timeSinceLast
	 * @param duration
	 * @return
	 */
	public double getCondProb(double timeSinceLast, double duration) {
		this.duration=duration;
		if(!upToDate) computeDistributions();
		
		double result=Double.NaN;
		
		if(duration/mean >= MIN_NORM_DURATION*0.9999) {	// the last bit is to avoid roundoff errors that cause infinite loop
//			System.out.println("good");
			if(timeSinceLast+duration <= safeTimeSinceLast) {
				result = super.getCondProb(timeSinceLast, duration);	// use super method
			}
			else {
				if(safeTimeSinceLast-duration*1.0001<0)
					return 1.0; // very long duration
				double condProbAtSafeTime = super.getCondProb(safeTimeSinceLast-duration*1.0001, duration);	// 1.0001 is needed because safeTimeSinceLast-duration+duration != safeTimeSinceLast inside this method
				double condProbAtInfTime = 1-Math.exp(-duration/(aperiodicity*aperiodicity*mean*2)); // based on Equation 24 of Matthews et al. (2002).
				if(timeSinceLast+duration>cdf.getMaxX())
					return condProbAtInfTime;

				// linear interpolate assuming inf time is mean*10
				result = condProbAtSafeTime + (condProbAtInfTime-condProbAtSafeTime)*(timeSinceLast-(safeTimeSinceLast-duration))/(10*mean-(safeTimeSinceLast-duration));
//				if(result<0)
//					System.out.println("found it: "+result+"\t"+timeSinceLast+"\t"+duration);
			}			
		}
		else {
//			System.out.println("bad");
			double condProbForMinNormDur = getCondProb(timeSinceLast, MIN_NORM_DURATION*mean); // this will temporarily override this.duration, so we need to fix this below
			result = condProbForMinNormDur*duration/(MIN_NORM_DURATION*mean);
			 this.duration=duration;
		}
		
		 return result;

	}	
	
	
	/**
	 * Overrides name and info assigned in parent
	 * @return
	 */
	public EvenlyDiscretizedFunc getCondProbFunc() {
		EvenlyDiscretizedFunc func = super.getCondProbFunc();
		func.setName(NAME+" Safe Conditional Probability Function");
		func.setInfo(adjustableParams.toString()+"\n"+"safeTimeSinceLast="+safeTimeSinceLast);
		return func;
	}
	
	/**
	 * This computes the probability of an event over the specified duration for the case where the 
	 * date of last event is unknown (looping over all possible values), but where the historic open 
	 * interval is applied (the latter defaults to zero if never set).
	 * 
	 * This avoids numerical artifacts in the parent method a couple ways.
	 * 
	 * First, if histOpenInterval>=safeTimeSinceLast,  we simply return the conditional probability at
	 * safeTimeSinceLast (technically we should weight average the values beyond, but they are
	 * nearly constant, the weights are decaying beyond (so that at safeTimeSinceLast is highest),
	 * and the likelihood of histOpenInterval ever getting to safeTimeSinceLast is vanishingly
	 * small (as noted in the doc for the getCondProb method here)).
	 * 
	 * A less efficient way of calculating this cond probability is also used if unstable results are
	 * expected (see details regarding "numer" and "denom" below).
	 * 
	 * @return
	 */
	public double getCondProbForUnknownTimeSinceLastEvent() {
		if(!upToDate) computeDistributions();

		double condProbAtSafeTime = this.getCondProb(safeTimeSinceLast,duration);
		if(histOpenInterval>=safeTimeSinceLast) {
			return condProbAtSafeTime;
		}
		
		// if first and last are same, don't need to weight average any
		double diffFromFirst = Math.abs((condProbAtSafeTime-getCondProb(histOpenInterval,duration))/condProbAtSafeTime);
		if(diffFromFirst < 1e-4)  {
			return condProbAtSafeTime;
		}
		
		// this is the faster calculation:
		if(integratedCDF==null) 
			makeIntegratedCDFs();
		double lastTime = histOpenInterval+duration;
		if(lastTime>integratedCDF.getMaxX())
			lastTime=integratedCDF.getMaxX();
		double numer = duration - (integratedCDF.getInterpolatedY(lastTime)-integratedCDF.getInterpolatedY(histOpenInterval));
		double denom = (integratedOneMinusCDF.getY(numPoints-1)-integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
		double result = numer/denom;
		
		if(result>1) {
			result=1;
		}
		
		// test stuff
//		double logNormDur = Math.log10(duration/mean);
//		double logNormHoi = Math.log10(histOpenInterval/mean);
//		System.out.println(logNormDur+"\t"+logNormHoi+"\t"+result+"\t"+numer+"\t"+denom+"\t"+(numer > 1e-10 && denom > 1e-10)+"\t"+duration+"\t"+integratedCDF.getInterpolatedY(lastTime)+"\t"+integratedCDF.getInterpolatedY(histOpenInterval));
		
		// avoid numerical artifacts found when the following are not satisfied
		if(numer > 1e-10 && denom > 1e-10) {
//System.out.println(histOpenIntParam.getValue()+"\t"+result+"\t"+integratedCDF.getInterpolatedY(lastTime)+"\t"+integratedCDF.getInterpolatedY(histOpenInterval)+"\tnumer > 1e-10 && denom > 1e-10");
//System.out.println("lastTime="+lastTime+"\thistOpenInterval="+histOpenInterval+"\n"+integratedCDF.toString());
			return result;
		}
		else {
			result=0;
			double normDenom=0;
			EvenlyDiscretizedFunc condProbFunc = getCondProbFunc();
			int firstIndex = condProbFunc.getClosestXIndex(histOpenInterval);
			int indexOfSafeTime = condProbFunc.getClosestXIndex(safeTimeSinceLast);	// need to use closest because condProbFunc has fewer points than CDF (so safeTimeSinceLast can exceed the x-axis range)
	
//			for(int i=firstIndex;i<condProbFunc.getNum();i++) {
			for(int i=firstIndex;i<=indexOfSafeTime;i++) {
				double probOfTimeSince = (1-cdf.getY(i));
				if(i==firstIndex)
					probOfTimeSince *= ((cdf.getX(i)+deltaX/2.0) - histOpenInterval)/deltaX;	// fraction of first bin
				normDenom+=probOfTimeSince; 
				result+= condProbFunc.getY(i)*probOfTimeSince;
			}
			result /= normDenom;	// normalize properly
			
			if(result>1) {
				result=1;	// avoid slightly greater than zero problems
			}

			return result;
		}
		
	}

	
	/**
	 * This returns the maximum value of timeSinceLast (as discretized in the x-axis of the cdf) that is  
	 * numerically safe (to avoid division by zero in the conditional probability calculations, where the
	 * denominator is 1-cdf). This returns Double.Nan if no x-axis values are safe (not even the first ones).  
	 * The threshold for safe values was found by trial and error and checked for aperiodicity values between 
	 * 0.1 and 1.0 (using the GUI).
	 * @return
	 */
	public double getSafeTimeSinceLastCutoff() {
		if(!upToDate) computeDistributions();
		return safeTimeSinceLast;
	}
	
	/**
	 * This finds the largest x-axis value such that (1.0-cdf.getY(x)) >= SAFE_ONE_MINUS_CDF
	 * (not too close to zero, as this is the denominator of the conditional probability calculation)
	 */
	private void computeSafeTimeSinceLastCutoff() {
		safeTimeSinceLast = Double.NaN;
		for(int x=0;x<cdf.size();x++) {
			if(1.0-cdf.getY(x) < SAFE_ONE_MINUS_CDF) {	// when cdf gets too close to 1, keep last safeTimeSinceLast
				break;
			}
			else {
				safeTimeSinceLast = cdf.getX(x);
			}
		}
		
		if(Double.isNaN(safeTimeSinceLast)) {
			throw new RuntimeException ("CDF never gets close to 1.0; need to increase numPoints?");
		}
		
//		System.out.println("safeTimeSinceLast="+safeTimeSinceLast);
	}
	
	
	/**
	 * This tests the difference between values obtained using the local and parent
	 * getCondProbFunc(*) methods for cases within safeTimeSinceLast, where the fix 
	 * for small normalized durations is applied in the local method.  This prints all
	 * cases that differ by more than 0.1%.  All those printed are indeed the problems
	 * we sought to avoid, and are very rare circumstances anyway.
	 */
	public void testSafeCalcs() {
		
		double[] durations = {1,0.1,0.01,0.001,0.0001};
		double[] aperiodicities = {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0};
		
		System.out.println("fractDiff\tsafeProb\tprob\taper\ttimeSince\tdur\tsafeDist");

		for(double aper: aperiodicities) {
			for(double dur:durations) {
				setAll(1.0, aper, 0.01, 1000,dur);
				double safeDist = getSafeTimeSinceLastCutoff();
//				EvenlyDiscretizedFunc safeCondProbFunc = getCondProbFunc();	// these two lines don't work because the both call local method
//				EvenlyDiscretizedFunc condProbFunc = super.getCondProbFunc();
//				System.out.println(safeCondProbFunc.getName());
//				System.out.println(condProbFunc.getName());

				EvenlyDiscretizedFunc condProbFuncOnlyForXvalues = super.getCondProbFunc();

				for(int i=0;i<condProbFuncOnlyForXvalues.size();i++) {
					double timeSince = condProbFuncOnlyForXvalues.getX(i);
					if(timeSince<safeDist) {
//						double safeProb = safeCondProbFunc.getY(i);
//						double prob = condProbFunc.getY(i);
						double safeProb = this.getCondProb(timeSince, dur);
						double prob = super.getCondProb(timeSince, dur);
						double fractDiff;
						if(prob<1e-16 && safeProb<1e-16) {
							fractDiff=0.0;
						}
						else {
							fractDiff = Math.abs((safeProb-prob)/safeProb);
						}
						if(fractDiff > 0.001) {
							System.out.println(fractDiff+"\t"+safeProb+"\t"+prob+"\t"+aper+"\t"+timeSince+"\t"+dur+"\t"+safeDist);
						}
					}
				}
			}			
		}
	}

	
	/**
	 *  Main method for running tests.  
	 *  Test1 compares the static getCondProb(*) method against values obtained directly from the WGCEP-2002 
	 *  code; all are within 0.3%.
	 *  Test2 campares the non static getCondProb(*) method against the static; all are within 0.5%.  The 
	 *  differences is that the non-static is slightly more accurate due to interpolation of the CDF
	 *  (exact same values are obtained otherwise; see commented out code).
	 *  Test3 is the non-static used more efficiently (exact same values as from non-static above); this
	 *  is about a factor of two faster.
	 *  Test4 examines what happens if delta is changed to 0.01 in the non-static method (also about
	 *  a factor of two faster).
	 */
	public static void main(String args[]) {
		
		BPT_DistCalc calcBPT = new BPT_DistCalc();
		
//		Prob2	0.12885578333291614	0.93359375	0.8132947960290353	0.2	49518.068970547385	6855.846444493884	NaN	185929.96653606958	0.1384514094960294	NaN	3.754790330104713
//		double deltaX = 49518.068970547385/200d;
//		double deltaX_alt = 247.5903449;	
//		System.out.println(deltaX+"\t"+deltaX_alt);
//		calcBPT.setAll(49518.068970547385, 0.2, deltaX, 1800, 6855.846444493884, 185929.96653606958);
//		System.out.println(calcBPT.getCondProbForUnknownTimeSinceLastEvent());
//		calcBPT.setAll(49518.068970547385, 0.2, deltaX_alt, 1800, 6855.846444493884, 185929.96653606958);
//		System.out.println(calcBPT.getCondProbForUnknownTimeSinceLastEvent());
//		calcBPT.setAll(1.0, 0.2, 0.005, 1800, 0.1384514094960294, 3.754790330104713);
//		System.out.println(calcBPT.getCondProbForUnknownTimeSinceLastEvent());
		
		calcBPT.testSafeCalcs();
		System.exit(0);

		// test data from WGCEP-2002 code run (single branch for SAF) done by Ned Field
		// in Feb of 2006 (see his "Neds0206TestOutput.txt" file).

		double timeSinceLast = 96;
		double nYr = 30;
		double alph = 0.5;
		double[] rate = {0.00466746464,0.00432087015,0.004199435,0.004199435};
		double[] prob = {0.130127236,0.105091952,0.0964599401,0.0964599401};
/*
		// this is a test of a problematic case (which let to a bug identification).  This case
		// shows a 4% difference between the static and non-static methods due to interpolation
		double timeSinceLast = 0.247;
		double nYr = 0.0107;
		double alph = 0.5;
		double[] rate = {1};
		double[] prob = {8.3067856E-4}; // this is the value given by the static method
		double timeSinceLast = 115;
//		double nYr = 5;
//		double alph = 0.5;
//		double[] rate = {0.002149};
//		double[] prob = {8.3067856E-4}; // this is the value given by the static method
*/

		// Test1
		double[] static_prob = new double[rate.length];
		double p;
		System.out.println("Test1: static-method comparison with probs from WG02 code");
		for(int i=0;i<rate.length;i++) {
			p = getCondProb(1/rate[i],alph, timeSinceLast, nYr);
			System.out.println("Test1 (static): prob="+(float)p+"; ratio="+(float)(p/prob[i]));
			static_prob[i]=p;
		}

		
		BPT_DistCalc calc = new BPT_DistCalc();
		
		// Test2
		double[] nonStatic_prob = new double[rate.length];

		System.out.println("Test2: non-static method compared to static");
		for(int i=0;i<rate.length;i++) {
			calc.setAll((1/rate[i]),alph);
			p = calc.getCondProb(timeSinceLast,nYr);
			System.out.println("Test2: prob="+(float)p+"; ratio="+(float)(p/static_prob[i]));
			nonStatic_prob[i]=p;
		}

		/*
		// Test3
		System.out.println("Test3: non-static method used efficiently compared to non-static");
		calc.setAll(1,alph);
		for(int i=0;i<rate.length;i++) {
			p = calc.getCondProb(timeSinceLast*rate[i],nYr*rate[i]);
			System.out.println("Test3: prob="+(float)p+"; ratio="+(float)(p/nonStatic_prob[i]));
		}
		
		
		// Speed tests
		// First the static method
		long milSec0 = System.currentTimeMillis();
		int numCalcs = 10000;
		for(int i=0; i< numCalcs; i++)
			p = getCondProb(1/rate[0],alph,timeSinceLast,nYr);
		double time = (double)(System.currentTimeMillis()-milSec0)/1000;
		System.out.println("Speed Test for static method = "+(float)time+" sec");
		// now the faster way
		milSec0 = System.currentTimeMillis();
		calc.setAll(1,alph);
		for(int i=0; i< numCalcs; i++)
			p = calc.getCondProb(timeSinceLast*rate[0],nYr*rate[0]);
		double time2 = (double)(System.currentTimeMillis()-milSec0)/1000;
		System.out.println("Speed Test for non-static used efficiently = "+(float)time2+" sec");
		System.out.println("Ratio of non-static used efficiently to static = "+(float)(time2/time));
		
		
		// test the delta=0.01 case
		System.out.println("Test4: comparison of non-static and non static w/ delta=0.01");
		for(int i=0;i<rate.length;i++) {
			double mri = 1/rate[i];
			int num = (int)(10*alph*mri/0.01);
			calc.setAll(mri,alph,0.01,num);
			p = calc.getCondProb(timeSinceLast,nYr);
			System.out.println("Test4 (delta=0.01): ="+(float)p+"; ratio="+(float)(p/nonStatic_prob[i]));
		}

		// Another Speed test
		milSec0 = System.currentTimeMillis();
		calc.setAll(1,alph);
		for(int i=0; i< numCalcs; i++)
			p = calc.getCondProb(timeSinceLast*rate[0],nYr*rate[0]);
		double time3 = (double)(System.currentTimeMillis()-milSec0)/1000;
		System.out.println("Speed Test for deltaX = 0.01 & non static used effieicintly = "+(float)time3+" sec");
		System.out.println("Ratio of compute time above versus static  = "+(float)(time3/time));
		*/
	}
	

}

