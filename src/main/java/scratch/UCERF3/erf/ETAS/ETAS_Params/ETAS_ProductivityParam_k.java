package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This k_ProductivityParam is used for setting the productivity 
 * parameter of the ETAS model.
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf), except
 * units are converted from years to days here.
 */
public class ETAS_ProductivityParam_k extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "k - ETAS Productivity";
	public final static String INFO = "The ETAS productivity parameter";
	private static final String UNITS = "(days)^(p-1)";
	public final static Double MIN = Double.valueOf(3.79E-4*Math.pow(365.25,0.07));
	public final static Double MAX = Double.valueOf(4.97E-3*Math.pow(365.25,0.07)*2);	// multiplied by two to allow Felzer value
	public final static Double DEFAULT_VALUE = Double.valueOf(2.84E-03*Math.pow(365.25,0.07));

	/**
	 * This sets the default value as given.
	 */
	public ETAS_ProductivityParam_k(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_ProductivityParam_k() { this(DEFAULT_VALUE);}
	
	
}
