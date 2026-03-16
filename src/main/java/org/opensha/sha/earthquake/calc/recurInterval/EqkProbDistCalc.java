package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.UnmodifiableEvenlyDiscrFunc;
import org.opensha.commons.data.function.DiscretizedFuncInterpolator;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> EqkProbDistCalc.java <p>
 * <b>Description:</p>.
 * This abstract class represents the core functionality for computing earthquake probabilities for various renewal models 
 * from the mean, aperiodicity, and other parameters.  Each subclass (one for each renewal model) simply computes the pdf 
 * and cdf in their respective implementations of the computeDistributions() method defined here.  Most other calculations 
 * are performed here from the pdf and cdf (except where there are analytical functions for specific renewal models that
 * avoid numerical artifacts; see Weibull).
 * 
 * The units of time are arbitrary (they just need to be consistent among parameters)
 * 
 * These classes have not been fully tested in terms of numerical accuracy (beyond that described below), so be sure to
 * test for your purposes using the GUI or by generating plots (as exemplified below).
 * 
 * This class was updated in 2026 to provide numerical stability for Weibull and Lognormal (generalizing previous 
 * BPT-specific methods and tests (now deleted)).
 * 
 * Numerical precision is specified by the NUMERICAL_PRECISION, and the safeTimeSinceLast is defined as the point where
 * 1.0-CDF = NUMERICAL_PRECISION (survivor function).  NUMERICAL_PRECISION is used a few other places as well.
 * 
 * The "extrapolate" option on some methods extends the last numerically safe conditional probability out to higher
 * times since last event; otherwise Double.NaN is returned.
 * 
 * The above safety measures were evaluated by generating and reviewing an extensive set of plots for parameter 
 * values used in 2026 NSHM TD calculations 
 * (see scratch.ned.longTermTD2026.LongTermTD_2026_Analyses.generateRenewalModelPlots(boolean).
 * 
 * A method could/should be added for sampling random values?
 * 
 <p>
 *
 * @author Edward Field
 * @created    July, 2007; updated several times since
 * @version 1.0
 */

public abstract class EqkProbDistCalc implements ParameterChangeListener {
	
	final static boolean D = false;	// debugging flag
	
	// this was verified
	final static double NUMERICAL_PRECISION = 1e-10;  // Preferred minimum survival value
//	final static double NUMERICAL_PRECISION = 1e-11;  // minimum survival value
//	final static double NUMERICAL_PRECISION = 1e-12;  // minimum survival value
	double safeTimeSinceLast=Double.NaN;
	int safeTimeSinceLastIndex=-1;

	
	// distributions
	protected EvenlyDiscretizedFunc pdf, cdf, integratedCDF, integratedOneMinusCDF;
	// for efficient interpolation of the CDF
	protected DiscretizedFuncInterpolator interpCDF;
	protected DiscretizedFuncInterpolator interpIntegratedCDF;
	protected DiscretizedFuncInterpolator interpIntegratedOneMinusCDF;
	
	protected double mean, aperiodicity, deltaX;
	protected int numPoints;
	protected boolean interpolate = true;
	/**
	 *  if interpolate == false but the 2 indexes in getCondProb are within this many bins of each other,
	 *  force interpolation to increase resolution
	 */
	protected int maxBinsAwayToForceInterpolate = 10;
	public static final double DELTA_X_DEFAULT = 0.001;
	private volatile boolean upToDate = false;
	protected  String NAME;
	protected String commonInfoString;
	private boolean unmodifiable = false;
	
	// TODO create and get these from ../../../param (hist open interval is already there)
	// Parameter names
	public final static String MEAN_PARAM_NAME= "Mean";
	public final static String APERIODICITY_PARAM_NAME = "Aperiodicity";
	public final static String DELTA_X_PARAM_NAME = "Delta T";
	public final static String NUM_POINTS_PARAM_NAME = "Num Points";
	
	// Parameter Infos
	protected final static String MEAN_PARAM_INFO= "Mean";
	protected final static String APERIODICITY_PARAM_INFO = "Aperiodicity is the standard deviation divided by the mean ";
	protected final static String DELTA_X_PARAM_INFO = "The time discretization for the distribution";
	protected final static String NUM_POINTS_PARAM_INFO = "The number of points for the distribution";
	
	// default param values
	protected final static Double DEFAULT_MEAN_PARAM_VAL = Double.valueOf(1);
	protected final static Double DEFAULT_APERIODICITY_PARAM_VAL = Double.valueOf(0.4);
	protected final static Double DEFAULT_DELTAX_PARAM_VAL = Double.valueOf(5e-5);
	protected final static Integer DEFAULT_NUMPOINTS_PARAM_VAL = Integer.valueOf(200001);
	
	// various adjustable params
	protected DoubleParameter meanParam, aperiodicityParam, deltaX_Param;
	protected IntegerParameter numPointsParam;
	
	// adjustable parameter list
	protected ParameterList adjustableParams;
	
	void clearCachedDistributions() {
		// distributions
		pdf = null;
		cdf = null;
		integratedCDF = null;
		integratedOneMinusCDF = null;
		// interpolators
		interpCDF = null;
		interpIntegratedCDF = null;
		interpIntegratedOneMinusCDF = null;
		safeTimeSinceLast=Double.NaN;
		safeTimeSinceLastIndex=-1;
	}

	/**
	 * The method is where subclasses are to compute the pdf and cdf for the given parameters (mean, aperiodicity, delta).
	 * 
	 * This will be called by {@link #ensureUpToDate()} if {@link #upToDate} is false, first calling
	 * {@link #clearCachedDistributions()} and then finally setting {@link #upToDate} to true.
	 */
	abstract void computeDistributions();
	
	/**
	 * Ensures that {@link #computeDistributions()} is called if any distributions are stale. If the integrated flag
	 * is set, also ensures that {@link #makeIntegratedCDFs()} has been called.
	 */
	final void ensureUpToDate(boolean integrated) {
		if (!upToDate || (integrated && integratedCDF == null)) {
			synchronized (this) {
				if (!upToDate) {
					Preconditions.checkState(!unmodifiable,
							"%s is set to be unmodifiable, can't rebuild distributions", NAME);
					clearCachedDistributions();
					computeDistributions();
					computeSafeTimeSinceLastCutoff();
					upToDate = true;
				}
				if (integrated && (integratedCDF == null || integratedOneMinusCDF == null)) {
					makeIntegratedCDFs();
				}
			}
		}
	}
	
	/**
	 * Ensures that all distributions have been computed, and then sets them to be unmodifiable. This ensures that
	 * values can never change when a distribution calculator is shared, e.g., in a multi-threaded calculation.
	 */
	public synchronized void setUnmodifiable() {
		ensureUpToDate(true);
		if (!(pdf instanceof UnmodifiableEvenlyDiscrFunc))
			pdf = new UnmodifiableEvenlyDiscrFunc(pdf);
		if (!(cdf instanceof UnmodifiableEvenlyDiscrFunc)) {
			cdf = new UnmodifiableEvenlyDiscrFunc(cdf);
			interpCDF = null; // make sure this is rebuilt with the final unmodifiable version
		}
		if (!(integratedCDF instanceof UnmodifiableEvenlyDiscrFunc)) {
			integratedCDF = new UnmodifiableEvenlyDiscrFunc(integratedCDF);
			interpIntegratedCDF = null; // make sure this is rebuilt with the final unmodifiable version
		}
		if (!(integratedOneMinusCDF instanceof UnmodifiableEvenlyDiscrFunc)) {
			integratedOneMinusCDF = new UnmodifiableEvenlyDiscrFunc(integratedOneMinusCDF);
			interpIntegratedOneMinusCDF = null; // make sure this is rebuilt with the final unmodifiable version
		}
		unmodifiable = true;
	}
	
	/**
	 * @return true if {@link #setUnmodifiable()} has been called.
	 */
	public boolean isUnmodifiable() {
		return unmodifiable;
	}
	
	/**
	 * Ensures that we're up to date by calling {@link #ensureUpToDate(boolean)}, then further ensures that all
	 * interpolators have been initialized. 
	 */
	final void ensureInterpolators(boolean integrated) {
		ensureUpToDate(integrated);
		if (interpCDF == null || (integrated && (interpIntegratedCDF == null || interpIntegratedOneMinusCDF == null))) {
			synchronized (this) {
				// it's faster to do repeat interpolation if you precompute the slope at every grid, but that
				// comes with an overhead cost. these implementations start with basic interpolation, then
				// switch to the optimized version after it's clear they're being reused
				
				// after this many interpolations, go to the trouble to build the optimized versions
				int reuseCount = 100;
				if (interpCDF == null)
					interpCDF = DiscretizedFuncInterpolator.getRepeatOptimized(cdf, reuseCount);
				if (integrated) {
					if (interpIntegratedCDF == null)
						interpIntegratedCDF = DiscretizedFuncInterpolator.getRepeatOptimized(integratedCDF, reuseCount);
					if (interpIntegratedOneMinusCDF == null)
						interpIntegratedOneMinusCDF = DiscretizedFuncInterpolator.getRepeatOptimized(integratedOneMinusCDF, reuseCount);
				}
			}
		}
	}
	
//	public EvenlyDiscretizedFunc getIntegratedCDF() {
//		return integratedCDF;
//	}
//	
//	public EvenlyDiscretizedFunc getIntegratedOneMinusCDF() {
//		return integratedOneMinusCDF;
//	}
	
	/**
	 * @return the current CDF, cloned to prevent modification
	 */
	public EvenlyDiscretizedFunc getCDF() {
		ensureUpToDate(false);
		EvenlyDiscretizedFunc cdf = this.cdf.deepClone();
		cdf.setName(NAME+" CDF (Cumulative Density Function)");
		cdf.setInfo(adjustableParams.toString());
		return cdf;
	}
	
	/**
	 * Compute CDF from PDF using Trapizoidal integration
	 * @param null
	 * @return
	 */
	protected EvenlyDiscretizedFunc computeCDFfromPDF(EvenlyDiscretizedFunc pdf) {
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf.set(0,0);
		double cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			cd += deltaX*(pdf.getY(i)+pdf.getY(i-1))/2;  // Trapizoidal integration
			if (cd > 1d) { // if it goes slightly over
				cd = 1;
			}
			cdf.set(i,cd);
		}
		
		// Ensure 
		if(pdf.getY(pdf.size()-1)<1e-15) {// CDF should go to 1.0
			double maxCDF = cdf.getY(cdf.size()-1);
//			System.out.println("maxCDF = "+maxCDF);
			if(maxCDF != 1.0) {
				pdf.scale(1.0/maxCDF);
				cdf = computeCDFfromPDF(pdf); // this could get into an infinite loop?
			}
		}
		return cdf;
	}

	
	/**
	 * @return efficient interpolator of the current CDF
	 */
	public DiscretizedFuncInterpolator getCDF_Interpolator() {
		ensureInterpolators(false);
		return interpCDF;
	}
	
	public EvenlyDiscretizedFunc getSurvivorFunc() {
		ensureUpToDate(false);
		EvenlyDiscretizedFunc survFunc = new EvenlyDiscretizedFunc(0, cdf.getMaxX(), cdf.size());
		survFunc.setName(NAME+" Survivor Function (1-CDF)");
		survFunc.setInfo(adjustableParams.toString());
		for(int i=0;i<cdf.size();i++)
			survFunc.set(i,1.0-cdf.getY(i));
		return survFunc;
	}


	/**
	 * @return the current PDF, cloned to prevent modification
	 */
	public EvenlyDiscretizedFunc getPDF() {
		ensureUpToDate(false);
		EvenlyDiscretizedFunc pdf = this.pdf.deepClone();
		pdf.setName(NAME+" PDF (Probability Density Function)");
		pdf.setInfo(adjustableParams.toString()+"\nComputed mean = "+(float)computeMeanFromPDF(pdf));
		return pdf;
	}

	public EvenlyDiscretizedFunc getHazFunc() {
		ensureUpToDate(false);
		EvenlyDiscretizedFunc hazFunc = new EvenlyDiscretizedFunc(0, pdf.getMaxX(), pdf.size());
		double haz;
		for(int i=0;i<hazFunc.size();i++) {
			double s = 1.0-cdf.getY(i); // survival
			haz = pdf.getY(i)/s;
			if(Double.isInfinite(haz) || Double.isInfinite(-haz) || i>safeTimeSinceLastIndex) 
				haz = Double.NaN;
			hazFunc.set(i,haz);
		}
		hazFunc.setName(NAME+" Hazard Function");
		hazFunc.setInfo(adjustableParams.toString());
		return hazFunc;
	}
	
	protected static void validateDuration(double duration) {
		Preconditions.checkState(duration > 0d && Double.isFinite(duration),
				"Duration must be positive and finite: %s", duration);
	}
	
	protected static void validateHistOpenInterval(double histOpenInterval) {
		Preconditions.checkState(histOpenInterval >= 0d && Double.isFinite(histOpenInterval),
				"Historic open interval must be non-negative and finite: %s", histOpenInterval);
	}
	
	protected static void validateTimeSinceLast(double timeSinceLast) {
		Preconditions.checkState(timeSinceLast >= 0d && Double.isFinite(timeSinceLast),
				"Time-since-last event must be non-negative and finite: %s", timeSinceLast);
	}
	
	/*
	 * This returns a function of the probability of an event occurring between time T
	 * (on the x-axis) and T+duration, conditioned that it has not occurred before T.
	 * No extrapolation beyond the last numerically sound value is applied.
	 * 
	 * @param duration
	 * @return
	 */
	public EvenlyDiscretizedFunc getCondProbFunc(double duration) {
		return getCondProbFunc(duration, false);
	}

	
	/*
	 * This returns a function of the probability of an event occurring between time T
	 * (on the x-axis) and T+duration, conditioned that it has not occurred before T.
	 * The extrapolate boolean indicates whether the last numerically safe conditional 
	 * probability is used for the remainder of the function (a constant value, which 
	 * is not necessarily the correct behavior)
	 * 
	 * @param duration
	 * @param extrapolate
	 * @return
	 */
	public EvenlyDiscretizedFunc getCondProbFunc(double duration, boolean extrapolate) {
		validateDuration(duration);
		ensureUpToDate(false);
		int durBins = (int)Math.ceil(duration / deltaX);
		int maxStartIndex = (numPoints - 1) - durBins;
		Preconditions.checkState(maxStartIndex > 0,
				"Duration too large for distributions: duration=%s, deltaX=%s, numPoints=%s",
				duration, deltaX, numPoints);

		int numStartPoints = maxStartIndex;
		EvenlyDiscretizedFunc condFunc = new EvenlyDiscretizedFunc(0.0, numStartPoints, deltaX);
		double lastGoodCondProb = Double.NaN;
		double lastGoodXaxisValue = Double.NaN;
		for(int i=0;i<condFunc.size();i++) {
			double condProb = getCondProb(condFunc.getX(i), duration);
			if(Double.isNaN(condProb) && extrapolate) {
				if(Double.isNaN(lastGoodCondProb)) { // compute once and apply the rest of the way out
					lastGoodXaxisValue = condFunc.getX(i-1);
					// the following isn't really needed here because it will find condFunc.getY(i-1)
					lastGoodCondProb = findLastGoodCondProb(condFunc.getX(i), duration);
				}
				condProb = lastGoodCondProb;
			}
			condFunc.set(i,condProb);
		}
		if(extrapolate)
			condFunc.setName(NAME+" Conditional Probability Function, extrapolated from X = "+(float)lastGoodXaxisValue);
		else
			condFunc.setName(NAME+" Conditional Probability Function");
		condFunc.setInfo(adjustableParams.toString());
		return condFunc;
	}
	
	
	public EvenlyDiscretizedFunc getCondProbGainFunc(double duration) {
		return getCondProbGainFunc(duration,false);
	}

	
	public EvenlyDiscretizedFunc getCondProbGainFunc(double duration, boolean extrapolate) {
		EvenlyDiscretizedFunc func = getCondProbFunc(duration, extrapolate);
		double poisProb = 1.0-Math.exp(-duration/mean);
		func.scale(1.0/poisProb);
		func.setName(NAME+" Conditional Probability Gain Function");
		func.setInfo("Relative to Poisson probability of one or more events.\n"+adjustableParams.toString());

		// This is for the U3 rate gains
//		func.scale(mean/duration);
//		func.setName(NAME+" Conditional Probability Gain Function");
//		func.setInfo("Defined as cond prob divided by expected number (duration/mean="+(float)(duration/mean)+").\n"+adjustableParams.toString());
		return func;
	}
	
	/**
	 * This computes the the probability of occurrence over the given duration conditioned 
	 * on timeSinceLast (how long it has been since the last event).
	 * 
	 * This returns Double.NaN if the denominator (total area/prob remaining) is less than 
	 * 1e-14 to avoid numerical problems.
	 *  
	 * @param timeSinceLast
	 * @param duration
	 * @return
	 */
	public double getCondProb(double timeSinceLast, double duration) {
		
		if(timeSinceLast+duration>safeTimeSinceLast) {
			return Double.NaN;
		}
		validateTimeSinceLast(timeSinceLast);
		validateDuration(duration);
		boolean doInterp = this.interpolate;
		int index1 = cdf.getClosestXIndex(timeSinceLast);
		int index2 = cdf.getClosestXIndex(timeSinceLast+duration);
		if (!doInterp && (index1 == index2 || index1 == 0 || index2-index1 < maxBinsAwayToForceInterpolate)) {
			// special cases to force interpolation in order to avoid zeros and improve accuracy,
			// e.g., if duration ~= deltaX
			doInterp = true;
		}
		
		double p1, p2;
		if (doInterp) {
			// ensures that we're up to date and the CDF interpolator has been built
			ensureInterpolators(false);
			p1 = interpCDF.findY(timeSinceLast);
			p2 = interpCDF.findY(timeSinceLast+duration);
		} else {
			// ensures that we're up to date
			ensureUpToDate(false);
			p2 = cdf.getY(index2);
			p1 = cdf.getY(index1);
		}
		double denom = 1.0 - p1;
		if (D) {
			System.out.println("index1="+index1+", index2="+index2+" for timeSinceLast="+timeSinceLast
					+" and duration="+duration+", deltaX="+deltaX
					+", CDF x[0]"+cdf.getX(0)+", x[1]="+cdf.getX(1)+", ..., x["+(cdf.size()-1)+"]="+cdf.getMaxX());
			System.out.println("("+p1+" - "+p2+")/(1 - "+p1+") = "+((p2 - p1)/denom));
		}
//if(timeSinceLast>1.6 && timeSinceLast<1.8)
//	System.out.println(timeSinceLast+"\t"+p1+"\t"+p2+"\t"+(p2-p1));
		// first test below ensure we our out on the tail (p2-p1=0 at low RI)
		// second test avoids unstable results
		if(denom < 0.25 && p2-p1<NUMERICAL_PRECISION*0.01)	
			return Double.NaN;		
		else
			return (p2-p1)/denom;
	}	
	
	
	/**
	 * This finds the last numerically sound conditional probability when
	 * too far out on the tail (where getCondProb() returns NaN).
	 * 
	 * @param timeSinceLast
	 * @param duration
	 * @return
	 */
	public double findLastGoodCondProb(double timeSinceLast, double duration) {
		int testStartIndex = cdf.getClosestXIndex(timeSinceLast);
		double condProb = Double.NaN;
		while(Double.isNaN(condProb)) {
			testStartIndex -= 1;
			condProb = getCondProb(cdf.getX(testStartIndex), duration);
		}
			return condProb;
	}


	

	/**
	 * Initialize adjustable parameters
	 *
	 */
	protected void initAdjParams() {
	
		meanParam =  new  DoubleParameter(MEAN_PARAM_NAME, Double.MIN_VALUE, Double.MAX_VALUE, DEFAULT_MEAN_PARAM_VAL);
		meanParam.setInfo(MEAN_PARAM_INFO);
		meanParam.addParameterChangeListener(this);
		aperiodicityParam  = new DoubleParameter(APERIODICITY_PARAM_NAME, Double.MIN_VALUE, Double.MAX_VALUE, DEFAULT_APERIODICITY_PARAM_VAL);
		aperiodicityParam.setInfo(APERIODICITY_PARAM_INFO);
		aperiodicityParam.addParameterChangeListener(this);
		deltaX_Param = new  DoubleParameter(DELTA_X_PARAM_NAME, Double.MIN_VALUE, Double.MAX_VALUE, DEFAULT_DELTAX_PARAM_VAL);
		deltaX_Param.setInfo(DELTA_X_PARAM_INFO);
		deltaX_Param.addParameterChangeListener(this);
		numPointsParam = new  IntegerParameter(NUM_POINTS_PARAM_NAME, Integer.MIN_VALUE, Integer.MAX_VALUE, DEFAULT_NUMPOINTS_PARAM_VAL);;
		numPointsParam.setInfo(NUM_POINTS_PARAM_INFO);
		numPointsParam.addParameterChangeListener(this);

		adjustableParams = new ParameterList();
		adjustableParams.addParameter(meanParam);
		adjustableParams.addParameter(aperiodicityParam);
//		adjustableParams.addParameter(durationParam);
		adjustableParams.addParameter(deltaX_Param);
		adjustableParams.addParameter(numPointsParam);
//		adjustableParams.addParameter(histOpenIntParam);

		setAll(DEFAULT_MEAN_PARAM_VAL.doubleValue(), DEFAULT_APERIODICITY_PARAM_VAL.doubleValue(),
				DEFAULT_DELTAX_PARAM_VAL.doubleValue(), DEFAULT_NUMPOINTS_PARAM_VAL.intValue());
//				DEFAULT_DURATION_PARAM_VAL.doubleValue(), DEFAULT_HIST_OPEN_INTERVAL_PARAM_VAL.doubleValue());

	}
	
	/**
	 * Get adjustable params
	 * 
	 * @return
	 */
	public ParameterList getAdjParams() {
		return this.adjustableParams;
	}
	
	
	/**
	 * Get the name 
	 * @return
	 */
	public String getName() {
		return this.NAME;
	}
	
	
	/**
	 * This sets the values of the distribution by finding those that are best fit to the function passed in.
	 * The best-fit values are found via a grid search.
	 * @param dist - the distribution to be fit (should be a true pdf such that integration (sum of y values multiplied by deltaX) equals 1)
	 * @param minMean
	 * @param maxMean
	 * @param numMean
	 * @param minAper
	 * @param maxAper
	 * @param numAper
	 */
	public void fitToThisFunction(EvenlyDiscretizedFunc dist, double minMean, double maxMean,
			int numMean, double minAper, double maxAper,int numAper) {
		Preconditions.checkState(!unmodifiable, "%s has been set to unmodifiable", NAME);
		ensureUpToDate(false);
		
		deltaX_Param.setValue(dist.getDelta()/2);	// increase discretization here just to be safe
		numPointsParam.setValue(dist.size()*2+1);	// vals start from zero whereas passed in histograms might start at delta/2
		double bestMean=0;
		double bestAper=0;
		double best_rms=Double.MAX_VALUE;
		double deltaMean=(maxMean-minMean)/(numMean-1);
		double deltaAper=(maxAper-minAper)/(numAper-1);
		for(int m=0;m<numMean;m++) {
			for(int c=0;c<numAper;c++) {
				meanParam.setValue(minMean+m*deltaMean);
				aperiodicityParam.setValue(minAper+c*deltaAper);
				EvenlyDiscretizedFunc pdf = this.getPDF();
				double rms=0;
				for(int i=0;i<dist.size()-1;i++) {
					double diff=(dist.getY(i)-pdf.getInterpolatedY(dist.getX(i)));
					rms += diff*diff;
				}
				if(rms<best_rms) {
					bestMean=mean;
					bestAper=aperiodicity;
					best_rms=rms;
				}
			}
		}
		meanParam.setValue(bestMean);
		aperiodicityParam.setValue(bestAper);
//		System.out.println(this.NAME+" best fit mean and aper: "+mean+"\t"+aperiodicity);
	}
	
	public double getMean() {return mean;}
	
	public double  getAperiodicity() {return aperiodicity;}
	
	
	/**
	 * This gives the probability as a function of the Open Interval (x-axis). Note that values can be biased
	 * upward out on the tail if the survivor function (1-CDF) does not get close enough to zero at the
	 * max x-axis value.  (the denominator in equation 8 of Field and Jordan (2015, doi: 10.1785/0120140096)
	 * is biased low because the integration does not go to infinity.
	 * @param duration
	 * @param maxHistOpenIntervalYrs
	 * @return
	 */
	public EvenlyDiscretizedFunc getCondProbForUnknownTimeSinceLastEventFunc(double duration, double maxHistOpenIntervalYrs) {
		validateDuration(duration);
		ensureUpToDate(false);
		int durBins = (int)Math.ceil(duration / deltaX);
		int numBins = (numPoints - 1) - durBins;
//		int numBins = (int)Math.round(maxHistOpenIntervalYrs) - durBins; // discretize by 1 year
		EvenlyDiscretizedFunc condFunc = new EvenlyDiscretizedFunc(0.0, numBins, deltaX);
		for(int i=0;i<condFunc.size();i++) {
			double condProb = getCondProbForUnknownTimeSinceLastEvent(duration, condFunc.getX(i));
			condFunc.set(i,condProb);
		}
		condFunc.setName(NAME+" Prob vs OpenInterval");
		condFunc.setInfo(adjustableParams.toString());
		return condFunc;
	}
	
	/**
	 * This computes the probability of an event over the specified duration for the case where the 
	 * date of last event is unknown (looping over all possible values), but where the historic open 
	 * interval is applied (the latter defaults to zero if never set).  Note that values can be biased
	 * upward out on the tail if the survivor function (1-CDF) does not get close enough to zero at the
	 * max x-axis value.  (the denominator in equation 8 of Field and Jordan (2015, doi: 10.1785/0120140096)
	 * is biased low because the integration does not go to infinity. 
	 * @return
	 */
	public double getCondProbForUnknownTimeSinceLastEvent(double duration, double histOpenInterval) {
		validateDuration(duration);
		validateHistOpenInterval(histOpenInterval);
		// ensures that we're up to date including integrated versions, and the interpolator have been built
		ensureInterpolators(true);
//		double numer = duration - (integratedCDF.getInterpolatedY(histOpenInterval+duration)-integratedCDF.getInterpolatedY(histOpenInterval));
//		double denom = (integratedOneMinusCDF.getY(numPoints-1)-integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
		double numer = duration - (interpIntegratedCDF.findY(histOpenInterval+duration)-interpIntegratedCDF.findY(histOpenInterval));
		double denom = (integratedOneMinusCDF.getY(numPoints-1)-interpIntegratedOneMinusCDF.findY(histOpenInterval));

		if(denom < NUMERICAL_PRECISION || numer<NUMERICAL_PRECISION)	
			return Double.NaN;

//		if(histOpenInterval>7.9); // && histOpenInterval<8.1)
//			System.out.println((float)histOpenInterval+"\t"+duration+"\t"+numer+"\t"+denom+"\t"+(1-interpCDF.findY(histOpenInterval)));
//		
		double result = numer/denom;
		
//
//	System.out.println("cdf.getInterpolatedY(histOpenInterval) = "+cdf.getInterpolatedY(histOpenInterval));
//
//	System.out.println("normHistOpenInterval = "+histOpenInterval);
//	System.out.println("normDuration = "+ duration);
//	System.out.println("normHistOpenInterval+normDuration = "+(histOpenInterval+duration));
//	
//	System.out.println("\ninterpIntegratedCDF.findY(histOpenInterval+duration) = "+interpIntegratedCDF.findY(histOpenInterval+duration));
//	System.out.println("interpIntegratedCDF.findY(histOpenInterval) = "+interpIntegratedCDF.findY(histOpenInterval));
//	
//	System.out.println("\nintegratedOneMinusCDF.getY(numPoints-1) = "+integratedOneMinusCDF.getY(numPoints-1));
//	System.out.println("interpIntegratedOneMinusCDF.findY(histOpenInterval) = "+interpIntegratedOneMinusCDF.findY(histOpenInterval));
//
//	
//	System.out.println("\nintegratedOneMinusCDF.getY(numPoints-1) = "+integratedOneMinusCDF.getY(numPoints-1));
//	System.out.println("integratedOneMinusCDF.getInterpolatedY(histOpenInterval) = "+integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
//	
//	System.out.println("\nnumer = "+numer+"\ndenom = "+denom+"\nresult = "+result+
//			"\nduration = "+duration+"\nhistOpenInterval = "+histOpenInterval);
//	double numer2 = duration - (integratedCDF.getInterpolatedY(histOpenInterval+duration)-integratedCDF.getInterpolatedY(histOpenInterval));
//	double denom2 = (integratedOneMinusCDF.getY(numPoints-1)-integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
//	double result2 = numer2/denom2;
//	System.out.println("numer2 = "+numer2+"\ndenom2 = "+denom2+"\nresult2 = "+result2);
//
//}
		
		
		// this tests other ways of computing the same thing
		if(D) {
			
			double numer2=0;
			double denom2=0;
			EvenlyDiscretizedFunc condProbFunc = getCondProbFunc(duration);
			int firstIndex = condProbFunc.getClosestXIndex(histOpenInterval);
			for(int i=firstIndex;i<condProbFunc.size();i++) {
				double probOfTimeSince = (1-cdf.getY(i));
				if(i==firstIndex)
					probOfTimeSince *= ((cdf.getX(i)+deltaX/2.0) - histOpenInterval)/deltaX;	// fraction of first bin
				denom2+=probOfTimeSince; 
				numer2+= condProbFunc.getY(i)*probOfTimeSince;
			}
			denom2 *= deltaX;
			numer2 *= deltaX;
			double result2 = numer2/denom2;	// normalize properly
			
			
			double numer3=0;
			int numIndicesForDuration = (int)Math.round(duration/cdf.getDelta());
			for(int i=firstIndex;i<cdf.size()-numIndicesForDuration;i++) {
				numer3 += (cdf.getY(i+numIndicesForDuration)-cdf.getY(i))*cdf.getDelta();
			}
			double result3 = (numer3/denom2);
			

			int lastIndex = condProbFunc.getClosestXIndex(histOpenInterval+duration);
			double sumCDF=0;
			for(int i=firstIndex;i<=lastIndex;i++) {
				sumCDF += cdf.getY(i)*cdf.getDelta();
			}
			double numer4 = duration-sumCDF;
			double result4= numer4/denom2;
			
			
			
			double poisProb1orMore = 1-Math.exp(-duration/mean);
			double poisProbOf1 = (duration/mean)*Math.exp(-duration/mean);
			System.out.println("result="+(float)result+"\tnumer="+numer+"\tdenom="+denom+
					"\nresult2="+(float)result2 +" ("+(float)(result/result2)+")"+"\tnumer2="+numer2+"\tdemon2="+denom2+
					"\nresult3="+(float)result3 +" ("+(float)(result/result3)+")"+"\tnumer3="+numer3+
					"\nresult4="+(float)result4+" ("+(float)(result/result4)+")"+"\tnumer4="+numer4+
					"\nduration/mean="+(duration/mean)+
					"\npoisProb1orMore="+(float)poisProb1orMore+
					"\npoisProbOf1="+(float)poisProbOf1);			
		}
		
		
		return result;
	}
	
	
	protected void makeIntegratedCDFs() {
		EvenlyDiscretizedFunc integratedCDF = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		EvenlyDiscretizedFunc integratedOneMinusCDF = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		double sum1=0;
		double sum2=0;
		for(int i=1;i<numPoints;i++) {
			sum1 += deltaX*(cdf.getY(i-1)+cdf.getY(i))/2;	// trapezoidal integration (assume triangle)
			sum2 += deltaX*(1.0-(cdf.getY(i-1)+cdf.getY(i))/2);
			integratedCDF.set(i,sum1);
			integratedOneMinusCDF.set(i,sum2);
		}
		this.integratedCDF = integratedCDF;
		this.integratedOneMinusCDF = integratedOneMinusCDF;
	}
	
	
	/**
	 * This provides the PDF of the date of last event when only the historic open interval is known.
	 * @return
	 */
	public EvenlyDiscretizedFunc getTimeSinceLastEventPDF(double histOpenInterval) {
		validateHistOpenInterval(histOpenInterval);
		ensureUpToDate(false);

		EvenlyDiscretizedFunc timeSinceLastPDF = new EvenlyDiscretizedFunc(0.0, numPoints , deltaX);
		double normDenom=0;
		int firstIndex = timeSinceLastPDF.getClosestXIndex(histOpenInterval);
		for(int i=firstIndex;i<timeSinceLastPDF.size();i++) {
			double probOfTimeSince = (1-cdf.getY(i));
			timeSinceLastPDF.set(i,probOfTimeSince);
			normDenom+=probOfTimeSince; 
		}
		timeSinceLastPDF.scale(1.0/(deltaX*normDenom));
		timeSinceLastPDF.setName("Time Since Last Event PDF");
		timeSinceLastPDF.setInfo("The PDF of date of last event when only the historic open interval ("+
				histOpenInterval+") is known\nmean = "+(float)computeMeanFromPDF(timeSinceLastPDF));
		return timeSinceLastPDF;
	}
	
	
	/**
	 * This provides the mean date of last event when only the historic open interval is known.
	 * @return
	 */
	public double setAllParameters(double histOpenInterval) {
		return computeMeanFromPDF(getTimeSinceLastEventPDF(histOpenInterval));
	}
	
	/**
	 * This computes the mean from the given PDF
	 * @param pdf
	 * @return
	 */
	public static double computeMeanFromPDF(EvenlyDiscretizedFunc pdf) {
		double result = 0;
		double normDenom=0;
		for(int i=0;i<pdf.size();i++) {
			result += pdf.getX(i)*pdf.getY(i);
			normDenom += pdf.getY(i);
		}
		return result/normDenom;
	}
	
	/**
	 * Method to set several values (but corresponding parameters are not changed,
	 * for efficiency, so getAdjParams().toString() won't be correct)
	 * @param mean
	 * @param aperiodicity
	 * @param deltaX
	 * @param numPoints
	 */
	public synchronized void setAll(double mean, double aperiodicity, double deltaX, int numPoints) {
		Preconditions.checkState(!unmodifiable, "%s has been set to unmodifiable, can't change distribution", NAME);
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX=deltaX;;
		this.numPoints=numPoints;
		upToDate=false;
	}
	
	/**
	 * Interpolation of the CDF is more accurate but can be slow, if numPoints is sufficiently high
	 * you can speed things up by disabling interpolation
	 * @param interpolate
	 */
	public void setInterpolate(boolean interpolate) {
		this.interpolate = interpolate;
	}
	
	/**
	 * Interpolation of the CDF is more accurate but can be slow, if numPoints is sufficiently high
	 * you can speed things up by disabling interpolation
	 * @param interpolate
	 * @param maxBinsAwayToForceInterpolate if interpolate == false but the 2 indexes in getCondProb are within this
	 * many bins of each other, force interpolation to increase resolution
	 */
	public void setInterpolate(boolean interpolate, int maxBinsAwayToForceInterpolate) {
		this.interpolate = interpolate;
		this.maxBinsAwayToForceInterpolate = maxBinsAwayToForceInterpolate;
	}
	
	/**
	 * This method sets values in the Parameter objects (slower, but safe in terms of using
	 * getAdjParams().toString())
	 * @param mean
	 * @param aperiodicity
	 * @param deltaX
	 * @param numPoints
	 */
	public synchronized void setAllParameters(double mean, double aperiodicity, double deltaX, int numPoints) {
		Preconditions.checkState(!unmodifiable, "%s has been set to unmodifiable, can't change distribution", NAME);
		this.meanParam.setValue(mean);
		this.aperiodicityParam.setValue(aperiodicity);
		this.deltaX_Param.setValue(deltaX);
		this.numPointsParam.setValue(numPoints);
		upToDate=false;
	}


	
	/**
	 * For this case deltaX defaults to 0.001*mean and numPoints is aperiodicity*10/deltaX+1.
	 * The corresponding values in the mean and aperiodicity parameters are not 
	 * changed, so getAdjParams().toString() won't be correct.
	 * 
	 * @param mean
	 * @param aperiodicity
	 */
	public synchronized void setAll(double mean, double aperiodicity) {
		Preconditions.checkState(!unmodifiable, "%s has been set to unmodifiable, can't change distribution", NAME);
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX = DELTA_X_DEFAULT*mean;
		this.numPoints = (int)Math.round(aperiodicity*10*mean/deltaX)+1;
		upToDate=false;
	}
	
	/**
	 * Set the primitive types whenever a parameter changes
	 */
	public synchronized void parameterChange(ParameterChangeEvent event) {
		Preconditions.checkState(!unmodifiable, "%s has been set to unmodifiable, can't change distribution", NAME);
		String paramName = event.getParameterName();
		if(paramName.equalsIgnoreCase(MEAN_PARAM_NAME)) this.mean = ((Double) meanParam.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(APERIODICITY_PARAM_NAME)) this.aperiodicity = ((Double) aperiodicityParam.getValue()).doubleValue();
//		else if(paramName.equalsIgnoreCase(DURATION_PARAM_NAME)) this.duration = ((Double) durationParam.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(DELTA_X_PARAM_NAME)) this.deltaX = ((Double) deltaX_Param.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(NUM_POINTS_PARAM_NAME)) this.numPoints = ((Integer) numPointsParam.getValue()).intValue();
//		else if(paramName.equalsIgnoreCase(HIST_OPEN_INTERVAL_PARAM_NAME)) this.histOpenInterval = ((Double) histOpenIntParam.getValue()).doubleValue();
		this.upToDate = false;
	}
	
	
	/**
	 * This finds the largest x-axis value such that survival (1.0-cdf.getY(x)) >= NUMERICAL_PRECISION
	 * (not too close to zero, as this is the denominator of the hazard & conditional probability calculation)
	 */
	protected void computeSafeTimeSinceLastCutoff() {
		Preconditions.checkState(!isUnmodifiable(), "%s is already set to be unmodifiable", NAME);
		safeTimeSinceLast = Double.NaN;
		safeTimeSinceLastIndex = -1;
		for(int x=0;x<cdf.size();x++) {
			if(1.0-cdf.getY(x) < NUMERICAL_PRECISION) {	// when cdf gets too close to 1, keep last safeTimeSinceLast
				break;
			}
			else {
				safeTimeSinceLast = cdf.getX(x);
				safeTimeSinceLastIndex = x;
			}
		}
		
		if(Double.isNaN(safeTimeSinceLast)) {
			safeTimeSinceLastIndex = cdf.size()-1;
			safeTimeSinceLast = cdf.getX(safeTimeSinceLastIndex);
//			throw new RuntimeException ("CDF never gets close to 1.0; need to increase numPoints?");
		}
		
//		System.out.println("safeTimeSinceLast="+safeTimeSinceLast);
	}
	
	public static double getPrecision() {
		return NUMERICAL_PRECISION;
	}


}

