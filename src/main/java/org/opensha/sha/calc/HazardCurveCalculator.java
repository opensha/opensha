package org.opensha.sha.calc;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.calc.params.NonSupportedTRT_OptionsParam;
import org.opensha.sha.calc.params.NumStochasticEventSetsParam;
import org.opensha.sha.calc.params.SetTRTinIMR_FromSourceParam;
import org.opensha.sha.calc.params.filters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.params.filters.MagDependentDistCutoffFilter;
import org.opensha.sha.calc.params.filters.MinMagFilter;
import org.opensha.sha.calc.params.filters.SourceFilter;
import org.opensha.sha.calc.params.filters.SourceFilterManager;
import org.opensha.sha.calc.params.filters.SourceFilters;
import org.opensha.sha.calc.params.filters.SourceFiltersParam;
import org.opensha.sha.calc.params.filters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.SiteAdaptiveSource;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_EqkRupForecast;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;


/**
 * <p>Title: HazardCurveCalculator </p>
 * <p>Description: This class calculates the Hazard curve based on the
 * input parameters imr, site and eqkRupforecast or eqkRupture (for 
 * probabilistic or deterministic, respectively)</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Ned Field & Nitin Gupta & Vipin Gupta
 * @date Oct 28, 2002
 * @version 1.0
 */

