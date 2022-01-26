package org.opensha.sha.imr.param.EqkRuptureParams;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * Aftershock parameter, indicates whether or not an event is an aftershock.
 * See constructors for info on editability and default values.
 */
public class AftershockParam extends BooleanParameter {

	public final static String NAME = "Aftershock";
	public final static String INFO = "Indicates whether earthquake is an aftershock";

	/**
	 * This constructor sets the default value as "false".
	 * This also makes the parameter non editable.
	 */
	public AftershockParam(boolean defaultValue) {
		super(NAME);
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setNonEditable();
	}
	/**
	 * This constructor sets the default value as "false".  
	 * This also makes the parameter non editable.
	 */
	public AftershockParam() { this(false); }



}
