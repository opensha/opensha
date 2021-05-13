package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

/**
 * This is for representing probability model options
 *
 * 
 * @author Ned Field
 * @version $Id:$
 */
public class ProbabilityModelParam extends EnumParameter<ProbabilityModelOptions> {
	
	public static final String NAME = "Probability Model";
	public static final String INFO = "This is for setting the different types of supported probability models";

	public ProbabilityModelParam() {
		super(NAME, EnumSet.allOf(ProbabilityModelOptions.class), ProbabilityModelOptions.POISSON, null);
		setInfo(INFO);
	}

}
