package org.opensha.sha.imr.param.SiteParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;


/**
 * Coastal margin sediment thickness, used by some NSHMP-Haz GMMs
 */
public class SedimentThicknessParam extends WarningDoubleParameter {

	
	public final static String NAME = "Sediment Thickness (zSed)";
	public final static String UNITS = "km";
	public final static String INFO = "Thickness of coastal margin sediments";
//	public final static Double DEFAULT = new Double("1.0");
	public final static Double MIN = new Double(0.0);
	public final static Double MAX = new Double(30000.0);


	/**
	 * This constructor sets the default as given, and leaves everything editable 
	 * (e.g., so the warning constraint can be added later).
	 * @param defaultThickness
	 */
	public SedimentThicknessParam(Double defaultThickness) {
		this(defaultThickness, true);
	}
	
	public SedimentThicknessParam(Double defaultThickness, boolean allowsNull) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(allowsNull);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultThickness);
	}

	/**
	 * This constructor sets the default as null, and leaves everything editable 
	 * (e.g., so the warning constraint can be added later).
	 */
	public SedimentThicknessParam() {this(null);}

	/**
	 * This uses the given default and warning-constraint limits, and sets 
	 * everything as non-editable.
	 * @param defaultDepth
	 * @param warnMin
	 * @param warnMax
	 * @param nullAllowed - tells whether null values are to be allowed
	 */
	public SedimentThicknessParam(Double defaultThickness, double warnMin, double warnMax, boolean nullAllowed) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNullAllowed(nullAllowed);
		getConstraint().setNonEditable();
		setInfo(INFO);
		setDefaultValue(defaultThickness);
		DoubleConstraint warn = new DoubleConstraint(warnMin, warnMax);
		setWarningConstraint(warn);
		warn.setNonEditable();
		setNonEditable();
	}
	
	/**
	 * This sets default as 1.0, uses the given warning-constraint limits, and sets 
	 * everything as non-editable.
	 * @param warnMin
	 * @param warnMax
	 * @param nullAllowed - tells whether null values are to be allowed
	 */
	public SedimentThicknessParam(double warnMin, double warnMax, boolean nullAllowed) {
		this(null,warnMin,warnMax,nullAllowed);
	}
}
