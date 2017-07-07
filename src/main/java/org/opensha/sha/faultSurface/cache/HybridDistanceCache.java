package org.opensha.sha.faultSurface.cache;

import java.util.concurrent.TimeUnit;

import org.opensha.commons.geo.Location;

/**
 * Hybrid of {@link SingleLocDistanceCache} and {@link MultiDistanceCache}. Will check single first, then delegate to multi cache.
 * This maintains the single threaded performance of the single cache, while improving performance of the multi cache. Typical
 * UCERF3 hazard calculation speedups when compared to the single cache are 0% (no improvement) for 1 thread, and 25% for 
 * threads > 1. 
 * @author kevin
 *
 */
public class HybridDistanceCache implements SurfaceDistanceCache {
	
	private SingleLocDistanceCache singleCache;
	private MultiDistanceCache multiCache;
	
	public HybridDistanceCache(CacheEnabledSurface surf) {
		this(surf, Runtime.getRuntime().availableProcessors()+5);
	}
	
	public HybridDistanceCache(CacheEnabledSurface surf, int maxSize) {
		this(surf, maxSize, 0, TimeUnit.HOURS);
	}
	
	public HybridDistanceCache(CacheEnabledSurface surf, int maxSize, long expirationTime, TimeUnit expirationUnit) {
		singleCache = new SingleLocDistanceCache(surf);
		multiCache = new MultiDistanceCache(surf, maxSize, expirationTime, expirationUnit);
	}

	@Override
	public SurfaceDistances getSurfaceDistances(Location loc) {
		SurfaceDistances surfDists = singleCache.getSurfaceDistancesIfPresent(loc);
		if (surfDists != null)
			return surfDists;
		// not in single cache, get from multi cache (load if necessary)
		surfDists = multiCache.getSurfaceDistances(loc);
		// put in single cache
		singleCache.putSurfaceDistances(loc, surfDists);
		return surfDists;
	}

	@Override
	public double getDistanceX(Location loc) {
		Double distX = singleCache.getDistanceXIfPresent(loc);
		if (distX != null)
			return distX;
		// not in single cache, get from multi cache (load if necessary)
		distX = multiCache.getDistanceX(loc);
		// put in single cache
		singleCache.putDistanceX(loc, distX);
		return distX;
	}

}
