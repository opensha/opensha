package org.opensha.sha.imr.mod;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;

/**
 * Base class for Attenuation Relationship Modifiers.
 * 
 * @author kevin
 *
 */
public abstract class AbstractAttenRelMod implements ShortNamed {
	
	/**
	 * Sets any parameters needed in the IMR. Will be called whenever a new module is loaded,
	 * or the IMR is changed.
	 * 
	 * @param imr
	 */
	public abstract void setIMRParams(ScalarIMR imr);
	
	/**
	 * Will be called whenever the site is changed. Default implementation just calls
	 * imr.setSite(site) but can be overridden to modify individual site parameters. Site specific
	 * IMRs should override this to just set the site location and default site types, then retrieve
	 * the actual site parameters from the passed in site object
	 * 
	 * @param imr
	 */
	public void setIMRSiteParams(ScalarIMR imr, Site site) {
		imr.setSite(site);
	}
	
	/**
	 * Will be called whenever the rupture is changed. Default implementation just calls
	 * imr.setEqkRupture(rup) but can be overridden to modify individual rupture parameters.
	 * 
	 * @param imr
	 * @param rup
	 */
	public void setIMRRupParams(ScalarIMR imr, EqkRupture rup) {
		imr.setEqkRupture(rup);
	}
	
	/**
	 * Will be called whenever the IMT is changed to set the IMT in the underlying IMR. Default
	 * implementation just calls imr.setIntensityMeasure(imt) but can be overridden if desired.
	 * @param imr
	 * @param imt
	 */
	public void setIMT_IMT(ScalarIMR imr, Parameter<Double> imt) {
		imr.setIntensityMeasure(imt);
	}
	
	/**
	 * Should return the modified log mean, or imr.getMean() if no modification necessary
	 * 
	 * @param imr
	 * @return
	 */
	public abstract double getModMean(ScalarIMR imr);
	
	/**
	 * Should return the modified standard deviation, or imr.getStdDev() if no modification necessary
	 * @param imr
	 * @return
	 */
	public abstract double getModStdDev(ScalarIMR imr);
	
	/**
	 * Calculates exceedance probabilities from the mod mean/std dev. Uses the default implementation
	 * from AttenutationRelationship including truncation but can be overridden for customized behavior
	 * @param imr
	 * @param intensityMeasureLevels
	 * @return
	 * @throws ParameterException
	 */
	public DiscretizedFunc getModExceedProbabilities(ScalarIMR imr,
			DiscretizedFunc intensityMeasureLevels) throws ParameterException {

		double stdDev = getModStdDev(imr);
		double mean = getModMean(imr);
		
		SigmaTruncTypeParam sigmaTruncTypeParam;
		SigmaTruncLevelParam sigmaTruncLevelParam;
		Parameter<?> truncParam = imr.getParameter(SigmaTruncTypeParam.NAME);
		if (truncParam != null && truncParam instanceof SigmaTruncTypeParam) {
			sigmaTruncTypeParam = (SigmaTruncTypeParam) truncParam;
			sigmaTruncLevelParam = (SigmaTruncLevelParam) imr.getParameter(SigmaTruncLevelParam.NAME);
		} else {
			sigmaTruncTypeParam = null;
			sigmaTruncLevelParam = null;
		}

		for (int i=0; i<intensityMeasureLevels.size(); i++) {
			double x = intensityMeasureLevels.getX(i);
			double y = AttenuationRelationship.getExceedProbability(mean, stdDev, x,
					sigmaTruncTypeParam, sigmaTruncLevelParam);
			intensityMeasureLevels.set(i, y);
		}

		return intensityMeasureLevels;
	}
	
	/**
	 * Return any parameters needed for this modifier, or null for no params
	 * @return
	 */
	public abstract ParameterList getModParams();

}
