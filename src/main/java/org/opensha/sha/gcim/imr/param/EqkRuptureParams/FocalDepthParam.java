package org.opensha.sha.gcim.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Magnitude parameter, reserved for representing source focal depth.
 * The warning constraint must be created and added after instantiation.
 * See constructors for info on editability and default values.
 */
public class FocalDepthParam extends WarningDoubleParameter {

	public final static String NAME = "Focal Depth";
	public final static String INFO = "Earthquake Source Focal Depth";
	protected final static Double MIN = new Double(0);
	protected final static Double MAX = new Double(500);
	// warning values are set in subclasses
	
	/**
	 * This sets the default value and warning-constraint limits
	 *  as given, and leaves the parameter as non editable.
	 */
	public FocalDepthParam(double minWarning, double maxWarning, double defaultFocalDepth) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
		DoubleConstraint warn = new DoubleConstraint(minWarning, maxWarning);
		warn.setNonEditable();
		setWarningConstraint(warn);
	    setInfo(INFO);
	    setDefaultValue(defaultFocalDepth);
	    setNonEditable();
	    
	}

	/**
	 * This sets the default value as 50km, and applies the given warning-
	 * constraint limits. The parameter is left as non editable.
	 */
	public FocalDepthParam(double minWarning, double maxWarning) { this(minWarning, maxWarning, 50.0);}

	/**
	 * This sets the default value as given.  No warning limits are set, so
	 * this is left editable so warning constraints can be added.
	 */
	public FocalDepthParam(double defaultFocalDepth) {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultFocalDepth);
	}

	/**
	 * This sets the default value as 50.0.  No warning limits are set, so
	 * this is left editable so warning constraints can be added.
	 */
	public FocalDepthParam() { this(50.0);}
	
	
}
