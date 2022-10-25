package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This p_ETAS_TemporalDecayParam is used for setting the decay 
 * value (p) in the ETAS temporal decay: (t+c)^-p.  
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf).
 */
public class ETAS_TemporalDecayParam_p extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "p - ETAS Temporal Decay";
	public final static String INFO = "The ETAS p value in the temporal decay: (t+c)^-p";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(1.00);
	protected final static Double MAX = new Double(1.40);
	public final static Double DEFAULT_VALUE = new Double(1.07);

	/**
	 * This sets the default value as given.
	 */
	public ETAS_TemporalDecayParam_p(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_TemporalDecayParam_p() { this(DEFAULT_VALUE);}
	
	
}
