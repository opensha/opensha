package org.opensha.sha.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log Peak Ground Acceleration intensity measure
 * parameter.  
 * See constructors for info on editability and default values.
 * @author field
 *
 */
public class MMI_Param extends DoubleParameter {

	public final static String NAME = "MMI";
	public final static String INFO = "Peak Ground Acceleration";
	public final static Double MIN = new Double(Math.log(1.0));
	public final static Double MAX = new Double(Math.log(10.0));
	public final static Double DEFAULT = new Double(Math.log(5.0));
	public final static String MMI_ERROR_STRING = "Problem:  cannot " +
			"complete\n the requested computation for MMI.\n\n" +
			"This has occurred because you attempted to compute the\n" +
			"standard deviation (or something else such as probability \n" +
			"of exceedance which depends on the standard deviation).  \n" +
			"The inability to compute these will remain until someone comes up\n" +
			"with the probability distribution for MMI (when computed from\n" +
			"PGA or PGV).  For now you can compute the median or the\n" +
			"IML that has exactly a 0.5 chance of being exceeded (assuming\n" +
			"this application supports such computations).\n";

	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(1.0) (the natural
	 * log of 1.0).
	 * The parameter is left as non editable
	 */
	public MMI_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX));
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(DEFAULT);
	    setNonEditable();
	}
}
