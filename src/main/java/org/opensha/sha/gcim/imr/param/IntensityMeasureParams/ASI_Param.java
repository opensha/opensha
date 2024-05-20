package org.opensha.sha.gcim.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log Acceleration Spectrum Intensity intensity measure
 * parameter, defined as ASI = integral{Sa(T,5%)} from 0.1-0.5 seconds, where Sa = pseudo
 * spectral acceleration;
 * See constructors for info on editability and default values.
 * @author Brendon Bradley April 2010
 *
 */
public class ASI_Param extends WarningDoubleParameter {

    public final static String NAME = "ASI"; 
    public final static String UNITS = "g.s"; 
    public final static String INFO = "Acceleration Spectrum Intensity"; 
    public final static Double MIN = Double.valueOf(Math.log(Double.MIN_VALUE)); 
    public final static Double MAX = Double.valueOf(Double.MAX_VALUE); 
    public final static Double DEFAULT_WARN_MIN = Double.valueOf(Math.log(Double.MIN_VALUE)); 
    public final static Double DEFAULT_WARN_MAX = Double.valueOf(Math.log(1.5));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultASI
	 */
	public ASI_Param(DoubleConstraint warningConstraint, double defaultASI) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultASI);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(1.0) (the natural
	 * log of 1.0).
	 * The parameter is left as non editable
	 */
	public ASI_Param() {
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
