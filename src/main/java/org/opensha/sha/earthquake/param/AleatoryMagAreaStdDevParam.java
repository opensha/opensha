package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This AleatoryMagVariabilityParam is used for setting the variability 
 * in magnitude for given fault area.
 */
public class AleatoryMagAreaStdDevParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Aleatory Mag-Area StdDev";
	public final static String INFO = "For variability of magnitude for given area";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(0d);
	protected final static Double MAX = new Double(1.0d);
	

	/**
	 * This sets the default value as given.
	 */
	public AleatoryMagAreaStdDevParam(double defaultStdDev) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultStdDev);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public AleatoryMagAreaStdDevParam() { this(0.0);}
	
	
}
