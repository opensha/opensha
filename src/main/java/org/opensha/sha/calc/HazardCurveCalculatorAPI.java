package org.opensha.sha.calc;

import java.util.List;
import java.util.Map;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;


/**
 * <p>Title: HazardCurveCalculatorAPI</p>
 * <p>Description: Defines the interface for the HazardCurveCalculator.</p>
 * @author : Ned (Edward) Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public interface HazardCurveCalculatorAPI extends CalculatorAPI {

	/**
	 * Get the adjustable parameter list of calculation parameters
	 *
	 * @return the adjustable ParameterList
	 */
	public ParameterList getAdjustableParams();

	/**
	 * Get iterator for the adjustable parameters
	 *
	 * @return parameter iterator
	 */
	public ListIterator<Parameter<?>> getAdjustableParamsIterator();
	
	/**
	 * This gets a list of source filters (e.g., distance, magnitude, etc) used to speed up
	 * hazard calculations
	 * @return
	 */
	public List<SourceFilter> getSourceFilters();

	/**
	 * This sets the maximum distance of sources to be considered in the calculation.
	 * Sources more than this distance away are ignored.  This is simply a direct
	 * way of setting the parameter.
	 * Default value is 200 km.
	 *
	 * @param distance: the maximum distance in km
	 */
	public void setMaxSourceDistance(double distance);

	/**
	 * This sets the minimum magnitude considered in the calculation.  Values
	 * less than the specified amount will be ignored.
	 *
	 * @param magnitude: the minimum magnitude
	 */
	public void setMinMagnitude(double magnitude);


	/**
	 * This is a direct way of getting the distance cutoff from that parameter
	 * 
	 * @return max source distance
	 */
	public double getMaxSourceDistance();

	/**
	 * This sets the mag-dist filter function (distance on x-axis and 
	 * mag on y-axis), and also sets the value of includeMagDistFilterParam as true
	 * 
	 * @param magDistfunc function to set
	 */
	public void setMagDistCutoffFunc(ArbitrarilyDiscretizedFunc magDistfunc);

	/**
	 * Set the number of stochastic event set realizations for average event set hazard
	 * curve calculation. This simply sets the <code>NumStochasticEventSetsParam</code>
	 * parameter.
	 * 
	 * @param numRealizations number of stochastic event set realizations
	 */
	public void setNumStochEventSetRealizations(int numRealizations);

	/**
	 * Sets the <code>IncludeMagDistFilterParam</code> parameter, which determines if the
	 * magnitude/distance filter is used in calculation.
	 * 
	 * @param include if true, the magnitude/distance filter is included
	 */
	public void setIncludeMagDistCutoff(boolean include);

	/**
	 * This gets the mag-dist filter function (distance on x-axis and 
	 * mag on y-axis), returning null if the includeMagDistFilterParam
	 * has been set to false.
	 * 
	 * @return  mag-dist filter function
	 */
	public ArbitrarilyDiscretizedFunc getMagDistCutoffFunc();

	/**
	 * This was created so new instances of this calculator could be
	 * given pointers to a set of parameter that already exist.
	 * 
	 * @param paramList parameters to be set
	 */
	public void setAdjustableParams(ParameterList paramList);



	/**
	 * Returns the Annualized Rates for the Hazard Curves 
	 * 
	 * @param hazFunction Discretized Hazard Function
	 * @return annualized rates for the given hazard function
	 */
	public DiscretizedFunc getAnnualizedRates(DiscretizedFunc hazFunction,double years);

	/**
	 * This function computes a hazard curve for the given Site, IMR, ERF, and 
	 * discretized function, where the latter supplies the x-axis values (the IMLs) for the 
	 * computation, and the result (probability) is placed in the y-axis values of this function.
	 * This always applies a source and rupture distance cutoff using the value of the
	 * maxDistanceParam parameter (set to a very high value if you don't want this).  It also 
	 * applies a magnitude-dependent distance cutoff on the sources if the value of 
	 * includeMagDistFilterParam is "true" and using the function in magDistCutoffParam.
	 * @param hazFunction: This function is where the hazard curve is placed
	 * @param site: site object
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @return
	 */
	public DiscretizedFunc getHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, ERF eqkRupForecast);

	/**
	 * This function computes a hazard curve for the given Site, imrMap, ERF, and 
	 * discretized function, where the latter supplies the x-axis values (the IMLs) for the 
	 * computation, and the result (probability) is placed in the y-axis values of this function.
	 * 
	 * This always applies a source and rupture distance cutoff using the value of the
	 * maxDistanceParam parameter (set to a very high value if you don't want this).  It also 
	 * applies a magnitude-dependent distance cutoff on the sources if the value of 
	 * includeMagDistFilterParam is "true" and using the function in magDistCutoffParam.
	 * 
	 * The IMR will be selected on a source by source basis by the <code>imrMap</code> parameter.
	 * If the mapping only contains a single IMR, then that IMR will be used for all sources.
	 * Otherwise, if a mapping exists for the source's tectonic region type (TRT), then the IMR
	 * from that mapping will be used for that source. If no mapping exists, a NullPointerException
	 * will be thrown.
	 * 
	 * Once the IMR is selected, it's TRT paramter can be set by the soruce, depending
	 * on the <code>SetTRTinIMR_FromSourceParam</code> param and <code>NonSupportedTRT_OptionsParam</code>
	 * param. If <code>SetTRTinIMR_FromSourceParam</code> is true, then the IMR's TRT param will be set by
	 * the source (otherwise it will be left unchanged). If it is to be set, but the source's TRT is not
	 * supported by the IMR, then <code>NonSupportedTRT_OptionsParam</code> is used.
	 * 
	 * @param hazFunction: This function is where the hazard curve is placed
	 * @param site: site object
	 * @param imrMap this <code>Map<TectonicRegionType,ScalarIntensityMeasureRelationshipAPI></code>
	 * specifies which IMR to use with each tectonic region.
	 * @param eqkRupForecast selected Earthquake rup forecast
	 * @return hazard curve. Function passed in is updated in place, so this is just a pointer to
	 * the <code>hazFunction</code> param.
	 * @throws NullPointerException if there are multiple IMRs in the mapping, but no mapping exists for
	 * a soruce in the ERF.
	 */
	public DiscretizedFunc getHazardCurve(
			DiscretizedFunc hazFunction,
			Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap, 
			ERF eqkRupForecast);


	/**
	 * This computes the "deterministic" exceedance curve for the given Site, IMR, and ProbEqkrupture
	 * (conditioned on the event actually occurring).  The hazFunction passed in provides the x-axis
	 * values (the IMLs) and the result (probability) is placed in the y-axis values of this function.
	 * @param hazFunction This function is where the deterministic hazard curve is placed
	 * @param site site object
	 * @param imr selected IMR object
	 * @param rupture Single Earthquake Rupture
	 * @return hazard curve. Function passed in is updated in place, so this is just a pointer to
	 * the <code>hazFunction</code> param.
	 */
	public DiscretizedFunc getHazardCurve(DiscretizedFunc
			hazFunction,
			Site site, ScalarIMR imr, EqkRupture rupture);


	/**
	 * Enables progress tracking
	 * 
	 * @see {@link #getCurrRuptures()}
	 * @see {@link #getTotRuptures()}
	 * @param trackProgress
	 */
	public void setTrackProgress(boolean trackProgress);
	
	/**
	 * Returns the status of progress tracking
	 * 
	 * @see {@link #setTrackProgress(boolean)}
	 * @return true if progress tracking is enabled
	 */
	public boolean isTrackProgress();

	/**
	 * gets the current rupture that is being processed, or -1 if progress tracking disabled
	 * 
	 * @see {@link #setTrackProgress(boolean)}
	 * @see {@link #isTrackProgress()}
	 * @return current rupture that is being processed
	 */
	public int getCurrRuptures();

	/**
	 * gets the total number of ruptures, or -1 if progress tracking disabled
	 * 
	 * @see {@link #setTrackProgress(boolean)}
	 * @see {@link #isTrackProgress()}
	 * @return total number of ruptures.
	 */
	public int getTotRuptures();

	/**
	 * This function computes an average hazard curve from a number of stochastic event sets
	 * for the given Site, IMR, eqkRupForecast, where the number of event-set realizations
	 * is specified as the value in numStochEventSetRealizationsParam. The passed in 
	 * discretized function supplies the x-axis values (the IMLs) 
	 * for the computation, and the result (probability) is placed in the 
	 * y-axis values of this function. This always applies a rupture distance 
	 * cutoff using the value of the maxDistanceParam parameter (set to a very high 
	 * value if you don't want this).  This does not (yet?) apply the magnitude-dependent 
	 * distance cutoff represented by includeMagDistFilterParam and magDistCutoffParam.
	 * 
	 * @param hazFunction This function is where the hazard curve is placed
	 * @param site site object
	 * @param imr selected IMR object
	 * @param eqkRupForecast selected Earthquake rup forecast
	 * @return hazard curve. Function passed in is updated in place, so this is just a pointer to
	 * the <code>hazFunction</code> param.
	 */
	public DiscretizedFunc getAverageEventSetHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			ERF eqkRupForecast);

	/**
	 * This function computes a hazard curve for the given Site, IMR, and event set
	 * (eqkRupList), where it is assumed that each of the events occur (probability 
	 * of each is 1.0). The passed in discretized function supplies the x-axis values 
	 * (the IMLs) for the computation, and the result (probability) is placed in the 
	 * y-axis values of this function. This always applies a rupture distance 
	 * cutoff using the value of the maxDistanceParam parameter (set to a very high 
	 * value if you don't want this).  This does not (yet?) apply the magnitude-dependent 
	 * distance cutoff represented by includeMagDistFilterParam and magDistCutoffParam.
	 * 
	 * @param hazFunction This function is where the hazard curve is placed
	 * @param site site object
	 * @param imr selected IMR object
	 * @param eqkRupForecast selected Earthquake rup forecast
	 * @param updateCurrRuptures tells whether to update current ruptures (for the getCurrRuptures() method used for progress bars)
	 * @return hazard curve. Function passed in is updated in place, so this is just a pointer to
	 * the <code>hazFunction</code> param.
	 */
	public DiscretizedFunc getEventSetHazardCurve(DiscretizedFunc hazFunction,
			Site site, ScalarIMR imr, 
			List<EqkRupture> eqkRupList, boolean updateCurrRuptures);

}
