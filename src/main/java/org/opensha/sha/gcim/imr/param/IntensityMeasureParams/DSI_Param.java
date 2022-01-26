package org.opensha.sha.gcim.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log Displacement Spectrum Intensity intensity measure
 * parameter, defined as DSI = integral{Sd(T,5%)} from 2.0-5.0 seconds, where Sd = 
 * spectral displacement;
 * See constructors for info on editability and default values.
 * @author Brendon Bradley Sept 2010
 *
 */
public class DSI_Param extends WarningDoubleParameter {

    public final static String NAME = "DSI"; 
    public final static String UNITS = "cm.s"; 
    public final static String INFO = "Displacement Spectrum Intensity"; 
    public final static Double MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double MAX = new Double(Double.MAX_VALUE); 
    public final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double DEFAULT_WARN_MAX = new Double(Math.log(3.0));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultDSI
	 */
	public DSI_Param(DoubleConstraint warningConstraint, double defaultDSI) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultDSI);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(0.3) (the natural
	 * log of 0.3).
	 * The parameter is left as non editable
	 */
	public DSI_Param() {
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
