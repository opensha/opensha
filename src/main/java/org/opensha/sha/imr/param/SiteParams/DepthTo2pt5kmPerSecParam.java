package org.opensha.sha.imr.param.SiteParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;


/**
 * Depth 2.5 km/sec Parameter, reserved for representing the depth to where
 * shear-wave velocity = 2.5 km/sec.
 * See constructors for info on editability and default values.
 */
public class DepthTo2pt5kmPerSecParam extends WarningDoubleParameter {

	
	public final static String NAME = "Depth 2.5 km/sec";
	public final static String UNITS = "km";
	public final static String INFO = "The depth to where shear-wave velocity = 2.5 km/sec";
//	public final static Double DEFAULT = new Double("1.0");
	public final static Double MIN = new Double(0.0);
	public final static Double MAX = new Double(30000.0);


	/**
	 * This constructor sets the default as given, and leaves everything editable 
	 * (e.g., so the warning constraint can be added later).
	 * @param defaultDepth
	 */
	public DepthTo2pt5kmPerSecParam(Double defaultDepth) {
		this(defaultDepth, true);
	}
	
	public DepthTo2pt5kmPerSecParam(Double defaultDepth, boolean allowsNull) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(allowsNull);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultDepth);
	}

	/**
	 * This constructor sets the default as 1.0, and leaves everything editable 
	 * (e.g., so the warning constraint can be added later).
	 */
	public DepthTo2pt5kmPerSecParam() {this(1.0);}

	/**
	 * This uses the given default and warning-constraint limits, and sets 
	 * everything as non-editable.
	 * @param defaultDepth
	 * @param warnMin
	 * @param warnMax
	 * @param nullAllowed - tells whether null values are to be allowed
	 */
	public DepthTo2pt5kmPerSecParam(Double defaultDepth, double warnMin, double warnMax, boolean nullAllowed) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(nullAllowed);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultDepth);
		DoubleConstraint warn = new DoubleConstraint(warnMin, warnMax);
		setWarningConstraint(warn);
		warn.setNonEditable();
		setNonEditable();
	}
	
	/**
	 * This sets default as 1.0, uses the given warning-constraint limits, and sets 
	 * everything as non-editable.
	 * @param warnMin
	 * @param warnMax
	 * @param nullAllowed - tells whether null values are to be allowed
	 */
	public DepthTo2pt5kmPerSecParam(double warnMin, double warnMax, boolean nullAllowed) {
		this(1.0,warnMin,warnMax,nullAllowed);
	}
}
