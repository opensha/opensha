package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Magnitude parameter, reserved for representing moment magnitude.
 * The warning constraint must be created and added after instantiation.
 * See constructors for info on editability and default values.
 */
public class MagParam extends WarningDoubleParameter {

	public final static String NAME = "Magnitude";
	public final static String INFO = "Earthquake Moment Magnatude";
	protected final static Double MIN = new Double(0);
	protected final static Double MAX = new Double(10);
	// warning values are set in subclasses
	
	/**
	 * This sets the default value and warning-constraint limits
	 *  as given, and leaves the parameter as non editable.
	 */
	public MagParam(double minWarning, double maxWarning, double defaultMag) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
		DoubleConstraint warn = new DoubleConstraint(minWarning, maxWarning);
		warn.setNonEditable();
		setWarningConstraint(warn);
	    setInfo(INFO);
	    setDefaultValue(defaultMag);
	    setNonEditable();
	    
	}

	/**
	 * This sets the default value as 5.5, and applies the given warning-
	 * constraint limits. The parameter is left as non editable.
	 */
	public MagParam(double minWarning, double maxWarning) { this(minWarning, maxWarning, 5.5);}

	/**
	 * This sets the default value as given.  No warning limits are set, so
	 * this is left editable so warning constraints can be added.
	 */
	public MagParam(double defaultMag) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultMag);
	}

	/**
	 * This sets the default value as 5.5.  No warning limits are set, so
	 * this is left editable so warning constraints can be added.
	 */
	public MagParam() { this(5.5);}
	
	
}
