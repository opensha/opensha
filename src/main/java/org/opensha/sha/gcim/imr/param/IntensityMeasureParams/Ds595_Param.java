package org.opensha.sha.gcim.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log 5-95% significant duration intensity measure
 * parameter, defined as the time interval over which 5% to 95% of the total arias intensity
 * of the ground motion is accumulated.  Formally, 
 * 			Ds595 = t95 - t5; where
 * 			ta is defined so that I=integral(a(t)^2) from t=0 to t=ta is equal to a% of the total 
 * 			arias intensity Ia = integral(a(t)^2) from t=0 to t=tmax.
 *
 * Unlike the 5-75% Significant duration (see Ds575) this definition is intended to capture
 * the duration of both body and surface waves (and not just body waves).
 * See constructors for info on editability and default values.
 * @author Brendon Bradley Oct 2010
 *
 */
public class Ds595_Param extends WarningDoubleParameter {

    public final static String NAME = "Ds595"; 
    public final static String UNITS = "s"; 
    public final static String INFO = "Significant Duration (5-95%)"; 
    public final static Double MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double MAX = new Double(Double.MAX_VALUE); 
    public final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double DEFAULT_WARN_MAX = new Double(Math.log(5.0));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultDs595
	 */
	public Ds595_Param(DoubleConstraint warningConstraint, double defaultDs595) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultDs595);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(5.0) (the natural
	 * log of 5.0).
	 * The parameter is left as non editable
	 */
	public Ds595_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
	    warn2.setNonEditable();
	    setWarningConstraint(warn2);
	    setDefaultValue(Math.log(5.0));
	    setNonEditable();
	}
}
