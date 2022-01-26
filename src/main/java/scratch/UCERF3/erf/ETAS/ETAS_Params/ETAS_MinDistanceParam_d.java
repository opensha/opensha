package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This d_ETAS_MinDistanceParam is used for setting the minimum distance 
 * (d) in the ETAS linear distance decay: (r+d)^-q.
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf).
 */
public class ETAS_MinDistanceParam_d extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "d - ETAS Min Distance";
	public final static String INFO = "The ETAS d value in the linear distance decay: (r+d)^-q";
	private static final String UNITS = "km";
	protected final static Double MIN = new Double(0.63);
	protected final static Double MAX = new Double(4.00);
	public final static Double DEFAULT_VALUE = new Double(0.79);


	/**
	 * This sets the default value as given.
	 */
	public ETAS_MinDistanceParam_d(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_MinDistanceParam_d() { this(DEFAULT_VALUE);}
	
	
}
