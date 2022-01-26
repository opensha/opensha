package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This FaultGridSpacingParameter is for setting fault discretization in
 * in gridded surfaces.
 */
public class FaultGridSpacingParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Fault Grid Spacing";
	public final static String INFO = "For discretization of faults";
	private static final String UNITS = "km";
	protected final static Double MIN = new Double(0.1d);
	protected final static Double MAX = new Double(10d);
	

	/**
	 * This sets the default value as given.
	 */
	public FaultGridSpacingParam(double defaultMag) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultMag);
	    this.setValueAsDefault();
	}

	/**
	 * This sets the default value as 1.0 km.
	 */
	public FaultGridSpacingParam() { this(1.0);}
	
	
}
