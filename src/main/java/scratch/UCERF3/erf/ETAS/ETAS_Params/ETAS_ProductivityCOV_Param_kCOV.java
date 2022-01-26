package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This k_ProductivityCOV_Param is used for setting the productivity 
 * COV of the ETAS model, assuming a log-normal distribution with a mean of 1.0
 * (the latter to avoid biased rates).  This allows aleatory variability in productivity.
 * 
 */
public class ETAS_ProductivityCOV_Param_kCOV extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "k - ETAS Productivity COV";
	public final static String INFO = "The ETAS productivity COV parameter (assuming lon-normal distribution)";
	private static final String UNITS = null;
	public final static Double MIN = 0.0;
	public final static Double MAX = 2.0;	
	public final static Double DEFAULT_VALUE = 0.0;	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_ProductivityCOV_Param_kCOV(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_ProductivityCOV_Param_kCOV() { this(DEFAULT_VALUE);}
	
	
}
