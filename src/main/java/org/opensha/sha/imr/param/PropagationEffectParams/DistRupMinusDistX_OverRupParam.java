package org.opensha.sha.imr.param.PropagationEffectParams;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * DistRupMinusDistX_OverRupParam - this represents distance X (relative to  
 * dist rup, or specifically: (DistRup-DistX)/DistRup), where distance 
 * X is the horizontal distance to surface projection of the 
 * top edge of the rupture, extended to infinity off the ends.  
 * This is not a formal propagation parameter because it's not used that way
 * (due to inefficiencies)
 * See constructors for info on editability and default values.
 */
public class DistRupMinusDistX_OverRupParam extends DoubleParameter {

	public final static String NAME = "(distRup-distX)/distRup";
	public final static String INFO = "(DistanceRup - DistanceX)/DistanceRup";
	public final static Double MIN = new Double(Double.NEGATIVE_INFINITY);
	public final static Double MAX = new Double(Double.POSITIVE_INFINITY);
//	public final static Double DEFAULT = new Double(0.0);

	/**
	 * This sets the default value as given, and sets the parameter as
	 * non editable.
	 */
	public DistRupMinusDistX_OverRupParam(double defaultValue) {
		super(NAME, MIN, MAX);
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setNonEditable();
	}

	/**
	 * This sets the default value as 0.0, and sets the parameter as
	 * non editable.
	 */
	public DistRupMinusDistX_OverRupParam() {this(0.0);}
}
