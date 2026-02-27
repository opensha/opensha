package scratch.peter.nshmp;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.nshmp2.tmp.JordanMadridHazardCalc;
import org.opensha.sha.calc.HazardCurveCalculatorAPI;
import org.opensha.sha.calc.params.NonSupportedTRT_OptionsParam;
import org.opensha.sha.calc.params.NumStochasticEventSetsParam;
import org.opensha.sha.calc.params.SetTRTinIMR_FromSourceParam;
import org.opensha.sha.calc.sourceFilters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.sourceFilters.MagDependentDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.params.MagDistCutoffParam;
import org.opensha.sha.calc.sourceFilters.params.MaxDistanceParam;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_EqkRupForecast;
import org.opensha.sha.faultSurface.PointSurface;
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

public class HazardCurveCalculatorNSHMP
implements HazardCurveCalculatorAPI, ParameterChangeWarningListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected final static String C = "HazardCurveCalculator";
	protected final static boolean D = false;

	//Info for parameter that sets the maximum distance considered
	private FixedDistanceCutoffFilter maxDistFilter;
	private MaxDistanceParam maxDistanceParam;

	//Info for parameter tells whether to apply a magnitude-dependent distance cutoff
	private static final String INCLUDE_MAG_DIST_PARAM_NAME = "Use Mag-Distance Filter?";
	private BooleanParameter includeMagDistFilterParam;
	
	//Info for parameter that specifies a magnitude-dependent distance cutoff
	// (distance on x-axis and mag on y-axis)
	private MagDependentDistCutoffFilter magDistFilter;
	private MagDistCutoffParam magDistCutoffParam;
	
	//Info for parameter that sets the maximum distance considered
	private NumStochasticEventSetsParam numStochEventSetRealizationsParam;
	
	//Info for parameter that tells whether to set TRT in IMR from source value
	private SetTRTinIMR_FromSourceParam setTRTinIMR_FromSourceParam;
	
	// This tells the calculator what to do if the TRT is not supported by the IMR
	private NonSupportedTRT_OptionsParam nonSupportedTRT_OptionsParam;
	

	private ParameterList adjustableParams;

	// misc counting and index variables
	protected int currRuptures = -1;
	protected int totRuptures=0;
	protected int sourceIndex;
	protected int numSources;


	/**
	 * creates the HazardCurveCalculator object
	 */
	public HazardCurveCalculatorNSHMP() {

		// Create adjustable parameters and add to list

		// Max Distance Parameter
		maxDistFilter = new FixedDistanceCutoffFilter();
		maxDistanceParam = maxDistFilter.getParam();
		maxDistanceParam.setValue(300.0);

		// Include Mag-Distance Filter Parameter
		includeMagDistFilterParam = new BooleanParameter(INCLUDE_MAG_DIST_PARAM_NAME, false);

		magDistFilter = new MagDependentDistCutoffFilter();
		magDistCutoffParam = magDistFilter.getParam();

		// Max Distance Parameter
		numStochEventSetRealizationsParam = new NumStochasticEventSetsParam();
		
		setTRTinIMR_FromSourceParam = new SetTRTinIMR_FromSourceParam();
		
		nonSupportedTRT_OptionsParam = new NonSupportedTRT_OptionsParam();

		adjustableParams = new ParameterList();
		adjustableParams.addParameter(maxDistanceParam);
		adjustableParams.addParameter(numStochEventSetRealizationsParam);
		adjustableParams.addParameter(includeMagDistFilterParam);
		adjustableParams.addParameter(magDistCutoffParam);
		adjustableParams.addParameter(setTRTinIMR_FromSourceParam);
		adjustableParams.addParameter(nonSupportedTRT_OptionsParam);

	}


	@Override
	public void setMaxSourceDistance(double distance){
		maxDistanceParam.setValue(distance);
	}
	
	@Override
	public void setMinMagnitude(double magnitude) {
		throw new RuntimeException("Method not supported");
	}



	@Override
	public void setNumStochEventSetRealizations(int numRealizations) {
		numStochEventSetRealizationsParam.setValue(numRealizations);
	}

	@Override
	public double getMaxSourceDistance() { 
		return maxDistanceParam.getValue().doubleValue(); 
	}

	@Override
	public void setMagDistCutoffFunc(ArbitrarilyDiscretizedFunc magDistfunc) {
		includeMagDistFilterParam.setValue(true);
		magDistCutoffParam.setValue(magDistfunc);
	}

	@Override
	public void setIncludeMagDistCutoff(boolean include) {
		this.includeMagDistFilterParam.setValue(include);
	}

	@Override
	public ArbitrarilyDiscretizedFunc getMagDistCutoffFunc() {
		if(includeMagDistFilterParam.getValue())
			return magDistCutoffParam.getValue();
		else
			return null;
	}


	@Override
	public DiscretizedFunc getAnnualizedRates(DiscretizedFunc hazFunction,double years) {
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
	
	public DiscretizedFunc getHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			ERF eqkRupForecast, DeterministicResult detResult, JordanMadridHazardCalc.ResWriter writer) {
		return getHazardCurve(hazFunction, site, TRTUtils.wrapInHashMap(imr), eqkRupForecast,
			detResult, writer);
	}

	@Override
	public DiscretizedFunc getHazardCurve(
			DiscretizedFunc hazFunction,
			Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap, 
			ERF eqkRupForecast){
		return getHazardCurve(hazFunction, site, imrMap, eqkRupForecast, null, null);
		// for compatibility with totally unreferenced interface
	}

	public DiscretizedFunc getHazardCurve(
			DiscretizedFunc hazFunction,
			Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap, 
			ERF eqkRupForecast, DeterministicResult detResult, JordanMadridHazardCalc.ResWriter resWriter){

		//	  System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
		//	  System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
		//	  System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
//		if(includeMagDistFilterParam.getValue() && D)
//			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());
		
		boolean setTRTinIMR_FromSource = setTRTinIMR_FromSourceParam.getValue();
		HashMap<ScalarIMR, TectonicRegionType> trtOrigVals = null;
		if (setTRTinIMR_FromSource)
			trtOrigVals = TRTUtils.getTRTsSetInIMRs(imrMap);

		this.currRuptures = -1;

		/* this determines how the calucations are done (doing it the way it's outlined
    in our original SRL paper gives probs greater than 1 if the total rate of events for the
    source exceeds 1.0, even if the rates of individual ruptures are << 1).
		 */
		boolean poissonSource = false;

		DiscretizedFunc condProbFunc = (ArbitrarilyDiscretizedFunc) hazFunction.deepClone();
		DiscretizedFunc sourceHazFunc = (ArbitrarilyDiscretizedFunc) hazFunction.deepClone();

		// declare some varibles used in the calculation
		double qkProb, distance;
		int k;

		// get the number of points
		int numPoints = hazFunction.size();

		// define distance filtering stuff
		double maxDistance = maxDistanceParam.getValue();
		boolean includeMagDistFilter = includeMagDistFilterParam.getValue();
		double magThresh=0.0;

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
		totRuptures = 0;
		sourceIndex =0;
		for(sourceIndex=0;sourceIndex<numSources;++sourceIndex)
			totRuptures+=eqkRupForecast.getSource(sourceIndex).getNumRuptures();
		//System.out.println("Total number of ruptures:"+ totRuptures);


		// init the current rupture number (also for progress bar)
		currRuptures = 0;
		int numRupRejected =0;

		// initialize the hazard function to 1.0
		initDiscretizeValues(hazFunction, 1.0);

		// this boolean will tell us whether a source was actually used
		// (e.g., all sources could be outside MAX_DISTANCE, leading to numerical problems)
		boolean sourceUsed = false;

		if (D) System.out.println(C+": starting hazard curve calculation");

		// loop over sources
		for(sourceIndex=0;sourceIndex < numSources ;sourceIndex++) {

			//if (sourceIndex%1000 ==0) System.out.println("SourceIdx: " + sourceIndex);
			
			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);
			TectonicRegionType trt = source.getTectonicRegionType();
			
			// get the IMR
			ScalarIMR imr = TRTUtils.getIMRforTRT(imrMap, trt);

			// Set Tectonic Region Type in IMR
			if(setTRTinIMR_FromSource) { // (otherwise leave as originally set)
				TRTUtils.setTRTinIMR(imr, trt, nonSupportedTRT_OptionsParam, trtOrigVals.get(imr));
			}

			// compute the source's distance from the site and skip if it's too far away
			distance = source.getMinDistance(site);

			// apply distance cutoff to source
			if(distance > maxDistance) {
				currRuptures += source.getNumRuptures();  //update progress bar for skipped ruptures
				continue;
			}
			//System.out.println(" dist: " + distance);

			// get magThreshold if we're to use the mag-dist cutoff filter
			if(includeMagDistFilter) {
				magThresh = magDistCutoffParam.getValue().getInterpolatedY(distance);
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
				
				EqkRupture rupture = source.getRupture(n);

				try {
					// get the rupture probability
					qkProb = ((ProbEqkRupture)rupture).getProbability();

					// apply magThreshold if we're to use the mag-dist cutoff filter
					if(includeMagDistFilter && rupture.getMag() < magThresh) {
						numRupRejected += 1;
						continue;
					}

					// indicate that a source has been used (put here because of above filter)
					sourceUsed = true;

					// set the EqkRup in the IMR
					imr.setEqkRupture(rupture);

					// get the conditional probability of exceedance from the IMR
					condProbFunc = imr.getExceedProbabilities(condProbFunc);
					
//					if (resWriter != null && ((NSHMP_ERF) eqkRupForecast).getName().startsWith("NMSZnocl")) {
//						double totRate = NSHMP_Utils.probToRate(qkProb, 1); // magWt * faultWt * srcRate (500=0.002 1000=0.001)
//						String id = source.getName();
//						double magFltWt = 0.0;
//						double srcRate = 0.0;
//						if (((NSHMP_ERF) eqkRupForecast).getName().contains("500")) {
//							srcRate = 1.0 / 500.0;
//							magFltWt = totRate / srcRate; // magWt * faultWt
//							id += " 500yr NoClust";
//						} else {
//							srcRate = 1.0 / 1000.0;
//							magFltWt = totRate / srcRate; // magWt * faultWt
//							id += " 1000yr NoClust";
//						}
//						
//						DiscretizedFunc printFn = condProbFunc.deepClone();
//						for (Point2D p : printFn) {
//							printFn.set(p.getX(), p.getY() * srcRate);
//						}
//						
//						double outWt = magFltWt * ((NSHMP_ERF) eqkRupForecast).getSourceWeight() * 0.5;
//						
//						String id1 = id + " bgAB";
//						DiscretizedFunc f1 = printFn.deepClone();
//						JordanMadridHazardCalc.addFuncs(f1, JordanMadridHazardCalc.dfAB);
//						resWriter.writeCurve(id1, outWt, f1);
//						
//						String id2 = id + " bgJ";
//						DiscretizedFunc f2 = printFn.deepClone();
//						JordanMadridHazardCalc.addFuncs(f2, JordanMadridHazardCalc.dfJ);
//						resWriter.writeCurve(id2, outWt, f2);
//
////						double outWt = magFltWt * ((NSHMP_ERF) eqkRupForecast).getSourceWeight();
////						resWriter.writeCurve(id, outWt, printFn);
//					}

					
					if (detResult != null) {
						double median = Math.exp(imr.getMean());
						if (detResult.median < median) {
							detResult.median = median;
							detResult.mag = rupture.getMag();
							detResult.rRup = rupture.getRuptureSurface().getDistanceRup(site.getLocation());
							detResult.name = source.getName();
						}
					}
					
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

						for(k=0;k<numPoints;k++)
							hazFunction.set(k,hazFunction.getY(k)*Math.pow(1-qkProb,condProbFunc.getY(k)));
					}
					// For non-Poissin source
					else
						for(k=0;k<numPoints;k++)
							sourceHazFunc.set(k,sourceHazFunc.getY(k) + qkProb*condProbFunc.getY(k));
				} catch (Throwable t) {
					System.err.println("Error occured while calculating hazard curve " +
							"for rupture:  "+sourceIndex+" "+n);
					System.err.println("Source Name: "+source.getName());
					System.err.println("Surface Type: "+rupture.getRuptureSurface().getClass().getName());
					System.err.println("ERF: "+eqkRupForecast.getName());
					System.err.println("IMR: "+imr.getName());
					System.err.println("Site: "+site);
					System.err.println("Curve: "+hazFunction);
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
		if(sourceUsed)
			for(i=0;i<numPoints;++i)
				hazFunction.set(i,1-hazFunction.getY(i));
		else
			this.initDiscretizeValues(hazFunction, 0.0);

		if (D) System.out.println(C+"hazFunction.toString"+hazFunction.toString());

		// System.out.println("numRupRejected="+numRupRejected);
		
		// reset TRT parameter in IMRs
		if (trtOrigVals != null)
			TRTUtils.resetTRTsInIMRs(trtOrigVals);

		return hazFunction;
	}

	@Override
	public DiscretizedFunc getAverageEventSetHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			ERF eqkRupForecast) {

		System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
		System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
		System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
		if(includeMagDistFilterParam.getValue())
			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());

		int numEventSets = numStochEventSetRealizationsParam.getValue();
		DiscretizedFunc hazCurve;
		hazCurve = (DiscretizedFunc)hazFunction.deepClone();
		initDiscretizeValues(hazFunction, 0);
		int numPts=hazCurve.size();
		// for progress bar
		currRuptures=0;
		//	  totRuptures=numEventSets;

		for(int i=0;i<numEventSets;i++) {
			List<EqkRupture> events = eqkRupForecast.drawRandomEventSet();
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


		ArbitrarilyDiscretizedFunc condProbFunc = (ArbitrarilyDiscretizedFunc) hazFunction.deepClone();

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		// declare some varibles used in the calculation
		int k;

		// get the number of points
		int numPoints = hazFunction.size();

		// define distance filtering stuff
		double maxDistance = maxDistanceParam.getValue();

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

			if(updateCurrRuptures)++currRuptures;

			EqkRupture rupture = eqkRupList.get(n);


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
			condProbFunc=(ArbitrarilyDiscretizedFunc)imr.getExceedProbabilities(condProbFunc);

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




	@Override
	public DiscretizedFunc getHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, EqkRupture rupture) {

		System.out.println("Haz Curv Calc: maxDistanceParam.getValue()="+maxDistanceParam.getValue().toString());
		System.out.println("Haz Curv Calc: numStochEventSetRealizationsParam.getValue()="+numStochEventSetRealizationsParam.getValue().toString());
		System.out.println("Haz Curv Calc: includeMagDistFilterParam.getValue()="+includeMagDistFilterParam.getValue().toString());
		if(includeMagDistFilterParam.getValue())
			System.out.println("Haz Curv Calc: magDistCutoffParam.getValue()="+magDistCutoffParam.getValue().toString());


		// resetting the Parameter change Listeners on the AttenuationRelationship parameters,
		// allowing the Server version of our application to listen to the parameter changes.
		( (AttenuationRelationship) imr).resetParameterEventListeners();


		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C + ": starting hazard curve calculation");

		// set the EqkRup in the IMR
		imr.setEqkRupture(rupture);

		// get the conditional probability of exceedance from the IMR
		hazFunction = (ArbitrarilyDiscretizedFunc) imr.getExceedProbabilities(hazFunction);

		if (D) System.out.println(C + "hazFunction.toString" + hazFunction.toString());

		return hazFunction;
	}

	@Override
	public int getCurrRuptures(){
		return this.currRuptures;
	}

	@Override
	public int getTotRuptures(){
		return this.totRuptures;
	}

	@Override
	public void stopCalc(){
		sourceIndex = numSources;
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
		this.maxDistanceParam = (MaxDistanceParam)paramList.getParameter(MaxDistanceParam.NAME);
		this.numStochEventSetRealizationsParam =
			(NumStochasticEventSetsParam)paramList.getParameter(NumStochasticEventSetsParam.NAME);
		this.includeMagDistFilterParam =
			(BooleanParameter)paramList.getParameter(INCLUDE_MAG_DIST_PARAM_NAME);
		this.magDistCutoffParam = (MagDistCutoffParam)paramList.getParameter(MagDistCutoffParam.NAME);
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
		maxDistanceParam.setValue(300);
		// do not apply mag-dist fileter
		includeMagDistFilterParam.setValue(false);
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
	public void parameterChangeWarning( ParameterChangeWarningEvent event ) {}


	@Override
	public List<SourceFilter> getSourceFilters() {
		List<SourceFilter> filters = new ArrayList<>();
		filters.add(maxDistFilter);
		if (includeMagDistFilterParam.getValue())
			filters.add(magDistFilter);
		return filters;
	}


	@Override
	public void setTrackProgress(boolean trackProgress) {
		// always enabled
	}


	@Override
	public boolean isTrackProgress() {
		// always enabled
		return true;
	};


}



