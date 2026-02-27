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
	 *
	 * @return the current rupture being traversed
	 */
	public int getCurrRuptures();

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
	 * The curve in place in the passed in hazFunction
	 * (with the X-axis values being the IMLs for which exceedance probabilites are desired).
	 * @param specFunction: This function is where the final interplotaed spectrum
	 * for the IML@prob curve is placed.
	 * @param site: site object
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @return
	 */
	public DiscretizedFunc getIML_SpectrumCurve(DiscretizedFunc
			spectrumFunction, Site site,
			ScalarIMR imr,
			ERF
			eqkRupForecast, double probVal, List supportedSA_Periods);

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
			List supportedSA_Periods);

	/**
	 *
	 * @return the total number of ruptures in the earthquake rupture forecast model
	 */
	public int getTotRuptures();
	
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
