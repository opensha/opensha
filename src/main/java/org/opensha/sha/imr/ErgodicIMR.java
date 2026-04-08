package org.opensha.sha.imr;

import org.opensha.sha.faultSurface.cache.SurfaceDistances;

/**
 * Interface for an ergodic IMR, i.e., one regressed on global or regional statistics with coefficients that do not
 * depend on specific site or source locations/paths.
 */
public interface ErgodicIMR extends ScalarIMR {

	/**
	 * This method sets the propagation effect parameters for the given distances.
	 * 
	 * This is helpful because it allows to override distances for a point source distance correction without
	 * also resetting the earthquake rupture object.
	 * @param distances
	 */
	public void setPropagationEffectParams(SurfaceDistances distances);

}
