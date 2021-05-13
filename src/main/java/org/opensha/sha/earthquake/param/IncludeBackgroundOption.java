package org.opensha.sha.earthquake.param;

import org.apache.commons.lang3.StringUtils;


/**
 * Options for including or excluding background seismicity.
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum IncludeBackgroundOption {
	EXCLUDE,
	INCLUDE,
	ONLY;
	
	@Override public String toString() {
		return StringUtils.capitalize(StringUtils.lowerCase(name()));
	}
}
