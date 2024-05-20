package org.opensha.sha.gcim.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log Cumulative Absolute Velocity intensity measure
 * parameter, defined as CAV = integral{abs(a(t))}, where a(t) is the acceleration time
 * history; abs() is absolute value; and the integral is over the entire duration of the 
 * time history
 * See constructors for info on editability and default values.
 * @author Brendon Bradley Oct 2010
 *
 */
public class CAV_Param extends WarningDoubleParameter {

    public final static String NAME = "CAV"; 
    public final static String UNITS = "g.s"; 
    public final static String INFO = "Cumulative Absolute Velocity"; 
    public final static Double MIN = Double.valueOf(Math.log(Double.MIN_VALUE)); 
    public final static Double MAX = Double.valueOf(Double.MAX_VALUE); 
    public final static Double DEFAULT_WARN_MIN = Double.valueOf(Math.log(Double.MIN_VALUE)); 
    public final static Double DEFAULT_WARN_MAX = Double.valueOf(Math.log(0.3));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultCAV
	 */
	public CAV_Param(DoubleConstraint warningConstraint, double defaultCAV) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultCAV);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(0.3) (the natural
	 * log of 0.3).
	 * The parameter is left as non editable
	 */
	public CAV_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
	    warn2.setNonEditable();
	    setWarningConstraint(warn2);
	    setDefaultValue(Math.log(0.3));
	    setNonEditable();
	}
}
