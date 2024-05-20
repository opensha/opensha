package org.opensha.sha.imr.param.SiteParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Vs30 Parameter, reserved for representing the average shear-wave velocity
 * in the upper 30 meters of a site (a commonly used parameter).  The warning 
 * constraint must be created and added when instantiated.
 * See constructors for info on editability and default values.
 */
public class Vs30_Param extends WarningDoubleParameter {

	public final static String NAME = "Vs30";
	public final static String UNITS = "m/sec";
	public final static String INFO = "The average shear-wave velocity between 0 and 30-meters depth";
//	public final static Double DEFAULT = Double.valueOf("760");
	protected final static Double MIN = Double.valueOf(0.0);
	protected final static Double MAX = Double.valueOf(5000.0);

	/**
	 * This constructor sets the default value as given and leaves the param editable so
	 * the warning constraint can be added.
	 */
	public Vs30_Param(double defaultValue) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setDefaultValue(defaultValue);
	}

	/**
	 * This constructor sets the default value as 760 m/sec and leaves the param editable so
	 * the warning constraint can be added.
	 */
	public Vs30_Param() {this(760);}

	/**
	 * This constructor sets the default and warning constraint as given, 
	 * and sets everything as non-editable.
	 * @param warnMin
	 * @param warnMax
	 */
	public Vs30_Param(double defaultValue, double warnMin, double warnMax) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    DoubleConstraint warn = new DoubleConstraint(warnMin, warnMax);
	    setWarningConstraint(warn);
	    warn.setNonEditable();
	    setNonEditable();
	}
	
	/**
	 * This constructor sets the default as 760 m/sec, the warning constraint 
	 *  as given, and sets everything as non-editable.
	 * @param warnMin
	 * @param warnMax
	 */
	public Vs30_Param(double warnMin, double warnMax) {this(760.0,warnMin, warnMax);}


}
