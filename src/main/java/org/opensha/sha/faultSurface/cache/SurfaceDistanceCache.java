package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;

/**
 * Interface for all surface distance cache implementations. Caches will always return a value. If it is not
 * present in the cache, the value will be calculated and then returned.<br><br>
 * 
 * Discussion of surface distance cache implementations and their performance can be found
 * <a href="http://opensha.usc.edu/trac/wiki/DistCaches">HERE</a>.
 * 
 * @author kevin
 *
 */
public interface SurfaceDistanceCache {
	
	/**
	 * Returns the distances for the given location, either from the cache or via calculation.
	 * 
	 * @param loc
	 * @return
	 */
	public SurfaceDistances getSurfaceDistances(Location loc);
	
	/**
	 * Returns the distance X value for the given location, either from the cache or via calculation.
	 * 
	 * @param loc
	 * @return
	 */
	public double getDistanceX(Location loc);

}
