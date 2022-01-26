package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Dip Parameter, for representing the average dip of the earthquake rupture.
 * See constructors for info on editability and default values.
 */
public class DipParam extends WarningDoubleParameter {

	public final static String NAME = "Dip";
	public final static String UNITS = "degrees";
	public final static String INFO = "Average dip of earthquake rupture";
	protected final static Double MIN = new Double(0);
	protected final static Double MAX = new Double(90);

	/**
	 * This sets the default value and warning-constraint limits
	 *  as given, and leaves the parameter as non editable.
	 */
	public DipParam(double minWarning, double maxWarning, double defaultDip) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
		DoubleConstraint warn = new DoubleConstraint(minWarning,maxWarning);
		warn.setNonEditable();
		setWarningConstraint(warn);
		setInfo(INFO);
		setDefaultValue(defaultDip);
		setNonEditable();
	}

	/**
	 * This sets the default value as 90, and applies the given warning-
	 * constraint limits. The parameter is left as non editable.
	 */
	public DipParam(double minWarning, double maxWarning) { this(minWarning, maxWarning, 90);}

	/**
	 * This sets the default dip as given and and leaves it 
	 * editable so one can set the warning constraint.
	 */
	public DipParam(double dipDefault) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(dipDefault);
	}

	/**
	 * This sets the default dip as 90 degrees, and and leaves it 
	 * editable so one can set the warning constraint.
	 */
	public DipParam() { this(90.0); }

}
