package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * This ApplyLongTermRatesInETAS_SamplingParam tells whether or not to apply the
 * spatial distribution of long-term rates when sampling ETAS aftershocks 
 */
public class ETAS_ApplyLongTermRatesInSamplingParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply Long-Term Rates";
	public final static String INFO = "This tells whether to apply the spatial distribution of long-term rates when sampling ETAS aftershocks";
	public static final boolean DEFAULT = true;

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ETAS_ApplyLongTermRatesInSamplingParam(boolean defaultValue) {
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
	public ETAS_ApplyLongTermRatesInSamplingParam() {this(DEFAULT);}

}
