package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class IncludeBackgroundParam extends EnumParameter<IncludeBackgroundOption> {
	
	public static final String NAME = "Background Seismicity";
	
	public IncludeBackgroundParam() {
		super(NAME, EnumSet
			.allOf(IncludeBackgroundOption.class),
			IncludeBackgroundOption.EXCLUDE, null);
	}

}
