package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * 
 */
public class ApplyGardnerKnopoffAftershockFilterParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply Aftershock Filter";
	public final static String INFO = "Applies the Gardner Knopoff aftershock filter";

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ApplyGardnerKnopoffAftershockFilterParam(boolean defaultValue) {
		super(NAME);
		setInfo(INFO);
		setDefaultValue(defaultValue);
	}
	
	/**
	 * This sets the default value as "false", and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ApplyGardnerKnopoffAftershockFilterParam() {this(false);}

}
