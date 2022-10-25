package org.opensha.sha.earthquake.calc.recurInterval;

import java.util.ArrayList;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;


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
	
	final static boolean D = true;	// debugging flag
	
	protected EvenlyDiscretizedFunc pdf, cdf, integratedCDF, integratedOneMinusCDF;
	protected double mean, aperiodicity, deltaX, duration, histOpenInterval;
	protected int numPoints;
	protected boolean interpolate = true;
	public static final double DELTA_X_DEFAULT = 0.001;
	protected boolean upToDate=false;
	protected  String NAME;
	protected String commonInfoString;
	
	// TODO create and get these from ../../../param (hist open interval is already there)
	// Parameter names
	public final static String MEAN_PARAM_NAME= "Mean";
	public final static String APERIODICITY_PARAM_NAME = "Aperiodicity";
	public final static String DURATION_PARAM_NAME = "Duration";
	public final static String DELTA_X_PARAM_NAME = "Delta T";
	public final static String NUM_POINTS_PARAM_NAME = "Num Points";
	public final static String HIST_OPEN_INTERVAL_PARAM_NAME = "Historic Open Interval";
	
	// Parameter Infos
	protected final static String MEAN_PARAM_INFO= "Mean";
	protected final static String APERIODICITY_PARAM_INFO = "Aperiodicity is the standard deviation divided by the mean ";
	protected final static String DURATION_PARAM_INFO = "Duration of the forecast";
	protected final static String DELTA_X_PARAM_INFO = "The time discretization for the distribution";
	protected final static String NUM_POINTS_PARAM_INFO = "The number of points for the distribution";
	protected final static String HIST_OPEN_INTERVAL_PARAM_INFO = "Historic time interval over which event is known not to have occurred";
	
	// default param values
	protected final static Double DEFAULT_MEAN_PARAM_VAL = new Double(100);
	protected final static Double DEFAULT_APERIODICITY_PARAM_VAL = new Double(0.5);
	protected final static Double DEFAULT_DURATION_PARAM_VAL = new Double(30);
	protected final static Double DEFAULT_DELTAX_PARAM_VAL = new Double(1);
	protected final static Integer DEFAULT_NUMPOINTS_PARAM_VAL = new Integer(500);
	protected final static Double DEFAULT_HIST_OPEN_INTERVAL_PARAM_VAL = new Double(0.0);
	
	// various adjustable params
	protected DoubleParameter meanParam, aperiodicityParam, durationParam, deltaX_Param, histOpenIntParam;
	protected IntegerParameter numPointsParam;
	
	// adjustable parameter list
	protected ParameterList adjustableParams;

	/*
	 * The method is where subclasses are to compute the pdf and cdf for the given parameters (mean, aperiodicity, delta
	 */
	abstract void computeDistributions();
	
	public EvenlyDiscretizedFunc getCDF() {
		if(!upToDate) computeDistributions();
		cdf.setName(NAME+" CDF (Cumulative Density Function)");
		cdf.setInfo(adjustableParams.toString());
		return cdf;
	}

	public EvenlyDiscretizedFunc getPDF() {
		if(!upToDate) computeDistributions();
		pdf.setName(NAME+" PDF (Probability Density Function)");
		pdf.setInfo(adjustableParams.toString()+"\nComputed mean = "+(float)computeMeanFromPDF(pdf));
		return pdf;
	}

	public EvenlyDiscretizedFunc getHazFunc() {
		if(!upToDate) computeDistributions();
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
	
	/*
	 * This gives a function of the probability of an event occurring between time T
	 * (on the x-axis) and T+duration, conditioned that it has not occurred before T.
	 */
	public EvenlyDiscretizedFunc getCondProbFunc() {
		if(duration==0)
			throw new RuntimeException("duration has not been set");
		if(!upToDate) computeDistributions();
		int numPts = numPoints - (int)(duration/deltaX+1);
//System.out.println("numPts="+numPts+"\t"+duration);
		EvenlyDiscretizedFunc condFunc = new EvenlyDiscretizedFunc(0.0, numPts , deltaX);
		for(int i=0;i<condFunc.size();i++) {
			condFunc.set(i,getCondProb(condFunc.getX(i), duration));
		}
		condFunc.setName(NAME+" Conditional Probability Function");
		condFunc.setInfo(adjustableParams.toString());
		return condFunc;
	}
	
	
	public EvenlyDiscretizedFunc getCondProbFunc(double duration) {
		this.duration = duration;
		if(!upToDate) computeDistributions();
		return getCondProbFunc();
	}
	
	
	
	public EvenlyDiscretizedFunc getCondProbGainFunc() {
		EvenlyDiscretizedFunc func = getCondProbFunc();
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
	 * The commented out code gives the non-interpolated result, which is not as accurate.
	 * 
	 * This does not check for numerical errors where the denominator approaches zero at high 
	 * timeSinceLast (look for a getSafeCondProb(*) version of this method in subclasses).
	 * @param timeSinceLast
	 * @param duration
	 * @return
	 */
	public double getCondProb(double timeSinceLast, double duration) {
		this.duration = duration;
		if(!upToDate) computeDistributions();
		
		boolean interpolate = this.interpolate;
		int pt1 = 0, pt2 = 0;
		if (!interpolate) {
			pt1 = (int)Math.round(timeSinceLast/deltaX);
			pt2 = (int)Math.round((timeSinceLast+duration)/deltaX);
			if (pt1 == pt2)
				// they're in the same bin, do interpolation to avoid zero values
				interpolate = true;
		}
		
		double p1, p2;
		if (interpolate) {
			p1 = cdf.getInterpolatedY(timeSinceLast);
			p2 = cdf.getInterpolatedY(timeSinceLast+duration);
		} else {
			p2 = cdf.getY(pt2);
			p1 = cdf.getY(pt1);
		}
		
		return (p2-p1)/(1.0-p1);
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
		durationParam = new  DoubleParameter(DURATION_PARAM_NAME, Double.MIN_VALUE, Double.MAX_VALUE, DEFAULT_DURATION_PARAM_VAL);
		durationParam.setInfo(DURATION_PARAM_INFO);
		durationParam.addParameterChangeListener(this);
		deltaX_Param = new  DoubleParameter(DELTA_X_PARAM_NAME, Double.MIN_VALUE, Double.MAX_VALUE, DEFAULT_DELTAX_PARAM_VAL);
		deltaX_Param.setInfo(DELTA_X_PARAM_INFO);
		deltaX_Param.addParameterChangeListener(this);
		numPointsParam = new  IntegerParameter(NUM_POINTS_PARAM_NAME, Integer.MIN_VALUE, Integer.MAX_VALUE, DEFAULT_NUMPOINTS_PARAM_VAL);;
		numPointsParam.setInfo(NUM_POINTS_PARAM_INFO);
		numPointsParam.addParameterChangeListener(this);
		histOpenIntParam = new  DoubleParameter(HIST_OPEN_INTERVAL_PARAM_NAME, 0, Double.MAX_VALUE, DEFAULT_HIST_OPEN_INTERVAL_PARAM_VAL);
		histOpenIntParam.setInfo(HIST_OPEN_INTERVAL_PARAM_INFO);
		histOpenIntParam.addParameterChangeListener(this);

		adjustableParams = new ParameterList();
		adjustableParams.addParameter(meanParam);
		adjustableParams.addParameter(aperiodicityParam);
		adjustableParams.addParameter(durationParam);
		adjustableParams.addParameter(deltaX_Param);
		adjustableParams.addParameter(numPointsParam);
		adjustableParams.addParameter(histOpenIntParam);

		setAll(DEFAULT_MEAN_PARAM_VAL.doubleValue(), DEFAULT_APERIODICITY_PARAM_VAL.doubleValue(),
				DEFAULT_DELTAX_PARAM_VAL.doubleValue(), DEFAULT_NUMPOINTS_PARAM_VAL.intValue(),
				DEFAULT_DURATION_PARAM_VAL.doubleValue(), DEFAULT_HIST_OPEN_INTERVAL_PARAM_VAL.doubleValue());

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
		
		if(!upToDate) computeDistributions();
		
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
	public double getCondProbForUnknownTimeSinceLastEvent() {
		
		if(!upToDate) computeDistributions();
		
		if(integratedCDF==null) 
			makeIntegratedCDFs();
		double numer = duration - (integratedCDF.getInterpolatedY(histOpenInterval+duration)-integratedCDF.getInterpolatedY(histOpenInterval));
		double denom = (integratedOneMinusCDF.getY(numPoints-1)-integratedOneMinusCDF.getInterpolatedY(histOpenInterval));
		double result = numer/denom;
		
		
		// this tests other ways of computing the same thing
		if(D) {
			
			double numer2=0;
			double denom2=0;
			EvenlyDiscretizedFunc condProbFunc = getCondProbFunc();
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
		integratedCDF = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		integratedOneMinusCDF = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		double sum1=0;
		double sum2=0;
		for(int i=1;i<numPoints;i++) {
			sum1 += deltaX*(cdf.getY(i-1)+cdf.getY(i))/2;	// trapezoidal integration (assume triangle)
			sum2 += deltaX*(1.0-(cdf.getY(i-1)+cdf.getY(i))/2);
			integratedCDF.set(i,sum1);
			integratedOneMinusCDF.set(i,sum2);
		}
	}
	
	
	/**
	 * This provides the PDF of the date of last event when only the historic open interval is known.
	 * @return
	 */
	public EvenlyDiscretizedFunc getTimeSinceLastEventPDF() {
		
		if(!upToDate) computeDistributions();

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
	public double getMeanTimeSinceLastEventPDF() {
		return computeMeanFromPDF(getTimeSinceLastEventPDF());
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
	public void setAll(double mean, double aperiodicity, double deltaX, int numPoints) {
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX=deltaX;;
		this.numPoints=numPoints;
		upToDate=false;
	}

	
	/**
	 * Method to set several values (but corresponding parameters are not changed,
	 * for efficiency, so getAdjParams().toString() won't be correct)
	 * @param mean
	 * @param aperiodicity
	 * @param deltaX
	 * @param numPoints
	 * @param duration
	 */
	public void setAll(double mean, double aperiodicity, double deltaX, int numPoints, double duration) {
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX=deltaX;;
		this.numPoints=numPoints;
		this.duration = duration;
		upToDate=false;
	}
	
	/**
	 * Method to set several values (but corresponding parameters are not changed,
	 * for efficiency, so getAdjParams().toString() won't be correct)
	 * @param mean
	 * @param aperiodicity
	 * @param deltaX
	 * @param numPoints
	 * @param duration
	 */
	public void setAll(double mean, double aperiodicity, double deltaX, int numPoints, double duration, double histOpenInterval) {
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX=deltaX;;
		this.numPoints=numPoints;
		this.duration = duration;
		this.histOpenInterval = histOpenInterval;
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
	 * @param duration
	 */
	public void setAllParameters(double mean, double aperiodicity, double deltaX, int numPoints, double duration, double histOpenInterval) {
		this.meanParam.setValue(mean);
		this.aperiodicityParam.setValue(aperiodicity);
		this.deltaX_Param.setValue(deltaX);
		this.numPointsParam.setValue(numPoints);
		this.durationParam.setValue(duration);
		this.histOpenIntParam.setValue(histOpenInterval);
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
	public void setAll(double mean, double aperiodicity) {
		this.mean=mean;
		this.aperiodicity=aperiodicity;
		this.deltaX = DELTA_X_DEFAULT*mean;
		this.numPoints = (int)Math.round(aperiodicity*10*mean/deltaX)+1;
		upToDate=false;
	}
	
	
	
	public void setDuration(double duration) {
		this.duration=duration;
	}
	
	public void setDurationAndHistOpenInterval(double duration, double histOpenInterval) {
		this.duration=duration;
		this.histOpenInterval=histOpenInterval;
	}
	
	
	/**
	 * Set the primitive types whenever a parameter changes
	 */
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		if(paramName.equalsIgnoreCase(MEAN_PARAM_NAME)) this.mean = ((Double) meanParam.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(APERIODICITY_PARAM_NAME)) this.aperiodicity = ((Double) aperiodicityParam.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(DURATION_PARAM_NAME)) this.duration = ((Double) durationParam.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(DELTA_X_PARAM_NAME)) this.deltaX = ((Double) deltaX_Param.getValue()).doubleValue();
		else if(paramName.equalsIgnoreCase(NUM_POINTS_PARAM_NAME)) this.numPoints = ((Integer) numPointsParam.getValue()).intValue();
		else if(paramName.equalsIgnoreCase(HIST_OPEN_INTERVAL_PARAM_NAME)) this.histOpenInterval = ((Double) histOpenIntParam.getValue()).doubleValue();
		this.upToDate = false;
	}


}

