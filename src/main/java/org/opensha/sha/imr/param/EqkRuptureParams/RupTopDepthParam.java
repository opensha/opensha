package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * RupTopDepth parameter - Depth to top of rupture.
 * See constructors for info on editability and default values.
 */
public class RupTopDepthParam extends WarningDoubleParameter {

	public final static String NAME = "Rupture Top Depth";
	public final static String UNITS = "km";
	public final static String INFO = "The depth to the shallowest point on the earthquake rupture surface";
	protected final static Double MIN = new Double(0);
	protected final static Double MAX = new Double(100);
	// warning values are set in subclasses
	
	
	/**
	 * This sets the default value and warning-constraint limits
	 *  as given, and leaves the parameter as non editable.
	 */
	public RupTopDepthParam(double minWarning, double maxWarning, double defaultDepth) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
		DoubleConstraint warn = new DoubleConstraint(minWarning,maxWarning);
		warn.setNonEditable();
		setWarningConstraint(warn);
		setInfo(INFO);
		setDefaultValue(defaultDepth);
		setNonEditable();
	}

	/**
	 * This sets the default value as 0.0, and applies the given warning-
	 * constraint limits. The parameter is left as non editable.
	 */
	public RupTopDepthParam(double minWarning, double maxWarning) { this(minWarning, maxWarning, 0.0);}
	
	/**
	 * This sets the default as given
	 * This is left editable so warning constraints can be added.
	 */
	public RupTopDepthParam(double defaultDepth) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultDepth);
	}

	/**
	 * This sets the default as 0.0
	 * This is left editable so warning constraints can be added.
	 */
	public RupTopDepthParam() { this(0.0); }

}
