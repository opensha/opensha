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
public class FixedStdDevMod extends AbstractAttenRelMod {
	
	public static final String NAME = "Fixed Std. Dev.";
	public static final String SHORT_NAME = "FixedStdDev";
	
	public static final String STD_DEV_PARAM_NAME = "Std. Dev.";
	
	private ParameterList modParams;
	private DoubleParameter stdDevParam;
	
	public FixedStdDevMod() {
		modParams = new ParameterList();
		
		stdDevParam = new DoubleParameter(STD_DEV_PARAM_NAME, 0.5);
		stdDevParam.setDefaultValue(0.5);
		stdDevParam.setValueAsDefault();
		
		modParams.addParameter(stdDevParam);
	}
	
	@Override
	public void setIMRParams(ScalarIMR imr) {
		// do nothing
	}

	@Override
	public double getModMean(ScalarIMR imr) {
		return imr.getMean();
	}

	@Override
	public double getModStdDev(ScalarIMR imr) {
//		if (Math.random() < 0.0001)
//			System.out.println("GetStdDev: "+stdDevParam.getValue());
		return stdDevParam.getValue();
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
