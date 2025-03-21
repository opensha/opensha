package org.opensha.sha.earthquake.param;

import org.opensha.sha.earthquake.util.GriddedFiniteRuptureSettings;

/**
 * Rupture type for background events.
 * 
 * Note: previously there were separate entries for FINITE (single) and CROSSHAIR (2), now it's just a single FINITE
 * enum choice and settings therein are handled by {@link GriddedFiniteRuptureSettings}
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum BackgroundRupType {
	POINT("Point sources"),
	FINITE("Random-strike faults");
	
	private String label;
	private BackgroundRupType(String label) {
		this.label = label;
	}
	
	@Override public String toString() {
		return label;
	}

}
