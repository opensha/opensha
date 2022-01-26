package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This parameter specifies the maximum magnitude for point source off-modeled fault ruptures in the ETAS model.
 * 
 * Above this magnitude, ruptures will be assigned a random finite surface (See ETAS_Utils.getRandomFiniteRupSurface(...))
 */
public class U3ETAS_MaxPointSourceMagParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Max Point Source Mag";
	public final static String INFO = "The maximum magnitude for point source representation of gridded ruptures. "
			+ "Above this magnitude ruptures will be assigned a random finite surface.";
	public final static Double MIN = 5d;
	public final static Double MAX = 10d;
	// default is disabled, for now.
	// if we change default, also update U3ETAS launcher docs to match:
	//	docs/json_configuration_format.md
	//	docs/configuring_simulations.md
	public final static Double DEFAULT_VALUE = MAX;

	/**
	 * This sets the default value as given.
	 */
	public U3ETAS_MaxPointSourceMagParam(double defaultValue) {
		super(NAME, MIN, MAX);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public U3ETAS_MaxPointSourceMagParam() { this(DEFAULT_VALUE); }
	
	
}
