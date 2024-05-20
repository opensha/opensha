package org.opensha.sha.imr.param.SiteParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Depth 1.0 km/sec Parameter, reserved for representing the depth to where
 * shear-wave velocity = 1.0 km/sec.
 * See constructors for info on editability and default values.
 */
public class DepthTo1pt0kmPerSecParam extends WarningDoubleParameter {


	public final static String NAME = "Depth 1.0 km/sec";
	public final static String UNITS = "m";
	public final static String INFO = "The depth to where shear-wave velocity = 1.0 km/sec";
	public final static Double MIN = Double.valueOf(0.0);
	public final static Double MAX = Double.valueOf(30000.0);


	/**
	 * This constructor sets the default as given, and leaves the param editable 
	 * so the warning constraint can be added later. Sets the primary constraint
	 * to allow <code>null</code>, or not.
	 * @param defaultDepth
	 * @param allowsNull 
	 */
	public DepthTo1pt0kmPerSecParam(Double defaultDepth, boolean allowsNull) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(allowsNull);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultDepth);
	}

	/**
	 * This constructor sets the default as 100, and leaves the param editable 
	 * so the warning constraint can be added later. Parameter configured via
	 * this constructor does allows <code>null</code> values.
	 */
	public DepthTo1pt0kmPerSecParam() {this(100.0, true);}

	/**
	 * This uses the given default and warning-constraint limits, and sets 
	 * everything as non-editable.
	 * @param defaultDepth
	 * @param warnMin
	 * @param warnMax
	 * @param allowsNull 
	 */
	public DepthTo1pt0kmPerSecParam(
			Double defaultDepth, double warnMin, double warnMax, boolean allowsNull) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(allowsNull);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultDepth);
		DoubleConstraint warn = new DoubleConstraint(warnMin, warnMax);
		setWarningConstraint(warn);
		warn.setNonEditable();
		setNonEditable();
	}
	
	/**
	 * This sets default as 100, uses the given warning-constraint limits, and sets 
	 * everything as non-editable. Parameter configured via
	 * this constructor allows <code>null</code> values.
	 * @param warnMin
	 * @param warnMax
	 */
	public DepthTo1pt0kmPerSecParam(double warnMin, double warnMax) {
		this(100.0,warnMin,warnMax, true);
	}
}
