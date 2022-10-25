package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * 
 */
public class U3ETAS_MaxCharFactorParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "MaxCharFactor";
	public final static String INFO = "The maximum CharFactor allowed on a fault section";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(1.0);
	protected final static Double MAX = new Double(100.00);
	public final static Double DEFAULT_VALUE = new Double(10.0);


	/**
	 * This sets the default value as given.
	 */
	public U3ETAS_MaxCharFactorParam(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 10.
	 */
	public U3ETAS_MaxCharFactorParam() { this(DEFAULT_VALUE);}
	
	
}
