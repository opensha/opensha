package org.opensha.sha.imr.param.SiteParams;

import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.StringParameter;

/**
 *  Vs flag Parameter - indicates whether vs was measured or inferred/estimated,
 *  or as other categories if the user provides them.
 *  See constructors for info on editability and default values.
 */

public class Vs30_TypeParam extends StringParameter {

	public final static String NAME = "Vs30 Type";
	public final static String INFO = "Indicates how Vs30 was obtained";
	// Options for constraint:
	public final static String VS30_TYPE_MEASURED = "Measured";
	public final static String VS30_TYPE_INFERRED = "Inferred";

	/**
	 * This provides maximum flexibility in terms of setting the options 
	 * and the default.  The parameter is left as non editable.
	 */
	public Vs30_TypeParam(StringConstraint options, String defaultValue) {
		super(NAME, options);
	    this.setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setNonEditable();
	}
	
	/**
	 * This sets the options as given in the fields here, and sets the default
	 * as VS30_TYPE_INFERRED.  Thge parameter is left as non editable.
	 */
	public Vs30_TypeParam() {
		super(NAME);
		StringConstraint options = new StringConstraint();
		options.addString(VS30_TYPE_MEASURED);
		options.addString(VS30_TYPE_INFERRED);
		options.setNonEditable();
		setValue(VS30_TYPE_INFERRED); // need to do this so next line succeeds
		setConstraint(options);
	    setInfo(INFO);
	    setDefaultValue(VS30_TYPE_INFERRED);
	    setNonEditable();
	}
}
