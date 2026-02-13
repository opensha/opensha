package org.opensha.sha.calc;

import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilterUtils;
import org.opensha.sha.calc.sourceFilters.params.SourceFiltersParam;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * <p>Title: SpectrumCalculator</p>
 *
 * <p>Description: This class computes the Spectral Values for given IML or Prob.
 * Value </p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */
public class SpectrumCalculator extends AbstractCalculator
implements SpectrumCalculatorAPI {


	private final static String C = "SpectrumCalculator";
	private final static boolean D = false;

	//Info for parameter that sets the maximum distance considered
	private SourceFilterManager sourceFilters;
	private SourceFiltersParam sourceFilterParam;

	private ParameterList adjustableParams;


	private int currRuptures = -1;
	private int totRuptures = 0;

	//index to keep track how many sources have been traversed
	private int sourceIndex;
	// get total number of sources
	private int numSources;


	/**
	 * SpectrumCalculator
	 */
	public SpectrumCalculator() {
		this(SourceFiltersParam.getDefault());
	}
	
	/**
	 * SpectrumCalculator
	 */
	public SpectrumCalculator(SourceFilterManager sourceFilters) {
		this.sourceFilters = sourceFilters;
		sourceFilterParam = new SourceFiltersParam(sourceFilters);
		
		// Create adjustable parameters and add to list
		adjustableParams = new ParameterList();
		adjustableParams.addParameter(sourceFilterParam);
	}

	/**
	 *
	 * @return the adjustable ParameterList
	 */
	public ParameterList getAdjustableParams() {
		return this.adjustableParams;
	}


	/**
	 *
	 * @return This was created so new instances of this calculator could be
	 * given pointers to a set of parameter that already exist.
	 */
	public void setAdjustableParams(ParameterList paramList) {
		this.adjustableParams = paramList;
		this.sourceFilterParam = (SourceFiltersParam)paramList.getParameter(SourceFiltersParam.NAME);
		this.sourceFilters = sourceFilterParam.getValue();
	}


	/**
	 * get the adjustable parameters
	 *
	 * @return
	 */
	public ListIterator getAdjustableParamsIterator() {
		return adjustableParams.getParametersIterator();
	}

	/**
	 * This function computes a spectrum curve for all SA Period supported
	 * by the IMR and then interpolates the IML value from all the computed curves.
	 * The curve in place in the passed in hazFunction
	 * (with the X-axis values being the IMLs for which exceedance probabilites are desired).
	 * @param specFunction: This function is where the final interplotaed spectrum
	 * for the IML@prob curve is placed.
	 * @param site: site object
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @return
	 */
	public DiscretizedFunc getIML_SpectrumCurve(DiscretizedFunc spectrumFunction,
			Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double probVal,
			List supportedSA_Periods) {
		signalReset();

		this.currRuptures = -1;

		/* this determines how the calucations are done (doing it the way it's outlined
     in the paper SRL gives probs greater than 1 if the total rate of events for the
     source exceeds 1.0, even if the rates of individual ruptures are << 1).
		 */
		boolean poissonSource = false;
		//creates a new Arb function with X value being in Log scale and Y values as 1.0
		DiscretizedFunc tempSpecFunc =initDiscretizedValuesToLog(spectrumFunction,1.0);

		int numSAPeriods = supportedSA_Periods.size();
		DiscretizedFunc[] hazFunction = new ArbitrarilyDiscretizedFunc[numSAPeriods];
		DiscretizedFunc[] sourceHazFunc = new ArbitrarilyDiscretizedFunc[numSAPeriods];

		for(int i=0;i<numSAPeriods;++i){
			hazFunction[i] = (DiscretizedFunc)tempSpecFunc.deepClone();
			sourceHazFunc[i] = (DiscretizedFunc)tempSpecFunc.deepClone();
		}
		ArbitrarilyDiscretizedFunc condProbFunc = (ArbitrarilyDiscretizedFunc)
		tempSpecFunc.deepClone();


		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		( (AttenuationRelationship) imr).resetParameterEventListeners();

		//System.out.println("hazFunction: "+hazFunction.toString());

		// declare some varibles used in the calculation
		double qkProb, distance;
		int k;

		// get the number of points
		int numPoints = tempSpecFunc.size();

		double maxDistance = sourceFilters.getMaxDistance();
		List<SourceFilter> filters = sourceFilters.getEnabledFilters();

		// set the maximum distance in the attenuation relationship
		// (Note- other types of IMRs may not have this method so we should really check type here)
		imr.setUserMaxDistance(maxDistance);

		// get total number of sources
		numSources = eqkRupForecast.getNumSources();
		//System.out.println("Number of Sources: "+numSources);
		//System.out.println("ERF info: "+ eqkRupForecast.getClass().getName());
		// compute the total number of ruptures for updating the progress bar
		totRuptures = 0;
		sourceIndex = 0;
		for (sourceIndex = 0; sourceIndex < numSources; ++sourceIndex)
			totRuptures += eqkRupForecast.getSource(sourceIndex).getNumRuptures();


		//System.out.println("Total number of ruptures:"+ totRuptures);


		// init the current rupture number (also for progress bar)
		currRuptures = 0;


		// set the Site in IMR
		imr.setSite(site);

		// this boolean will tell us whether a source was actually used
		// (e.g., all could be outside MAX_DISTANCE)
		boolean sourceUsed = false;

		if (D)
			System.out.println(C + ": starting hazard curve calculation");

		// loop over sources
		for (sourceIndex = 0; sourceIndex < numSources; sourceIndex++) {
			if (isCancelled()) return null;
			
			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);

			// compute the source's distance from the site and skip if it's too far away
			distance = source.getMinDistance(site);
			// apply any filters
			if (SourceFilterUtils.canSkipSource(filters, source, site)) {
				currRuptures += source.getNumRuptures();  //update progress bar for skipped ruptures
				continue;
			}

			// indicate that a source has been used
			sourceUsed = true;

			// determine whether it's poissonian
			poissonSource = source.isSourcePoissonian();

			// initialize the source hazard function to 0.0 if it's a non-poisson source
			if (!poissonSource)
				for(int m=0;m<numSAPeriods;++m)
					initDiscretizeValues(sourceHazFunc[m], 0.0);

			// get the number of ruptures for the current source
			int numRuptures = source.getNumRuptures();

			// loop over these ruptures
			for (int n = 0; n < numRuptures; n++, ++currRuptures) {

				EqkRupture rupture = source.getRupture(n);

				// get the rupture probability
				qkProb = ( (ProbEqkRupture) rupture).getProbability();
				
				if (qkProb == 0d)
					continue;
				
				// apply any filters
				if (SourceFilterUtils.canSkipRupture(filters, rupture, site)) {
					continue;
				}

				// set the EqkRup in the IMR
				imr.setEqkRupture(rupture);

				//looping over all the SA Periods to get the ExceedProb Val for each.
				for (int saPeriodIndex = 0; saPeriodIndex < numSAPeriods; ++saPeriodIndex) {
					imr.getParameter(PeriodParam.NAME).setValue(
							supportedSA_Periods.get(saPeriodIndex));

					// get the conditional probability of exceedance from the IMR
					condProbFunc = (ArbitrarilyDiscretizedFunc) imr.getExceedProbabilities(
							condProbFunc);
					//System.out.println("CurrentRupture: "+currRuptures);
					// For poisson source
					if (poissonSource) {
						/* First make sure the probability isn't 1.0 (or too close); otherwise rates are
             infinite and all IMLs will be exceeded (because of ergodic assumption).  This
             can happen if the number of expected events (over the timespan) exceeds ~37,
             because at this point 1.0-Math.exp(-num) = 1.0 by numerical precision (and thus,
             an infinite number of events).  The number 30 used in the check below provides a
             safe margin */
						if (Math.log(1.0 - qkProb) < -30.0)
							throw new RuntimeException(
									"Error: The probability for this ProbEqkRupture (" + qkProb +
							") is too high for a Possion source (~infinite number of events)");

						for (k = 0; k < numPoints; k++)
							hazFunction[saPeriodIndex].set(k,
									hazFunction[saPeriodIndex].getY(k) *
									Math.pow(1 - qkProb, condProbFunc.getY(k)));
					}
					// For non-Poissin source
					else
						for (k = 0; k < numPoints; k++)
							sourceHazFunc[saPeriodIndex].set(k,
									sourceHazFunc[saPeriodIndex].getY(k) +
									qkProb * condProbFunc.getY(k));
				}
			}
			// for non-poisson source:
			if (!poissonSource)
				for(int i=0;i<numSAPeriods;++i)
					for (k = 0; k < numPoints; k++)
						hazFunction[i].set(k, hazFunction[i].getY(k) * (1 - sourceHazFunc[i].getY(k)));
		}

		int i;
		// finalize the hazard function
		if (sourceUsed)
			for(int j=0;j<numSAPeriods;++j)
				for (i = 0; i < numPoints; ++i)
					hazFunction[j].set(i, 1 - hazFunction[j].getY(i));
		else
			for(int j=0;j<numSAPeriods;++j)
				for (i = 0; i < numPoints; ++i)
					hazFunction[j].set(i, 0.0);

		//creating the temp functionlist that gets the linear X Value for each SA-Period
		//spectrum curve.
		DiscretizedFunc[] tempHazFunction = new ArbitrarilyDiscretizedFunc[numSAPeriods];
		for(int j=0;j<numSAPeriods;++j){
			tempHazFunction[j] = new ArbitrarilyDiscretizedFunc();
			for (i = 0; i < numPoints; ++i) {
				tempHazFunction[j].set(spectrumFunction.getX(i),hazFunction[j].getY(i));
			}
		}
		//creating the Spectrum function by interpolating in Log space the IML vals
		//for the given prob. value. It is done for each SA period function.
		DiscretizedFunc imlSpectrumFunction = new ArbitrarilyDiscretizedFunc();
		for(int j=0;j<numSAPeriods;++j){
			double val = tempHazFunction[j].getFirstInterpolatedX_inLogXLogYDomain(probVal);
			imlSpectrumFunction.set(((Double)supportedSA_Periods.get(j)).doubleValue(), val);
		}

		if (D)
			System.out.println(C + "hazFunction.toString" + hazFunction.toString());
		return imlSpectrumFunction;
	}


	/**
	 * Initialize the prob as 1 for the Hazard function
	 *
	 * @param arb
	 */
	private void initDiscretizeValues(DiscretizedFunc arb, double val){
		int num = arb.size();
		for(int i=0;i<num;++i)
			arb.set(i,val);
	}


	/**
	 * Converts a Linear Arb. function to a function with X values being the Log scale.
	 * It does not modify the original function, an returns  a new function.
	 * @param linearFunc DiscretizedFuncAPI Linear Arb function
	 * @param val double values to initialize the Y value of the Arb function with.
	 * @return DiscretizedFuncAPI Arb function with X values being the log scale.
	 */
	private DiscretizedFunc initDiscretizedValuesToLog(DiscretizedFunc linearFunc,double val){
		DiscretizedFunc toXLogFunc = new ArbitrarilyDiscretizedFunc();
		if (IMT_Info.isIMT_LogNormalDist(SA_Param.NAME))
			for (int i = 0; i < linearFunc.size(); ++i)
				toXLogFunc.set(Math.log(linearFunc.getX(i)), val);
		return toXLogFunc;
	}



	/**
	 * This function computes a spectrum curve for the given Site, IMR, and ERF.  The curve
	 * in place in the passed in hazFunction (with the X-axis values being the SA
	 * Periods for which exceedance probabilites are desired).
	 * @param hazFunction: This function is where the hazard curve is placed
	 * @param site: site object
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @return
	 */
	public DiscretizedFunc getSpectrumCurve(Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double imlVal,
			List supportedSA_Periods) {
		signalReset();

		//creating the Master function that initializes the Function with supported SA Periods Vals
		DiscretizedFunc hazFunction = new ArbitrarilyDiscretizedFunc();
		initDiscretizeValues(hazFunction, supportedSA_Periods, 1.0);
		int numPoints = hazFunction.size();

		this.currRuptures = -1;

		/* this determines how the calucations are done (doing it the way it's outlined
     in the paper SRL gives probs greater than 1 if the total rate of events for the
     source exceeds 1.0, even if the rates of individual ruptures are << 1).
		 */
		boolean poissonSource = false;

		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		((AttenuationRelationship)imr).resetParameterEventListeners();

		//System.out.println("hazFunction: "+hazFunction.toString());

		// declare some varibles used in the calculation
		double qkProb, distance;
		int k;

		double maxDistance = sourceFilters.getMaxDistance();
		List<SourceFilter> filters = sourceFilters.getEnabledFilters();

		// set the maximum distance in the attenuation relationship
		// (Note- other types of IMRs may not have this method so we should really check type here)
		imr.setUserMaxDistance(maxDistance);



		//Source func
		DiscretizedFunc sourceHazFunc = new ArbitrarilyDiscretizedFunc();
		initDiscretizeValues(sourceHazFunc,supportedSA_Periods,0.0);

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


		// set the Site in IMR
		imr.setSite(site);

		// this boolean will tell us whether a source was actually used
		// (e.g., all could be outside MAX_DISTANCE)
		boolean sourceUsed = false;

		if (D) System.out.println(C+": starting hazard curve calculation");

		// loop over sources
		for(sourceIndex=0;sourceIndex < numSources ;sourceIndex++) {
			// quit if user cancelled the calculation
			if (isCancelled()) return null;

			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);

			// compute the source's distance from the site and skip if it's too far away
			distance = source.getMinDistance(site);
			// apply any filters
			if (SourceFilterUtils.canSkipSource(filters, source, site)) {
				currRuptures += source.getNumRuptures();  //update progress bar for skipped ruptures
				continue;
			}
			
			// indicate that a source has been used
			sourceUsed = true;

			// determine whether it's poissonian
			poissonSource = source.isSourcePoissonian();

			// get the number of ruptures for the current source
			int numRuptures = source.getNumRuptures();

			// loop over these ruptures
			for(int n=0; n < numRuptures ; n++,++currRuptures) {

				EqkRupture rupture = source.getRupture(n);

				// get the rupture probability
				qkProb = ((ProbEqkRupture)rupture).getProbability();
				
				if (qkProb == 0d)
					continue;
				
				// apply any filters
				if (SourceFilterUtils.canSkipRupture(filters, rupture, site)) {
					continue;
				}

				// set the EqkRup in the IMR
				imr.setEqkRupture(rupture);

				DiscretizedFunc condProbFunc = null;


				// get the conditional probability of exceedance from the IMR
				condProbFunc = (DiscretizedFunc) imr.getSA_ExceedProbSpectrum(Math.log(
						imlVal));
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
			for(i=0;i<numPoints;++i)
				hazFunction.set(i,0.0);
		if (D) System.out.println(C+"hazFunction.toString"+hazFunction.toString());
		return hazFunction;
	}

	/**
	 *
	 * @return the current rupture being traversed
	 */
	public int getCurrRuptures() {
		return this.currRuptures;
	}

	/**
	 *
	 * @return the total number of ruptures in the earthquake rupture forecast model
	 */
	public int getTotRuptures() {
		return this.totRuptures;
	}



	/**
	 * Initialize the prob as 1 for the Hazard function
	 *
	 * @param arb
	 */
	private void initDiscretizeValues(DiscretizedFunc arb, List supportedSA_Periods,
			double val){
		int num = supportedSA_Periods.size();
		for(int i=0;i<num;++i)
			arb.set(((Double)supportedSA_Periods.get(i)).doubleValue(),val);
	}


	/**
	 * This function computes a deterministic exceedance curve for the given Site, IMR, and ProbEqkrupture.  The curve
	 * in place in the passed in hazFunction (with the X-axis values being the IMLs for which
	 * exceedance probabilites are desired).
	 * @param hazFunction: This function is where the hazard curve is placed
	 * @param site: site object
	 * @param imr: selected IMR object
	 * @param rupture: Single Earthquake Rupture
	 * @return
	 */
	public DiscretizedFunc getDeterministicSpectrumCurve(
			Site site, ScalarIMR imr, EqkRupture rupture,
			boolean probAtIML, double imlProbVal) {


		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		( (AttenuationRelationship) imr).resetParameterEventListeners();


		//System.out.println("hazFunction: "+hazFunction.toString());

		// set the Site in IMR
		imr.setSite(site);

		if (D) System.out.println(C + ": starting hazard curve calculation");

		// set the EqkRup in the IMR
		imr.setEqkRupture(rupture);

		DiscretizedFunc hazFunction = null;
		if(probAtIML)
			// get the conditional probability of exceedance from the IMR
			hazFunction = (DiscretizedFunc) imr.getSA_ExceedProbSpectrum(Math.log(imlProbVal));
		else{
			hazFunction = (DiscretizedFunc) imr.getSA_IML_AtExceedProbSpectrum(
					imlProbVal);
			int numPoints = hazFunction.size();
			for(int i=0;i<numPoints;++i){
				hazFunction.set(i,Math.exp(hazFunction.getY(i)));
			}
		}
		if (D) System.out.println(C + "hazFunction.toString" + hazFunction.toString());
		return hazFunction;
	}

	@Override
	protected boolean isCancelled() {
		boolean cancelled = super.isCancelled();
		if (D && cancelled) System.out.println("Signal caught in " + C);
		return cancelled;
	}
}
