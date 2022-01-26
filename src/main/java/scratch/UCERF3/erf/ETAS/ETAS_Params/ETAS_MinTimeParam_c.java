package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This c_ETAS_MinTimeParam is used for setting the minimum time value
 * (c) in the ETAS temporal decay: (t+c)^-p.  
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf), except
 * units are converted from years to days here.
 */
public class ETAS_MinTimeParam_c extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "c - ETAS Min Time";
	public final static String INFO = "The ETAS c value in the temporal decay: (t+c)^-p";
	private static final String UNITS = "days";
	protected final static Double MIN = new Double(1.00E-6*365.25);
	protected final static Double MAX = new Double(3.16E-4*365.25);
	public final static Double DEFAULT_VALUE = new Double(1.78E-05*365.25);
	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_MinTimeParam_c(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_MinTimeParam_c() { this(DEFAULT_VALUE);}
	
	
}
