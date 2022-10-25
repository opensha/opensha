package org.opensha.sha.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log Peak Ground Acceleration intensity measure
 * parameter.  
 * See constructors for info on editability and default values.
 * @author field
 *
 */
public class PGA_Param extends WarningDoubleParameter {

	public final static String NAME = "PGA";
	public final static String UNITS = "g";
	public final static String INFO = "Peak Ground Acceleration";
	public final static Double MIN = new Double(Math.log(Double.MIN_VALUE));
	public final static Double MAX = new Double(Double.MAX_VALUE);
	public final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE));
	public final static Double DEFAULT_WARN_MAX = new Double(Math.log(2.5));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultPGA
	 */
	public PGA_Param(DoubleConstraint warningConstraint, double defaultPGA) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultPGA);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(1.0) (the natural
	 * log of 1.0).
	 * The parameter is left as non editable
	 */
	public PGA_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
	    warn2.setNonEditable();
	    setWarningConstraint(warn2);
	    setDefaultValue(Math.log(1.0));
	    setNonEditable();
	}
}
