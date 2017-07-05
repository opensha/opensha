package scratch.UCERF3.erf.ETAS.ETAS_Params;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

/**
 * This is for representing probability model options
 *
 * 
 * @author Ned Field
 * @version $Id:$
 */
public class U3ETAS_ProbabilityModelParam extends EnumParameter<U3ETAS_ProbabilityModelOptions> {
	
	public static final String NAME = "U3ETAS Probability Model";
	public static final String INFO = "This is for setting the different types of supported probability models";
	public static final U3ETAS_ProbabilityModelOptions DEFAULT = U3ETAS_ProbabilityModelOptions.FULL_TD;

	public U3ETAS_ProbabilityModelParam() {
		super(NAME, EnumSet.allOf(U3ETAS_ProbabilityModelOptions.class), DEFAULT, null);
		setInfo(INFO);
	}

}
