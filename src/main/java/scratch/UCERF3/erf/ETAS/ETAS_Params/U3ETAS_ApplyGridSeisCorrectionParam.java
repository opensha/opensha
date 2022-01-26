package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * This U3ETAS_ApplySubSeisRatesWhenComputingSupraNucleationRatesParam tells whether 
 * supra-seismogenic nucleation rates should be proportional to the sub-seismogenic rate
 * within each fault subsection
  */
public class U3ETAS_ApplyGridSeisCorrectionParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply SubSeis Rates to Supra Nucleation";
	public final static String INFO = "This tells whether supra-seismogenic nucleation rates should be proportional to sub-seismogenic rates within each fault subsection";
	public static final boolean DEFAULT = true;

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public U3ETAS_ApplyGridSeisCorrectionParam(boolean defaultValue) {
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
	public U3ETAS_ApplyGridSeisCorrectionParam() {this(DEFAULT);}

}
