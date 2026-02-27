package org.opensha.sha.faultSurface.utils;

import org.opensha.sha.faultSurface.PointSurface;

public interface PointSourceDistanceCorrection {
	
	/**
	 * This returns the corrected Joyner-Boore distance (rJB; shortest horizontal distance to the surface projection
	 * of rupture) for a point source at the given horizontal site-source distance. Units are kilometers.
	 * 
	 * @param mag rupture magnitude
	 * @param surf rupture surface
	 * @param horzDist horizontal distance between the site location and rupture epicenter (km)
	 * @return corrected distance JB for the given rupture at the given horizontal site-to-epicenter distance (km)
	 */
	public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist);

}
