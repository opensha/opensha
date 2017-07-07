package org.opensha.nshmp2.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/**
 * Identifier for different site types.
 * 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum SiteType {

	// TODO clean up, make comprehensive, add corresponding Vs30 values if
	// possible
	FIRM_ROCK,
	HARD_ROCK;

	// lifted from Campbell 2003
//	FIRM_SOIL,
//	VERY_FIRM_SOIL,
//	SOFT_ROCK,
//	GENERIC_SOIL,
//	GENERIC_ROCK,
//	NEHRP_BC;

	@Override
	public String toString() {
		return WordUtils.capitalize(StringUtils
			.replaceChars(name(), '_', ' ').toLowerCase());
	}
}