public class HazardCurveCalculator extends AbstractCalculator
implements ParameterChangeWarningListener, HazardCurveCalculatorAPI {

	private final static String C = "HazardCurveCalculator";
	private final static boolean D = false;

	/*
	 * Source filters
	 */
	
	private SourceFilterManager sourceFilters;
	private SourceFiltersParam sourceFilterParam;
	
	private FixedDistanceCutoffFilter fixedDistanceFilter;
	private MagDependentDistCutoffFilter magDependentFilter;
	private TectonicRegionDistCutoffFilter trtDependentFilter;
	private MinMagFilter minMagFilter;
	
	/*
	 * Other params
	 */
	
	//Info for parameter that sets the maximum distance considered
	private NumStochasticEventSetsParam numStochEventSetRealizationsParam;
	
	//Info for parameter that tells whether to set TRT in IMR from source value
	private SetTRTinIMR_FromSourceParam setTRTinIMR_FromSourceParam;
	
	// This tells the calculator what to do if the TRT is not supported by the IMR
	private NonSupportedTRT_OptionsParam nonSupportedTRT_OptionsParam;

	private ParameterList adjustableParams;

	// misc counting and index variables
	private boolean trackProgress = false;
	private int currRuptures = -1;
	private int totRuptures = 0;
	private int sourceIndex;
	private int numSources;


	/**
	 * creates the HazardCurveCalculator object
	 */
	public HazardCurveCalculator() {
		this(SourceFiltersParam.getDefault());
	}

	/**
	 * creates the HazardCurveCalculator object
	 */
	public HazardCurveCalculator(SourceFilterManager sourceFilters) {
		// Create adjustable parameters and add to list
		this.sourceFilters = sourceFilters;
		sourceFilterParam = new SourceFiltersParam(sourceFilters);
		
		fixedDistanceFilter = (FixedDistanceCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF);
		magDependentFilter = (MagDependentDistCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.MAG_DIST_CUTOFFS);
		trtDependentFilter = (TectonicRegionDistCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.TRT_DIST_CUTOFFS);
		minMagFilter = (MinMagFilter) sourceFilters.getFilterInstance(SourceFilters.MIN_MAG);

		// Max Distance Parameter
		numStochEventSetRealizationsParam = new NumStochasticEventSetsParam();
		
		setTRTinIMR_FromSourceParam = new SetTRTinIMR_FromSourceParam();
		
		nonSupportedTRT_OptionsParam = new NonSupportedTRT_OptionsParam();

		adjustableParams = new ParameterList();
		adjustableParams.addParameter(sourceFilterParam);
		adjustableParams.addParameter(numStochEventSetRealizationsParam);
		adjustableParams.addParameter(setTRTinIMR_FromSourceParam);
		adjustableParams.addParameter(nonSupportedTRT_OptionsParam);

	}
	
	public SourceFilterManager getSourceFilterManager() {
		return sourceFilters;
	}
	
	@Override
	public List<SourceFilter> getSourceFilters() {
		return sourceFilters.getEnabledFilters();
	}


	@Override
	public void setMaxSourceDistance(double distance) {
		sourceFilters.setEnabled(SourceFilters.FIXED_DIST_CUTOFF, true);
		fixedDistanceFilter.setMaxDistance(distance);
		if (sourceFilterParam.isEditorBuilt())
			sourceFilterParam.getEditor().refreshParamEditor();
	}
	
	/**
	 * This sets the minimum magnitude considered in the calculation.  Values
	 * less than the specified amount will be ignored.
	 *
	 * @param magnitude: the minimum magnitude
	 */
	@Override
	public void setMinMagnitude(double magnitude) {
		sourceFilters.setEnabled(SourceFilters.MIN_MAG, true);
		minMagFilter.setMinMagnitude(magnitude);
		if (sourceFilterParam.isEditorBuilt())
			sourceFilterParam.getEditor().refreshParamEditor();
	}

	@Override
	public void setNumStochEventSetRealizations(int numRealizations) {
		numStochEventSetRealizationsParam.setValue(numRealizations);
	}

	@Override
	public double getMaxSourceDistance() { 
		double maxDist = Double.POSITIVE_INFINITY;
		if (sourceFilters.isEnabled(SourceFilters.FIXED_DIST_CUTOFF))
			maxDist = fixedDistanceFilter.getMaxDistance();
		if (sourceFilters.isEnabled(SourceFilters.MAG_DIST_CUTOFFS))
			maxDist = Math.min(maxDist, magDependentFilter.getMagDistFunc().getMaxX());
		if (sourceFilters.isEnabled(SourceFilters.TRT_DIST_CUTOFFS))
			maxDist = Math.min(maxDist, trtDependentFilter.getCutoffs().getLargestCutoffDist());
		return maxDist;
	}

	@Override
	public void setMagDistCutoffFunc(ArbitrarilyDiscretizedFunc magDistfunc) {
		sourceFilters.setEnabled(SourceFilters.MAG_DIST_CUTOFFS, true);
		magDependentFilter.setMagDistFunc(magDistfunc);
		if (sourceFilterParam.isEditorBuilt())
			sourceFilterParam.getEditor().refreshParamEditor();
	}

	@Override
	public void setIncludeMagDistCutoff(boolean include) {
		sourceFilters.setEnabled(SourceFilters.MAG_DIST_CUTOFFS, include);
	}

	@Override
	public ArbitrarilyDiscretizedFunc getMagDistCutoffFunc() {
		if (sourceFilters.isEnabled(SourceFilters.MAG_DIST_CUTOFFS))
			return magDependentFilter.getMagDistFunc();
		else
			return null;
	}

	@Override
	public DiscretizedFunc getAnnualizedRates(DiscretizedFunc hazFunction, double years) {
		DiscretizedFunc annualizedRateFunc = (DiscretizedFunc)hazFunction.deepClone();
		int size = annualizedRateFunc.size();
		for(int i=0;i<size;++i){
			annualizedRateFunc.set(i, - Math.log(1-annualizedRateFunc.getY(i))/years);
		}
		return annualizedRateFunc;
	}

	@Override
	public DiscretizedFunc getHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			ERF eqkRupForecast) {
		return getHazardCurve(hazFunction, site, TRTUtils.wrapInHashMap(imr), eqkRupForecast);
	}

	@Override
	public DiscretizedFunc getHazardCurve(
			DiscretizedFunc hazFunction,
			Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap, 
			ERF eqkRupForecast){
		//	  System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
		//	  System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
		//	  System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
//		if(includeMagDistFilterParam.getValue() && D)
//			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());
		signalReset();
		
		boolean setTRTinIMR_FromSource = setTRTinIMR_FromSourceParam.getValue();
		HashMap<ScalarIMR, TectonicRegionType> trtOrigVals = null;
		if (setTRTinIMR_FromSource)
			trtOrigVals = TRTUtils.getTRTsSetInIMRs(imrMap);

		this.currRuptures = -1;

		/*
		 * this determines how the calculations are done (doing it the way it's outlined
		 * in our original SRL paper gives probs greater than 1 if the total rate of events for the
		 * source exceeds 1.0, even if the rates of individual ruptures are << 1).
		 */
		boolean poissonSource = false;

//		DiscretizedFunc condProbFunc = hazFunction.deepClone();
//		DiscretizedFunc sourceHazFunc = hazFunction.deepClone();
		// these light functions are much faster on set operations
		DiscretizedFunc origHazFunc = hazFunction;
		hazFunction = new LightFixedXFunc(hazFunction);
		DiscretizedFunc condProbFunc = new LightFixedXFunc(hazFunction);
		DiscretizedFunc sourceHazFunc = new LightFixedXFunc(hazFunction);

		// declare some varibles used in the calculation
		double qkProb;
		int k;

		// get the number of points
		int numPoints = hazFunction.size();

		// define source/rup filtering stuff
		double maxDistance = getMaxSourceDistance();
		List<SourceFilter> filters = getSourceFilters();

		// initialize IMRs w/ max distance, site, and reset parameter listeners 
		// (the latter allows server versions to listen to parameter changes)
		for (ScalarIMR imr:imrMap.values()) {
			imr.resetParameterEventListeners();
			imr.setUserMaxDistance(maxDistance);
			imr.setSite(site);
		}

		// get total number of sources
		numSources = eqkRupForecast.getNumSources();
		//System.out.println("Number of Sources: "+numSources);
		//System.out.println("ERF info: "+ eqkRupForecast.getClass().getName());


		// compute the total number of ruptures for updating the progress bar
		if (trackProgress) {
			totRuptures = 0;
			sourceIndex =0;
			for(sourceIndex=0;sourceIndex<numSources;++sourceIndex) {
				ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);
				if (source instanceof SiteAdaptiveSource)
					source = ((SiteAdaptiveSource)source).getForSite(site);
				totRuptures += source.getNumRuptures();
			}
		}
		//System.out.println("Total number of ruptures:"+ totRuptures);


		// init the current rupture number (also for progress bar)
		currRuptures = 0;
		// initialize the hazard function to 1.0
		initDiscretizeValues(hazFunction, 1.0);

		// this boolean will tell us whether a source was actually used
		// (e.g., all sources could be outside MAX_DISTANCE, leading to numerical problems)
		boolean sourceUsed = false;

		if (D) System.out.println(C+": starting hazard curve calculation");

		// loop over sources
		for(sourceIndex=0;sourceIndex < numSources ;sourceIndex++) {
			if (isCancelled()) return null;
			//if (sourceIndex%1000 ==0) System.out.println("SourceIdx: " + sourceIndex);
			
			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);
			TectonicRegionType trt = source.getTectonicRegionType();
			
			if (source instanceof SiteAdaptiveSource)
				source = ((SiteAdaptiveSource)source).getForSite(site);
			
			// get the IMR
			ScalarIMR imr = TRTUtils.getIMRforTRT(imrMap, trt);

			// Set Tectonic Region Type in IMR
			if(setTRTinIMR_FromSource) { // (otherwise leave as originally set)
				TRTUtils.setTRTinIMR(imr, trt, nonSupportedTRT_OptionsParam, trtOrigVals.get(imr));
			}

			// apply any filters
			if (canSkipSource(filters, source, site)) {
				currRuptures += source.getNumRuptures();  //update progress bar for skipped ruptures
				continue;
			}

			// determine whether it's poissonian (calcs depend on this)
			poissonSource = source.isSourcePoissonian();

			// initialize the source hazard function to 0.0 if it's a non-poisson source
			if(!poissonSource)
				initDiscretizeValues(sourceHazFunc, 0.0);

			// get the number of ruptures for the current source
			int numRuptures = source.getNumRuptures();

			// loop over these ruptures
			for(int n=0; n < numRuptures ; n++,++currRuptures) {
				
				ProbEqkRupture rupture = source.getRupture(n);

				try {
					// get the rupture probability
					qkProb = rupture.getProbability();
					
					if (qkProb == 0d)
						continue;
					
					// apply any filters
					if (canSkipRupture(filters, rupture, site)) {
						continue;
					}
					
					// indicate that a source has been used (put here because of above filters)
					sourceUsed = true;

					// set the EqkRup in the IMR
					imr.setEqkRupture(rupture);

					// get the conditional probability of exceedance from the IMR
					condProbFunc = imr.getExceedProbabilities(condProbFunc);
					
					// For poisson source
					if(poissonSource) {
						/* First make sure the probability isn't 1.0 (or too close); otherwise rates are
					infinite and all IMLs will be exceeded (because of ergodic assumption).  This
					can happen if the number of expected events (over the timespan) exceeds ~37,
					because at this point 1.0-Math.exp(-num) = 1.0 by numerical precision (and thus,
					an infinite number of events).  The number 30 used in the check below provides a
					safe margin.
						 */
						if(Math.log(1.0-qkProb) < -30.0)
							throw new RuntimeException("Error: The probability for this ProbEqkRupture ("+qkProb+
							") is too high for a Possion source (~infinite number of events)");
						// we're going to do a bunch of (1-prob)^value
						// Math.pow(a, b) is about 3 times slower than Math.exp(a*b)
						// we can speed this up by replacing the power with this log equivalence and precomputing ln(a):
						// a^b = exp(b*ln(a))
//						double lnBase = Math.log(1-qkProb);
						double lnBase = Math.log1p(-qkProb);
						Preconditions.checkState(Double.isFinite(lnBase), "Bad lnBase=%s for qkProb=%s", lnBase, qkProb);
						for(k=0;k<numPoints;k++) {
							hazFunction.set(k,hazFunction.getY(k)*Math.exp(lnBase*condProbFunc.getY(k)));
//							hazFunction.set(k,hazFunction.getY(k)*Math.pow(1-qkProb,condProbFunc.getY(k)));
						}
					}
					// For non-Poissin source
					else
						for(k=0;k<numPoints;k++)
							sourceHazFunc.set(k,sourceHazFunc.getY(k) + qkProb*condProbFunc.getY(k));
				} catch (Throwable t) {
					System.err.println("Error occured while calculating hazard curve " +
							"for rupture:  "+sourceIndex+" "+n);
					System.err.println("Type: "+t.getClass());
					System.err.println("Message: "+t.getMessage());
					System.err.println("Source Name: "+source.getName());
					System.err.println("Surface Type: "+rupture.getRuptureSurface().getClass().getName());
					System.err.println("Mag: "+rupture.getMag());
					System.err.println("Probability: "+rupture.getProbability());
					System.err.println("ERF: "+eqkRupForecast.getName());
					System.err.println("IMR: "+imr.getName());
					System.err.println("Site: "+site);
					System.err.println("Curve: "+hazFunction);
					System.err.flush();
					//System.err.println("RupM: "+source.getRupture(n).getMag());
					ExceptionUtils.throwAsRuntimeException(t);
				}
			}
			// for non-poisson source:
			if(!poissonSource)
				for(k=0;k<numPoints;k++)
					hazFunction.set(k,hazFunction.getY(k)*(1-sourceHazFunc.getY(k)));
		}

		int i;
		// finalize the hazard function
		if(sourceUsed) {
			for(i=0;i<numPoints;++i)
				origHazFunc.set(i,1-hazFunction.getY(i));
			hazFunction = origHazFunc;
		} else {
			hazFunction = origHazFunc;
			this.initDiscretizeValues(hazFunction, 0.0);
		}

		if (D) System.out.println(C+"hazFunction.toString"+hazFunction.toString());

		// System.out.println("numRupRejected="+numRupRejected);
		
		// reset TRT parameter in IMRs
		if (trtOrigVals != null)
			TRTUtils.resetTRTsInIMRs(trtOrigVals);

		return hazFunction;
	}
	
	public static boolean canSkipSource(Collection<SourceFilter> filters, ProbEqkSource source, Site site) {
		if (filters == null || filters.isEmpty())
			return false;
		if (!filters.isEmpty()) {
			// source-site distance
			double distance = source.getMinDistance(site);
			
			for (SourceFilter filter : filters)
				if (filter.canSkipSource(source, site, distance))
					return true;
		}
		return false;
	}
	
	public static boolean canSkipRupture(Collection<SourceFilter> filters, EqkRupture rupture, Site site) {
		if (filters == null || filters.isEmpty())
			return false;
		if (!filters.isEmpty()) {
			for (SourceFilter filter : filters)
				if (filter.canSkipRupture(rupture, site))
					return true;
		}
		return false;
	}

	@Override
	public DiscretizedFunc getAverageEventSetHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			ERF eqkRupForecast) {
		signalReset();

//		System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
//		System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
//		System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
//		if(includeMagDistFilterParam.getValue())
//			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());

		int numEventSets = numStochEventSetRealizationsParam.getValue();
		DiscretizedFunc hazCurve;
		hazCurve = (DiscretizedFunc)hazFunction.deepClone();
		initDiscretizeValues(hazFunction, 0);
		int numPts=hazCurve.size();
		// for progress bar
		currRuptures=0;
		//	  totRuptures=numEventSets;

		for(int i=0;i<numEventSets;i++) {
			if (isCancelled()) return null;
			List<EqkRupture> events = eqkRupForecast.drawRandomEventSet(site, getSourceFilters());
			if(i==0) totRuptures = events.size()*numEventSets; // this is an approximate total number of events
			currRuptures+=events.size();
			getEventSetHazardCurve( hazCurve,site, imr, events, false);
			for(int x=0; x<numPts; x++)
				hazFunction.set(x, hazFunction.getY(x)+hazCurve.getY(x));
		}
		for(int x=0; x<numPts; x++)
			hazFunction.set(x, hazFunction.getY(x)/numEventSets);
		return hazFunction;
	}

	@Override
	public DiscretizedFunc getEventSetHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			List<EqkRupture> eqkRupList, boolean updateCurrRuptures) {
		signalReset();

		DiscretizedFunc condProbFunc = hazFunction.deepClone();

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		// declare some varibles used in the calculation
		int k;

		// get the number of points
		int numPoints = hazFunction.size();

		// define distance filtering stuff
		double maxDistance = getMaxSourceDistance();
		List<SourceFilter> filters = getSourceFilters();

		// set the maximum distance in the attenuation relationship
		imr.setUserMaxDistance(maxDistance);

		int totRups = eqkRupList.size();
		// progress bar stuff
		if(updateCurrRuptures) {
			totRuptures = totRups;
			currRuptures = 0;
		}

		// initialize the hazard function to 1.0 (initial total non-exceedance probability)
		initDiscretizeValues(hazFunction, 1.0);

		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C+": starting hazard curve calculation");

		//	  System.out.println("totRuptures="+totRuptures);


		// loop over ruptures
		for(int n=0; n < totRups ; n++) {
			if (isCancelled()) return null;

			if(updateCurrRuptures)++currRuptures;

			EqkRupture rupture = eqkRupList.get(n);
			
			// apply any filters
			if (canSkipRupture(filters, rupture, site))
				continue;

			// set the EqkRup in the IMR
			imr.setEqkRupture(rupture);

			// get the conditional probability of exceedance from the IMR
			condProbFunc=imr.getExceedProbabilities(condProbFunc);

			// multiply this into the total non-exceedance probability
			// (get the product of all non-eceedance probabilities)
			for(k=0;k<numPoints;k++) 
				hazFunction.set(k,hazFunction.getY(k)*(1.0-condProbFunc.getY(k)));

		}

		//	  System.out.println(C+"hazFunction.toString"+hazFunction.toString());

		// now convert from total non-exceed prob to total exceed prob
		for(int i=0;i<numPoints;++i)
			hazFunction.set(i,1.0-hazFunction.getY(i));

		//	  System.out.println(C+"hazFunction.toString"+hazFunction.toString());

		//	  System.out.println("numRupRejected="+numRupRejected);

		return hazFunction;
	}


	
	/**
	 * This sums the conditional exceedance functions of all ruptures, giving the expected
	 * number of exceedances for this events set (given aleatory variability in IML)
	 * @param hazFunction
	 * @param site
	 * @param imr
	 * @param eqkRupList
	 * @param updateCurrRuptures
	 * @return
	 */
	public DiscretizedFunc getEventSetExpNumExceedCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, List<EqkRupture> eqkRupList, boolean updateCurrRuptures) {
		signalReset();

		DiscretizedFunc condProbFunc = hazFunction.deepClone();

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		// declare some varibles used in the calculation
		int k;

		// get the number of points
		int numPoints = hazFunction.size();

		// define distance filtering stuff
		double maxDistance = getMaxSourceDistance();
		List<SourceFilter> filters = getSourceFilters();

		// set the maximum distance in the attenuation relationship
		imr.setUserMaxDistance(maxDistance);

		int totRups = eqkRupList.size();
		// progress bar stuff
		if(updateCurrRuptures) {
			totRuptures = totRups;
			currRuptures = 0;
		}

		// initialize the hazard function to 1.0 (initial total non-exceedance probability)
		initDiscretizeValues(hazFunction, 0.0);

		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C+": starting hazard curve calculation");

		//	  System.out.println("totRuptures="+totRuptures);


		// loop over ruptures
		for(int n=0; n < totRups ; n++) {
			if (isCancelled()) return null;
			
			if(updateCurrRuptures)++currRuptures;

			EqkRupture rupture = eqkRupList.get(n);
			
			// apply any filters
			if (canSkipRupture(filters, rupture, site))
				continue;

			/*
    		// apply mag-dist cutoff filter
    		if(includeMagDistFilter) {
    			//distance=??; // NEED TO COMPUTE THIS DISTANCE
     			if(rupture.getMag() < magDistCutoffParam.getValue().getInterpolatedY(distance) {
    			numRupRejected += 1;
    			continue;
    		}
			 */

			// set the EqkRup in the IMR
			imr.setEqkRupture(rupture);

			// get the conditional probability of exceedance from the IMR
			condProbFunc=imr.getExceedProbabilities(condProbFunc);

			// add this cond prob to hazard function
			for(k=0;k<numPoints;k++) 
				hazFunction.set(k,hazFunction.getY(k)+condProbFunc.getY(k));

		}

		//	  System.out.println(C+"hazFunction.toString"+hazFunction.toString());

		//	  System.out.println("numRupRejected="+numRupRejected);

		return hazFunction;
	}


	/**
	 * The computed the exceedance curve using random IML samples.  The curve is
	 * 1.0 up to the maximum IML sampled (among all ruptures) and zero above that.
	 * @param hazFunction
	 * @param site
	 * @param imr
	 * @param eqkRupList
	 * @param updateCurrRuptures
	 * @return
	 */
	public DiscretizedFunc getEventSetHazardCurveRandomIML(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, List<EqkRupture> eqkRupList, boolean updateCurrRuptures, Random random) {
		signalReset();

		if(random == null)
			random = new Random();

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		// get the number of points
		int numPoints = hazFunction.size();

		// define distance filtering stuff
		double maxDistance = getMaxSourceDistance();
		List<SourceFilter> filters = getSourceFilters();

		// set the maximum distance in the attenuation relationship
		imr.setUserMaxDistance(maxDistance);

		int totRups = eqkRupList.size();
		// progress bar stuff
		if(updateCurrRuptures) {
			totRuptures = totRups;
			currRuptures = 0;
		}

		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C+": starting hazard curve calculation");

		//	  System.out.println("totRuptures="+totRuptures);

		double maxIML = Double.NEGATIVE_INFINITY;
		
		// loop over ruptures
		for(int n=0; n < totRups ; n++) {
			if (isCancelled()) return null;

			if(updateCurrRuptures)++currRuptures;

			EqkRupture rupture = eqkRupList.get(n);
			
			// apply any filters
			if (canSkipRupture(filters, rupture, site))
				continue;

			/*
    		// apply mag-dist cutoff filter
    		if(includeMagDistFilter) {
    			//distance=??; // NEED TO COMPUTE THIS DISTANCE
     			if(rupture.getMag() < magDistCutoffParam.getValue().getInterpolatedY(distance) {
    			numRupRejected += 1;
    			continue;
    		}
			 */

			// set the EqkRup in the IMR
			imr.setEqkRupture(rupture);

			double randIML = imr.getRandomIML(random);
			if(maxIML < randIML)
				maxIML = randIML;

		}

		// now convert from total non-exceed prob to total exceed prob
		for(int i=0;i<numPoints;++i)
			if(hazFunction.getX(i) < maxIML)
				hazFunction.set(i,1.0);
			else
				hazFunction.set(i,0.0);

		return hazFunction;
	}
	
	/**
	 * This computes the number of exceedances curve using random IML samples.
	 * @param hazFunction
	 * @param site
	 * @param imr
	 * @param eqkRupList
	 * @param updateCurrRuptures
	 * @return
	 */
	public DiscretizedFunc getEventSetNumExceedCurveRandomIML(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, List<EqkRupture> eqkRupList, boolean updateCurrRuptures, Random random) {
		signalReset();

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		// define distance filtering stuff
		double maxDistance = getMaxSourceDistance();
		List<SourceFilter> filters = getSourceFilters();

		// set the maximum distance in the attenuation relationship
		imr.setUserMaxDistance(maxDistance);

		int totRups = eqkRupList.size();
		// progress bar stuff
		if(updateCurrRuptures) {
			totRuptures = totRups;
			currRuptures = 0;
		}

		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C+": starting hazard curve calculation");

		//	  System.out.println("totRuptures="+totRuptures);

		// loop over ruptures
		for(int n=0; n < totRups ; n++) {
			if (isCancelled()) return null;

			if(updateCurrRuptures)++currRuptures;

			EqkRupture rupture = eqkRupList.get(n);
			
			// apply any filters
			if (canSkipRupture(filters, rupture, site))
				continue;


			/*
    		// apply mag-dist cutoff filter
    		if(includeMagDistFilter) {
    			//distance=??; // NEED TO COMPUTE THIS DISTANCE
     			if(rupture.getMag() < magDistCutoffParam.getValue().getInterpolatedY(distance) {
    			numRupRejected += 1;
    			continue;
    		}
			 */

			// set the EqkRup in the IMR
			imr.setEqkRupture(rupture);

			double randIML = imr.getRandomIML(random);
			
			for(int i=0;i<hazFunction.size();++i) {
				if(hazFunction.getX(i) < randIML)
					hazFunction.set(i,hazFunction.getY(i)+1.0);
				else
					break;
			}
		}

		return hazFunction;
	}



	@Override
	public DiscretizedFunc getHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, EqkRupture rupture) {

//		System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
//		System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
//		System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
//		if(includeMagDistFilterParam.getValue())
//			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());
		
		List<SourceFilter> filters = getSourceFilters();

		if (canSkipRupture(filters, rupture, site)) {
			hazFunction.scale(0.0);
			return hazFunction;
		}

		// resetting the Parameter change Listeners on the AttenuationRelationship parameters,
		// allowing the Server version of our application to listen to the parameter changes.
		( (AttenuationRelationship) imr).resetParameterEventListeners();


		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C + ": starting hazard curve calculation");

		// set the EqkRup in the IMR
		imr.setEqkRupture(rupture);

		// get the conditional probability of exceedance from the IMR
		hazFunction = imr.getExceedProbabilities(hazFunction);

		if (D) System.out.println(C + "hazFunction.toString" + hazFunction.toString());

		return hazFunction;
	}
	
	@Override
	public void setTrackProgress(boolean trackProgress) {
		this.trackProgress = trackProgress;
	}
	
	@Override
	public boolean isTrackProgress() {
		return trackProgress;
	}

	@Override
	public int getCurrRuptures() {
		if (trackProgress)
			return this.currRuptures;
		return -1;
	}

	@Override
	public int getTotRuptures() {
		if (trackProgress)
			return this.totRuptures;
		return -1;
	}

	/**
	 * Initialize the prob as 1 for the Hazard function
	 *
	 * @param arb
	 */
	protected void initDiscretizeValues(DiscretizedFunc arb, double val){
		int num = arb.size();
		for(int i=0;i<num;++i)
			arb.set(i,val);
		Preconditions.checkState(num == arb.size(), "initializing X values changed size of function! " +
				"It is possible that there is a NaN or infitite value in the hazard curve.");
	}

	@Override
	public ParameterList getAdjustableParams() {
		return this.adjustableParams;
	}

	@Override
	public void setAdjustableParams(ParameterList paramList) {
		this.adjustableParams = paramList;
		this.sourceFilterParam = (SourceFiltersParam)paramList.getParameter(SourceFiltersParam.NAME);
		sourceFilters = sourceFilterParam.getValue();
		
		fixedDistanceFilter = (FixedDistanceCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF);
		magDependentFilter = (MagDependentDistCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.MAG_DIST_CUTOFFS);
		trtDependentFilter = (TectonicRegionDistCutoffFilter) sourceFilters.getFilterInstance(SourceFilters.TRT_DIST_CUTOFFS);
		minMagFilter = (MinMagFilter) sourceFilters.getFilterInstance(SourceFilters.MIN_MAG);
		this.numStochEventSetRealizationsParam =
			(NumStochasticEventSetsParam)paramList.getParameter(NumStochasticEventSetsParam.NAME);
		this.setTRTinIMR_FromSourceParam =
			(SetTRTinIMR_FromSourceParam)paramList.getParameter(SetTRTinIMR_FromSourceParam.NAME);
		this.nonSupportedTRT_OptionsParam =
			(NonSupportedTRT_OptionsParam)paramList.getParameter(NonSupportedTRT_OptionsParam.NAME);
	}

	@Override
	public ListIterator<Parameter<?>> getAdjustableParamsIterator() {
		return adjustableParams.getParametersIterator();
	}

	/**
	 * This tests whether the average over many curves from getEventSetCurve
	 * equals what is given by getHazardCurve.
	 */
	public void testEventSetHazardCurve(int numIterations) {
		// set distance filter large since these are handled slightly differently in each calc
//		maxDistanceParam.setValue(300);
		setMaxSourceDistance(300d);
		// do not apply mag-dist fileter
		numStochEventSetRealizationsParam.setValue(numIterations);

		ScalarIMR imr = new BJF_1997_AttenRel(this); 
		imr.setParamDefaults();
		imr.setIntensityMeasure("PGA");

		Site site = new Site();
		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
		while(it.hasNext())
			site.addParameter(it.next());
		site.setLocation(new Location(34,-118));

		AbstractERF eqkRupForecast = new Frankel96_EqkRupForecast();
		eqkRupForecast.updateForecast();

		ArbitrarilyDiscretizedFunc hazCurve = new ArbitrarilyDiscretizedFunc();
		hazCurve.set(-3.,1);  // log(0.001)
		hazCurve.set(-2.,1);
		hazCurve.set(-1.,1);
		hazCurve.set(1.,1);
		hazCurve.set(2.,1);   // log(10)

		hazCurve.setName("Hazard Curve");

		this.getHazardCurve(hazCurve, site, imr, eqkRupForecast);

		System.out.println(hazCurve.toString());

		ArbitrarilyDiscretizedFunc aveCurve = hazCurve.deepClone();
		getAverageEventSetHazardCurve(aveCurve,site, imr,eqkRupForecast);

		/*
	this.initDiscretizeValues(aveCurve, 0.0);
	ArbitrarilyDiscretizedFunc curve = hazCurve.deepClone();
	for(int i=0; i<numIterations;i++) {
		try {
			getEventSetHazardCurve(curve, site, imr, eqkRupForecast.drawRandomEventSet());
			for(int x=0; x<curve.getNum();x++) aveCurve.set(x, aveCurve.getY(x)+curve.getY(x));
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	for(int x=0; x<curve.getNum();x++) aveCurve.set(x, aveCurve.getY(x)/numIterations);
		 */

		aveCurve.setName("Ave from "+numIterations+" event sets");
		System.out.println(aveCurve.toString());

	}

	// added this and the associated API implementation to instantiate BJF_1997_AttenRel in the above
	public void parameterChangeWarning( ParameterChangeWarningEvent event ) {};

	@Override
	protected boolean isCancelled() {
		boolean cancelled = super.isCancelled();
		if (D && cancelled) System.out.println("Signal caught in " + C);
		return cancelled;
	}

	// this is temporary for testing purposes
	public static void main(String[] args) {
		HazardCurveCalculator calc;
		calc = new HazardCurveCalculator();
		calc.testEventSetHazardCurve(1000);
		/*
    double temp1, temp2, temp3, temp4;
    boolean OK;
    for(double n=1; n<2;n += 0.02) {
      temp1 = Math.pow(10,n);
      temp2 = 1.0-Math.exp(-temp1);
      temp3 = Math.log(1.0-temp2);
      temp4 = (temp3+temp1)/temp1;
      OK = temp1<=30;
      System.out.println((float)n+"\t"+temp1+"\t"+temp2+"\t"+temp3+"\t"+temp4+"\t"+OK);
    }
		 */
	}
}



