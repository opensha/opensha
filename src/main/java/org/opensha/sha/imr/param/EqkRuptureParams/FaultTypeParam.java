package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.StringParameter;

/**
 * FaulltTypeParam, a StringParameter for representing different
 * styles of faulting.  The options are not specified here because
 * nomenclature generally differs among subclasses.  The default must
 * also be specified in the constructor.
 * See constructors for info on editability and default values.
 */

public class FaultTypeParam extends StringParameter {

	public final static String NAME = "Fault Type";
	public final static String INFO = "Style of faulting";

	/**
	 * This sets the parameter as non-editable
	 * @param options
	 * @param defaultValue
	 */
	public FaultTypeParam(StringConstraint options, String defaultValue) {
		super(NAME, options);
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    this.setNonEditable();
	}
}
