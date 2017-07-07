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
public class BackgroundRupParam extends EnumParameter<BackgroundRupType> {
	
	public static final String NAME = "Treat Background Seismicity As";

	public BackgroundRupParam() {
		super(NAME, EnumSet
			.allOf(BackgroundRupType.class), BackgroundRupType.POINT, null);
	}

}
