package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;

/**
 * This can be used to test performance without any caching, but should never be used in production
 * @author kevin
 *
 */
class DisabledDistanceCache implements SurfaceDistanceCache {
	
	private CacheEnabledSurface surf;
	
	public DisabledDistanceCache(CacheEnabledSurface surf) {
		this.surf = surf;
	}

	@Override
	public synchronized SurfaceDistances getSurfaceDistances(Location loc) {
		return surf.calcDistances(loc);
	}

	@Override
	public void clearCache() {
		// do nothing
	}

	@Override
	public double getQuickDistance(Location loc) {
		return surf.calcQuickDistance(loc);
	}

}
