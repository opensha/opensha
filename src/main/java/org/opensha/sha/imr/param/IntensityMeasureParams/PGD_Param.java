package org.opensha.sha.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes is for the natural-log Peak Ground Displacement intensity measure
 * parameter.
 * See constructors for info on editability and default values.  
 * @author field
 *
 */
public class PGD_Param extends WarningDoubleParameter {

	public final static String NAME = "PGD";
	public final static String UNITS = "cm";
	public final static String INFO = "Peak Ground Displacement";
	public final static Double MIN = new Double(Math.log(Double.MIN_VALUE));
	public final static Double MAX = new Double(Double.MAX_VALUE);
	public final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE));
	protected final static Double DEFAULT_WARN_MAX = new Double(Math.log(2500));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultPGA
	 */
	public PGD_Param(DoubleConstraint warningConstraint, double defaultPGA) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultPGA);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(0.01) (the natural
	 * log of 0.01).
	 * The parameter is left as non editable
	 */
	public PGD_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
	    warn2.setNonEditable();
	    setWarningConstraint(warn2);
	    setDefaultValue(Math.log(0.01));
	    setNonEditable();
	}
}
