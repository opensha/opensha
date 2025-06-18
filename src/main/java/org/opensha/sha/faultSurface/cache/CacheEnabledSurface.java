package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * {@link RuptureSurface}'s that implement this interface can easily add caching capabilities
 * @author kevin
 *
 */
public interface CacheEnabledSurface extends RuptureSurface {
	
	/**
	 * Calculates distances directly without any caching, used by a loading cache.
	 * @param loc
	 * @return
	 */
	public SurfaceDistances calcDistances(Location loc);
	
	/**
	 * Calculates the quick distance for the given location without any caching, used by a loading cache
	 * 
	 * @param loc
	 * @return
	 */
	public double calcQuickDistance(Location loc);
	
	/**
	 * Clears any cached site distances.
	 */
	public void clearCache();

}
