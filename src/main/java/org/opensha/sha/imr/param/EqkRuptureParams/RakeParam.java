package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.DoubleParameter;

/**
 * Rake Parameter, reserved for representing the average rake of the earthquake
 * rupture.
 * See constructors for info on editability and default values.
 */
public class RakeParam extends DoubleParameter {

	public final static String NAME = "Rake";
	public final static String UNITS = "degrees";
	public final static String INFO = "Average rake of earthquake rupture";
	protected final static Double MIN = new Double( -180);
	protected final static Double MAX = new Double(180);

	/**
	 * This sets the default as given  
	 * This also leaves the parameter as non editable.
	 */
	public RakeParam(double defaultRake) {
		this(defaultRake, false);
	}
	
	public RakeParam(Double defaultRake, boolean nullAllowed) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(nullAllowed);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultRake);
	    setNonEditable();
	}

	/**
	 * This sets the default as 0.0  
	 * This also leaves the parameter as non editable.
	 */
	public RakeParam() {this(0.0);}

}
