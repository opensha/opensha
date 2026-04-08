package org.opensha.sha.calc;

import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.ScalarIMR;

public interface SpectrumCalculatorAPI extends CalculatorAPI {

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
	public DiscretizedFunc getDeterministicSpectrumCurve(Site site,
			ScalarIMR imr, EqkRupture rupture, boolean probAtIML,
			double imlProbVal);

	/**
	 * This function computes a spectrum curve for all SA Period supported
	 * by the IMR and then interpolates the IML value from all the computed curves.
	 * 
	 * @param hazardXValues Hazard curve x values, already in log units
	 * @param site site
	 * @param imr selected IMR
	 * @param eqkRupForecast selected ERF
	 * @param probVal probability at which to compute spectrum
	 * @return hazard spectrum of log IMLs for the given probability
	 */
	public DiscretizedFunc getIML_SpectrumCurve(DiscretizedFunc
			hazardXValues, Site site,
			ScalarIMR imr,
			ERF
			eqkRupForecast, double probVal);

	/**
	 * This function computes a spectrum curve for the given Site, IMR, and ERF.
	 * @param site site
	 * @param imr selected IMR
	 * @param eqkRupForecast selected ERF
	 * @param imlVal IML in log units
	 * @return probability spectrum at the given IML
	 */
	public DiscretizedFunc getSpectrumCurve(Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			double imlVal);

	/**
	 * gets the current calculation progress that is being processed, or -1 if the calculation has not started
	 * 
	 * This will either be the current rupture or source index (depending on calculation type), and is relative to
	 * the value of {@link #getTotalProgressCount()}.
	 * 
	 * @see {@link #setTrackProgress(boolean)}
	 * @see {@link #getTotalProgressCount()}
	 * @return current rupture that is being processed
	 */
	public int getCurrentProgress();

	/**
	 * gets the total number of calculation steps
	 * 
	 * @see {@link #setTrackProgress(boolean)}
	 * @return total number of ruptures.
	 */
	public int getTotalProgressCount();
	
	// TODO: methods to get and set source filters if we want programatic access

	/**
	 *
	 * @return This was created so new instances of this calculator could be
	 * given pointers to a set of parameter that already exist.
	 */
	public void setAdjustableParams(ParameterList paramList);


	/**
	 *
	 * @return the adjustable ParameterList
	 */
	public ParameterList getAdjustableParams();

	/**
	 * get the adjustable parameters
	 *
	 * @return
	 */
	public ListIterator getAdjustableParamsIterator();


}
