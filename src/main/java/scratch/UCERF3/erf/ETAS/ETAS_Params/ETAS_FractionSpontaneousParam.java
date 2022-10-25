package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This n_ETAS_BranchingRatioParam is used for setting the fraction (n)
 * of ETAS earthquakes that are triggered (relative to all earthquakes).
 * The fraction of spontaneous earthquakes is therefore 1-n.
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf)
 */
public class ETAS_FractionSpontaneousParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "n - ETAS Fraction Spontaneous";
	public final static String INFO = "The fraction of ETAS earthquakes that are spontaneous (vs triggered)";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(0.1);
	protected final static Double MAX = new Double(0.9);
	public final static Double DEFAULT_VALUE = new Double(0.33);
	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_FractionSpontaneousParam(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_FractionSpontaneousParam() { this(DEFAULT_VALUE);}
	
	
}
