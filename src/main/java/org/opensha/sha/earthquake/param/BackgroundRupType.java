package org.opensha.sha.earthquake.param;

/**
 * Rupture type for background events.
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum BackgroundRupType {
	POINT("Point Sources"),
	FINITE("Single Random Strike Faults"),
	CROSSHAIR("Two Perpendicular Faults");
	
	private String label;
	private BackgroundRupType(String label) {
		this.label = label;
	}
	
	@Override public String toString() {
		return label;
	}

}
