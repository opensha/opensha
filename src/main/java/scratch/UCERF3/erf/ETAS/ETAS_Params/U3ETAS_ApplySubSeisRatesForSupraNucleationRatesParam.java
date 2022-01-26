package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * This U3ETAS_ApplyGridSeisCorrectionParam tells whether or not to correct 
 * gridded-seismicity rates to not be less than the expected rate of aftershocks
 * from supra-seismogenic events
 */
public class U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply Gridded Seis Correction";
	public final static String INFO = "This tells whether to correct gridded seismicity rates soas not to be less than the expected rate of aftershocks from supraseismogenic events";
	public static final boolean DEFAULT = true;

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam(boolean defaultValue) {
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
	public U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam() {this(DEFAULT);}

}
