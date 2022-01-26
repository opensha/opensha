package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * Aperiodicity for BPT renewal model.
 */
public class MaximumMagnitudeParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Maximum Magnitude";
	public final static String INFO = "The maximum magnitude for the region";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(5.0);
	protected final static Double MAX = new Double(10.0);
	

	/**
	 * This sets the default value as given.
	 */
	public MaximumMagnitudeParam(double defaultMaxMag) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultMaxMag);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 8.3.
	 */
	public MaximumMagnitudeParam() { this(8.3);}
	
	
}
