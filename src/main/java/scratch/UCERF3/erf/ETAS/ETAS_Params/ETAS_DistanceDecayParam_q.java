package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This q_ETAS_DistanceDecayParam is used for setting the distance decay 
 * value (q) in the ETAS linear distance decay: (r+d)^-q.
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf).
 */
public class ETAS_DistanceDecayParam_q extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "q - ETAS Distance Decay";
	public final static String INFO = "The ETAS q value in the linear distance decay: (r+d)^-q";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(1.8);
	protected final static Double MAX = new Double(4.00);
	public final static Double DEFAULT_VALUE = new Double(1.96);


	/**
	 * This sets the default value as given.
	 */
	public ETAS_DistanceDecayParam_q(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_DistanceDecayParam_q() { this(DEFAULT_VALUE);}
	
	
}
