package org.opensha.sha.imr.mod.impl;

import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;

import com.google.common.primitives.Doubles;

/**
 * Simple modifier demo which allows the user to scale the mean/std dev of an underlying IMR
 * 
 * @author kevin
 *
 */
public class SimpleScaleMod extends AbstractAttenRelMod {
	
	public static final String NAME = "Simple Scalar";
	public static final String SHORT_NAME = "SimpleScalar";
	
	private ParameterList modParams;
	private DoubleParameter meanScaleParam;
	private DoubleParameter stdDevScaleParam;
	private BooleanParameter logSpaceScaleParam;
	
	public SimpleScaleMod() {
		modParams = new ParameterList();
		
		meanScaleParam = new DoubleParameter("Mean Scale Factor", 1d);
		meanScaleParam.setDefaultValue(1d);
		meanScaleParam.setValueAsDefault();
		stdDevScaleParam = new DoubleParameter("Std. Dev. Scale Factor", 1d);
		stdDevScaleParam.setDefaultValue(1d);
		stdDevScaleParam.setValueAsDefault();
		logSpaceScaleParam = new BooleanParameter("Mean Scale In Log Space", false);
		logSpaceScaleParam.setValueAsDefault();
		
		modParams.addParameter(meanScaleParam);
		modParams.addParameter(stdDevScaleParam);
		modParams.addParameter(logSpaceScaleParam);
	}
	
	@Override
	public void setIMRParams(ScalarIMR imr) {
		// do nothing
	}

	@Override
	public double getModMean(ScalarIMR imr) {
		return getScaledVal(meanScaleParam.getValue(), imr.getMean(), !logSpaceScaleParam.getValue());
	}

	@Override
	public double getModStdDev(ScalarIMR imr) {
		return getScaledVal(stdDevScaleParam.getValue(), imr.getStdDev(), false);
	}
	
	private double getScaledVal(double scalar, double val, boolean convertToLinear) {
		if (scalar == 1d)
			return val;
		
		if (convertToLinear) {
			if (!Doubles.isFinite(val))
				return val;
			// convert to linear space
			val = Math.exp(val);
			// multiply in linear space
			val *= scalar;
			// convert back to log space
			val = Math.log(val);
//			System.out.println("from "+orig+" to "+val);
			return val;
		}
		return val*scalar;
	}

	@Override
	public ParameterList getModParams() {
		return modParams;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

}
