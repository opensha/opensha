package org.opensha.sha.imr.param.PropagationEffectParams;

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * HangingWallFlagParam parameter - indicates whether a site is 
 * on the hanging wall of a rupture surface.  Exact definition and setting 
 * of value must be handled in the implementing class.
 * See constructors for info on editability and default values.
 */
public class HangingWallFlagParam extends BooleanParameter {

	public final static String NAME = "Site on Hanging Wall";
	public final static String INFO = "Indicates whether the site is on the hanging wall";

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public HangingWallFlagParam(boolean defaultValue) {
		super(NAME);
		setInfo(INFO);
		setDefaultValue(defaultValue);
	}
	
	/**
	 * This sets the default value as "false", and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public HangingWallFlagParam() {this(false);}

}
