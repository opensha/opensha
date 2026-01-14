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
 * and cdf in their respective implementations of the computeDistributions() method defined here.  All other calculations 
 * are performed here from the pdf and cdf.
 * 
 * The units of time are arbitrary (they just need to be consistent among parameters)
 * 
 * These classes have not been fully tested in terms of numerical accuracy, so make sure the number of points is high (so 
 * the cdf gets close to 1.0), and that the time discretization is very small compared to both the mean and the duration.
 * No checks for these are currently made.
 * 
 * Some subclasses implement a getSafeCondProb(*) method that checks and corrects for numerical errors when the denominator of the
 * conditional probability calculation (1.0-cdf.getInterpolatedY(timeSinceLast+duration)) approaches zero at high timeSinceLast.
 * See BPT_DistCalc for an example; that implementation could be moved here?
 * 
 * A method could/should be added for sampling random values.
 * 
 <p>
 *
 * @author Edward Field
 * @created    July, 2007; updated several times since
 * @version 1.0
 */

public abstract class EqkProbDistCalc implements ParameterChangeListener {
	
	final static boolean D = false;	// debugging flag
	
	// distributions
	protected EvenlyDiscretizedFunc pdf, cdf, integratedCDF, integratedOneMinusCDF;
	// for efficient interpolation of the CDF
	protected DiscretizedFuncInterpolator interpCDF;
	protected DiscretizedFuncInterpolator interpIntegratedCDF;
	protected DiscretizedFuncInterpolator interpIntegratedOneMinusCDF;
	
	protected double mean, aperiodicity, deltaX;
	protected int numPoints;
	protected boolean interpolate = true;
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
	protected final static Double DEFAULT_MEAN_PARAM_VAL = Double.valueOf(100);
	protected final static Double DEFAULT_APERIODICITY_PARAM_VAL = Double.valueOf(0.5);
	protected final static Double DEFAULT_DELTAX_PARAM_VAL = Double.valueOf(1);
	protected final static Integer DEFAULT_NUMPOINTS_PARAM_VAL = Integer.valueOf(500);
	
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
			haz = pdf.getY(i)/(1.0-cdf.getY(i));
			if(Double.isInfinite(haz) || Double.isInfinite(-haz)) haz = Double.NaN;
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
	 * This gives a function of the probability of an event occurring between time T
	 * (on the x-axis) and T+duration, conditioned that it has not occurred before T.
	 */
	public EvenlyDiscretizedFunc getCondProbFunc(double duration) {
		validateDuration(duration);
		ensureUpToDate(false);
//		int numPts = numPoints - (int)(duration/deltaX+1);
////System.out.println("numPts="+numPts+"\t"+duration);
//		EvenlyDiscretizedFunc condFunc = new EvenlyDiscretizedFunc(0.0, numPts , deltaX);
		int durBins = (int)Math.ceil(duration / deltaX);
		int maxStartIndex = (numPoints - 1) - durBins;
		Preconditions.checkState(maxStartIndex >= 0,
				"Duration too large for distributions: duration=%s, deltaX=%s, numPoints=%s",
				duration, deltaX, numPoints);

		int numStartPoints = maxStartIndex + 1;
		EvenlyDiscretizedFunc condFunc = new EvenlyDiscretizedFunc(0.0, numStartPoints, deltaX);
		for(int i=0;i<condFunc.size();i++) {
			condFunc.set(i,getCondProb(condFunc.getX(i), duration));
		}
		condFunc.setName(NAME+" Conditional Probability Function");
		condFunc.setInfo(adjustableParams.toString());
		return condFunc;
	}
	
	public EvenlyDiscretizedFunc getCondProbGainFunc(double duration) {
		EvenlyDiscretizedFunc func = getCondProbFunc(duration);
//		double poisProb = 1.0-Math.exp(-duration/mean);
//		func.scale(1.0/poisProb);
//		func.setName(NAME+" Conditional Probability Gain Function");
//		func.setInfo("Relative to Poisson probability of one or more events.\n"+adjustableParams.toString());

		func.scale(mean/duration);
		func.setName(NAME+" Conditional Probability Gain Function");
		func.setInfo("Defined as cond prob divided by expected number (duration/mean="+(float)(duration/mean)+").\n"+adjustableParams.toString());
		
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
		validateTimeSinceLast(timeSinceLast);
		validateDuration(duration);
		boolean doInterp = this.interpolate;
		int index1 = 0, index2 = 0;
		if (!doInterp) {
			index1 = (int)Math.floor(timeSinceLast/deltaX);
			index2 = (int)Math.floor((timeSinceLast+duration)/deltaX);
			
			// clamp the indexes in case of precision issues
			int maxIndex = numPoints - 1;
			if (index1 < 0)
				index1 = 0;
			else if (index1 > maxIndex)
				index1 = maxIndex;
			if (index2 < 0)
				index2 = 0;
			else if (index2 > maxIndex)
				index2 = maxIndex;
			
			if (index1 == index2 || index1 == 0)
				// special cases to force interpolation in order to avoid zeros, e.g., if duration ~= deltaX
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
		double denom = 1d - p1;
		if(denom > 1e-14)
			// large enough to be numerically stable
			return (p2-p1)/denom;
		// numerical issue
		return Double.NaN;
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
	 * This computes the probability of an event over the specified duration for the case where the 
	 * date of last event is unknown (looping over all possible values), but where the historic open 
	 * interval is applied (the latter defaults to zero if never set).
	 * @return
	 */
	public double getCondProbForUnknownTimeSinceLastEvent(double duration, double histOpenInterval) {
		validateDuration(duration);
		validateHistOpenInterval(histOpenInterval);
		// ensures that we're up to date including integrated versions, and the interpolator have been built
		ensureInterpolators(true);
		
//		if(integratedCDF==null) 
//			makeIntegratedCDFs();
//		double numer = duration - (integratedCDF.getInterpolatedY(histOpenInterval+duration)-integratedCDF.getInterpolatedY(histOpenInterval));
//		double denom = (integratedOneMinusCDF.getY(numPoints-1)-integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
		double numer = duration - (interpCDF.findY(histOpenInterval+duration)-interpIntegratedCDF.findY(histOpenInterval));
		double denom = (integratedOneMinusCDF.getY(numPoints-1)-interpIntegratedOneMinusCDF.findY(histOpenInterval));
		double result = numer/denom;
		
		
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
	public double getMeanTimeSinceLastEventPDF(double histOpenInterval) {
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
	
	


}

