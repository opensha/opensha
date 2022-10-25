package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * Aperiodicity for BPT renewal model.
 */
public class BPT_AperiodicityParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "BPT Aperiodicity";
	public final static String INFO = "Aperiodicity for BPT renewal model";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(0.01);
	protected final static Double MAX = new Double(1.79);
	

	/**
	 * This sets the default value as given.
	 */
	public BPT_AperiodicityParam(double defaultAperiodicity) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultAperiodicity);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public BPT_AperiodicityParam() { this(0.2);}
	
	
}
