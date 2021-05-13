package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;

public class ErgodicFromRefIMRMod extends AbstractAttenRelMod {
	
	public static final String NAME = "Ergodic From Ref IMR";
	public static final String SHORT_NAME = "ErgodicRef";

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
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
		return imr.getStdDev();
	}

	@Override
	public ParameterList getModParams() {
		return null;
	}

}
