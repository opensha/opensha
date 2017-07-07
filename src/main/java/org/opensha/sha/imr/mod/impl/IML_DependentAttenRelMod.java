package org.opensha.sha.imr.mod.impl;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;

public abstract class IML_DependentAttenRelMod extends AbstractAttenRelMod {
	
	/**
	 * Should return the modified log mean, or imr.getMean() if no modification necessary
	 * 
	 * @param imr
	 * @param iml
	 * @return
	 */
	public double getModMean(ScalarIMR imr) {
		if (isCurrentlyIML_Dependent())
			throw new UnsupportedOperationException(
				"IML Dependent Modifier cannot call getModMean(imr), must call getModMean(imr, iml)");
		return getModMean(imr, Double.NaN);
	}
	
	public abstract double getModMean(ScalarIMR imr, double iml);
	
	/**
	 * Should return the modified standard deviation, or imr.getStdDev() if no modification necessary
	 * @param imr
	 * @return
	 */
	public double getModStdDev(ScalarIMR imr) {
		if (isCurrentlyIML_Dependent())
			throw new UnsupportedOperationException(
				"IML Dependent Modifier cannot call getModStdDev(imr), must call getModStdDev(imr, iml)");
		return getModStdDev(imr, Double.NaN);
	}
	
	/**
	 * Should return the modified standard deviation, or imr.getStdDev() if no modification necessary
	 * @param imr
	 * @param iml
	 * @return
	 */
	public abstract double getModStdDev(ScalarIMR imr, double iml);

	@Override
	public DiscretizedFunc getModExceedProbabilities(ScalarIMR imr, DiscretizedFunc intensityMeasureLevels)
			throws ParameterException {
		if (!isCurrentlyIML_Dependent())
			return super.getModExceedProbabilities(imr, intensityMeasureLevels);
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
			
			double stdDev = getModStdDev(imr, x);
			double mean = getModMean(imr, x);
			
			double y = AttenuationRelationship.getExceedProbability(mean, stdDev, x,
					sigmaTruncTypeParam, sigmaTruncLevelParam);
			intensityMeasureLevels.set(i, y);
		}

		return intensityMeasureLevels;
	}
	
	protected boolean isCurrentlyIML_Dependent() {
		return true;
	}

}
