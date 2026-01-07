package org.opensha.sha.calc;

import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.params.MaxDistanceParam;
import org.opensha.sha.calc.params.PointSourceOptimizationsParam;
import org.opensha.sha.calc.params.filters.SourceFilter;
import org.opensha.sha.calc.params.filters.SourceFilterManager;
import org.opensha.sha.calc.params.filters.SourceFilters;
import org.opensha.sha.calc.params.filters.SourceFiltersParam;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.SiteAdaptiveSource;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.primitives.Doubles;

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
	//enables point source optimizations
	private PointSourceOptimizationsParam pointSourceOptimizations;

	private ParameterList adjustableParams;

	// progress tracking
	private int currProgress = -1;
	private int totProgress = 0;


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
		pointSourceOptimizations = new PointSourceOptimizationsParam();
		
		// Create adjustable parameters and add to list
		adjustableParams = new ParameterList();
		adjustableParams.addParameter(sourceFilterParam);
		adjustableParams.addParameter(pointSourceOptimizations);
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


	public DiscretizedFunc getIML_SpectrumCurve(DiscretizedFunc hazardXValues,
			Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double probVal) {
		RuptureSpectraCalculator rupSpectraCalc;
		if (pointSourceOptimizations.getValue())
			rupSpectraCalc = new PointSourceOptimizedSpectraCalc();
		else
			rupSpectraCalc = RuptureSpectraCalculator.BASIC_IMPLEMENTATION;
		return getIML_SpectrumCurve(hazardXValues, site, imr, eqkRupForecast, probVal, rupSpectraCalc);
	}

	// TODO add to API?
	public DiscretizedFunc getIML_SpectrumCurve(DiscretizedFunc hazardXValues,
			Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double probVal,
			RuptureSpectraCalculator rupSpectraCalc) {
		signalReset();

		this.currProgress = -1;

		/* 
		 * this determines how the calucations are done (doing it the way it's outlined
     	 * in the paper SRL gives probs greater than 1 if the total rate of events for the
     	 * source exceeds 1.0, even if the rates of individual ruptures are << 1).
		 */
		boolean poissonSource = false;
		//creates a new Arb function with X value being in Log scale and Y values as 1.0
		// get the number of hazard x values points
		final int numXVals = hazardXValues.size();
		double[] xValues = new double[numXVals];
		for (int i=0; i<numXVals; i++)
			xValues[i] = hazardXValues.getX(i);

		imr.setIntensityMeasure(SA_Param.NAME);
		PeriodParam periodParam = (PeriodParam)imr.getParameter(PeriodParam.NAME);
		List<Double> supportedSA_Periods = periodParam.getAllowedDoubles();
		double[] periodsArray = Doubles.toArray(supportedSA_Periods);
		int numSAPeriods = supportedSA_Periods.size();
		
		LightFixedXFunc[] hazFunctions = new LightFixedXFunc[numSAPeriods];
		LightFixedXFunc[] sourceHazFuncs = new LightFixedXFunc[numSAPeriods];
		LightFixedXFunc[] condProbFuncs = new LightFixedXFunc[numSAPeriods];

		for(int i=0;i<numSAPeriods;++i){
			hazFunctions[i] = new LightFixedXFunc(xValues, new double[xValues.length]);
			initDiscretizeValues(hazFunctions[i], 1.0);
			sourceHazFuncs[i] = new LightFixedXFunc(xValues, new double[xValues.length]);
			condProbFuncs[i] = new LightFixedXFunc(xValues, new double[xValues.length]);
		}


		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
		( (AttenuationRelationship) imr).resetParameterEventListeners();

		//System.out.println("hazFunction: "+hazFunction.toString());

		// declare some varibles used in the calculation
		double qkProb, distance;
		int k;

		double maxDistance = sourceFilters.getMaxDistance();
		List<SourceFilter> filters = sourceFilters.getEnabledFilters();

		// set the maximum distance in the attenuation relationship
		// (Note- other types of IMRs may not have this method so we should really check type here)
		imr.setUserMaxDistance(maxDistance);

		// get total number of sources
		int numSources = eqkRupForecast.getNumSources();
		//System.out.println("Number of Sources: "+numSources);
		//System.out.println("ERF info: "+ eqkRupForecast.getClass().getName());
		
		currProgress = 0;
		totProgress = numSources;

		// set the Site in IMR
		imr.setSite(site);

		// this boolean will tell us whether a source was actually used
		// (e.g., all could be outside MAX_DISTANCE)
		boolean sourceUsed = false;

		if (D)
			System.out.println(C + ": starting hazard curve calculation");

		// loop over sources
		for (int sourceIndex = 0; sourceIndex < numSources; sourceIndex++) {
			if (isCancelled()) return null;
			
			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);

			// compute the source's distance from the site and skip if it's too far away
			distance = source.getMinDistance(site);
			// apply any filters
			if (HazardCurveCalculator.canSkipSource(filters, source, site)) {
				currProgress++;
				continue;
			}
			
			if (source instanceof SiteAdaptiveSource)
				source = ((SiteAdaptiveSource)source).getForSite(site);

			// indicate that a source has been used
			sourceUsed = true;

			// determine whether it's poissonian
			poissonSource = source.isSourcePoissonian();

			// initialize the source hazard function to 0.0 if it's a non-poisson source
			if (!poissonSource)
				for(int m=0;m<numSAPeriods;++m)
					initDiscretizeValues(sourceHazFuncs[m], 0.0);

			// get the number of ruptures for the current source
			int numRuptures = source.getNumRuptures();

			// loop over these ruptures
			for (int n = 0; n < numRuptures; n++) {
				EqkRupture rupture = source.getRupture(n);

				// get the rupture probability
				qkProb = ( (ProbEqkRupture) rupture).getProbability();
				
				if (qkProb == 0d)
					continue;
				
				// apply any filters
				if (HazardCurveCalculator.canSkipRupture(filters, rupture, site)) {
					continue;
				}
				
				// get the conditional probability of exceedance from the IMR for all periods
				rupSpectraCalc.getMultiPeriodExceedProbabilities(imr, periodParam, supportedSA_Periods, rupture, condProbFuncs);

				//looping over all the SA Periods to get the ExceedProb Val for each.
				for (int saPeriodIndex = 0; saPeriodIndex < numSAPeriods; ++saPeriodIndex) {
					
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

						for (k = 0; k < numXVals; k++)
							hazFunctions[saPeriodIndex].set(k,
									hazFunctions[saPeriodIndex].getY(k) *
									Math.pow(1 - qkProb, condProbFuncs[saPeriodIndex].getY(k)));
					}
					// For non-Poissin source
					else
						for (k = 0; k < numXVals; k++)
							sourceHazFuncs[saPeriodIndex].set(k,
									sourceHazFuncs[saPeriodIndex].getY(k) +
									qkProb * condProbFuncs[saPeriodIndex].getY(k));
				}
			}
			// for non-poisson source:
			if (!poissonSource)
				for(int i=0;i<numSAPeriods;++i)
					for (k = 0; k < numXVals; k++)
						hazFunctions[i].set(k, hazFunctions[i].getY(k) * (1 - sourceHazFuncs[i].getY(k)));
			currProgress++;
		}

		int i;
		// finalize the hazard function
		if (sourceUsed)
			for(int j=0;j<numSAPeriods;++j)
				for (i = 0; i < numXVals; ++i)
					hazFunctions[j].set(i, 1 - hazFunctions[j].getY(i));
		else
			for(int j=0;j<numSAPeriods;++j)
				for (i = 0; i < numXVals; ++i)
					hazFunctions[j].set(i, 0.0);

		
		//creating the Spectrum function by interpolating in Log space the IML vals
		//for the given prob. value. It is done for each SA period function.
		LightFixedXFunc imlSpectrumFunction = new LightFixedXFunc(periodsArray, new double[numSAPeriods]);
		for(int p=0;p<numSAPeriods;++p){
			double val = hazFunctions[p].getFirstInterpolatedX_inLogYDomain(probVal);
			imlSpectrumFunction.set(p, val);
		}

		if (D)
			System.out.println(C + "hazFunction.toString" + imlSpectrumFunction.toString());
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
	
	public DiscretizedFunc getSpectrumCurve(Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double imlVal) { // this is already in log units
		RuptureSpectraCalculator rupSpectraCalc;
		if (pointSourceOptimizations.getValue())
			rupSpectraCalc = new PointSourceOptimizedSpectraCalc();
		else
			rupSpectraCalc = RuptureSpectraCalculator.BASIC_IMPLEMENTATION;
		return getSpectrumCurve(site, imr, eqkRupForecast, imlVal, rupSpectraCalc);
	}
	
	public DiscretizedFunc getSpectrumCurve(Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double imlVal, // this is already in log units
			RuptureSpectraCalculator rupSpectraCalc) {
		signalReset();
		
		imr.setIntensityMeasure(SA_Param.NAME);
		PeriodParam periodParam = (PeriodParam)imr.getParameter(PeriodParam.NAME);
		List<Double> supportedSA_Periods = periodParam.getAllowedDoubles();
		double[] periodsArray = Doubles.toArray(supportedSA_Periods);
		int numPeriods = supportedSA_Periods.size();
		LightFixedXFunc spectrum = new LightFixedXFunc(periodsArray, new double[numPeriods]);
		initDiscretizeValues(spectrum, 1d);

		this.currProgress = -1;

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
		double qkProb;
		int k;

		double maxDistance = sourceFilters.getMaxDistance();
		List<SourceFilter> filters = sourceFilters.getEnabledFilters();

		// set the maximum distance in the attenuation relationship
		// (Note- other types of IMRs may not have this method so we should really check type here)
		imr.setUserMaxDistance(maxDistance);
		
		//Source func
		LightFixedXFunc sourceHazFunc = new LightFixedXFunc(periodsArray, new double[numPeriods]);

		// get total number of sources
		int numSources = eqkRupForecast.getNumSources();
		//System.out.println("Number of Sources: "+numSources);
		//System.out.println("ERF info: "+ eqkRupForecast.getClass().getName());
		
		currProgress = 0;
		totProgress = numSources;


		// set the Site in IMR
		imr.setSite(site);

		// this boolean will tell us whether a source was actually used
		// (e.g., all could be outside MAX_DISTANCE)
		boolean sourceUsed = false;

		if (D) System.out.println(C+": starting hazard curve calculation");

		// loop over sources
		for(int sourceIndex=0; sourceIndex < numSources; sourceIndex++) {
			// quit if user cancelled the calculation
			if (isCancelled()) return null;

			// get the ith source
			ProbEqkSource source = eqkRupForecast.getSource(sourceIndex);
			
			// apply any filters
			if (HazardCurveCalculator.canSkipSource(filters, source, site)) {
				currProgress++;
				continue;
			}
			
			if (source instanceof SiteAdaptiveSource)
				source = ((SiteAdaptiveSource)source).getForSite(site);
			
			// indicate that a source has been used
			sourceUsed = true;

			// determine whether it's poissonian
			poissonSource = source.isSourcePoissonian();

			// get the number of ruptures for the current source
			int numRuptures = source.getNumRuptures();

			// loop over these ruptures
			for(int n=0; n < numRuptures ; n++) {

				EqkRupture rupture = source.getRupture(n);

				// get the rupture probability
				qkProb = ((ProbEqkRupture)rupture).getProbability();
				
				if (qkProb == 0d)
					continue;
				
				// apply any filters
				if (HazardCurveCalculator.canSkipRupture(filters, rupture, site)) {
					continue;
				}

				DiscretizedFunc condProbFunc = rupSpectraCalc.getSA_ExceedProbSpectrum(imr, rupture, imlVal);
				
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

					for(k=0;k<numPeriods;k++)
						spectrum.set(k,spectrum.getY(k)*Math.pow(1-qkProb,condProbFunc.getY(k)));
				}
				// For non-Poissin source
				else
					for(k=0;k<numPeriods;k++)
						sourceHazFunc.set(k,sourceHazFunc.getY(k) + qkProb*condProbFunc.getY(k));
			}
			// for non-poisson source:
			if(!poissonSource)
				for(k=0;k<numPeriods;k++)
					spectrum.set(k,spectrum.getY(k)*(1-sourceHazFunc.getY(k)));
			currProgress++;
		}

		int i;
		// finalize the hazard function
		if(sourceUsed)
			for(i=0;i<numPeriods;++i)
				spectrum.set(i,1-spectrum.getY(i));
		else
			for(i=0;i<numPeriods;++i)
				spectrum.set(i,0.0);
		if (D) System.out.println(C+"hazFunction.toString"+spectrum.toString());
		return spectrum;
	}

	/**
	 *
	 * @return the current rupture being traversed
	 */
	public int getCurrentProgress() {
		return this.currProgress;
	}

	/**
	 *
	 * @return the total number of ruptures in the earthquake rupture forecast model
	 */
	public int getTotalProgressCount() {
		return this.totProgress;
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
			hazFunction = (DiscretizedFunc) imr.getSA_ExceedProbSpectrum(imlProbVal);
		else{
			hazFunction = (DiscretizedFunc) imr.getSA_IML_AtExceedProbSpectrum(
					imlProbVal);
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
