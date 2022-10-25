package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * This ImposeGR_SamplingParam tells whether or not to impose 
 * Gutenberg-Richter sampling in the ETAS model.
 */
public class ETAS_ImposeGR_SamplingParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Impose GR Sampling";
	public final static String INFO = "This tells whether to impose Gutenberg-Richter in sampling ETAS aftershocks";

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ETAS_ImposeGR_SamplingParam(boolean defaultValue) {
		super(NAME);
		setInfo(INFO);
		setDefaultValue(defaultValue);
		setValue(defaultValue);	// this is needed for some reason
	}
	
	/**
	 * This sets the default value as "true", and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ETAS_ImposeGR_SamplingParam() {this(false);}

}
