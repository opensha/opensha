package org.opensha.sha.faultSurface.utils;

import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;

public interface PointSourceDistanceCorrection {
	
	/**
	 * This returns the corrected Joyner-Boore distance (rJB; shortest horizontal distance to the surface projection
	 * of rupture) for a point source at the given horizontal site-source distance. Units are kilometers.
	 * 
	 * @param rup rupture, used to get properties such as magnitude for mag-depdendent calculations
	 * @param horzDist horizontal distance between the site location and rupture epicenter (km)
	 * @return corrected distance JB for the given rupture at the given horizontal site-to-epicenter distance (km)
	 */
	public double getCorrectedDistanceJB(EqkRupture rup, double horzDist);
	
	/**
	 * A {@link PointSourceDistanceCorrection} paired with an {@link EqkRupture}, for use by an {@link PointSurface}.
	 */
	public static class RuptureSpecificCorrection {
		
		public final EqkRupture rupture;
		public final PointSourceDistanceCorrection correction;
		
		public RuptureSpecificCorrection(EqkRupture rupture, PointSourceDistanceCorrection correction) {
			this.rupture = rupture;
			this.correction = correction;
		}
		
		/**
		 * This returns the corrected Joyner-Boore distance (rJB; shortest horizontal distance to the surface projection
		 * of rupture) for a point source at the given horizontal site-source distance. Units are kilometers.
		 * 
		 * @param horzDist horizontal distance between the site location and rupture epicenter (km)
		 * @return corrected distance JB for the given rupture at the given horizontal site-to-epicenter distance (km)
		 */
		public double getCorrectedDistanceJB(double horzDist) {
			return correction.getCorrectedDistanceJB(rupture, horzDist);
		}
	}

}
