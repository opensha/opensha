package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.WrappedRuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;

/**
 * Overrides the cache of the given surface to avoid contention in multithreaded applications
 * 
 * @author kevin
 *
 */
public class CustomCacheWrappedSurface extends WrappedRuptureSurface implements CacheEnabledSurface {

	private CacheEnabledSurface surf;
	
	private SurfaceDistanceCache cache;

	public CustomCacheWrappedSurface(CacheEnabledSurface surf) {
		this(surf, null);
	}

	public CustomCacheWrappedSurface(CacheEnabledSurface surf, CacheTypes type) {
		super(surf);
		this.surf = surf;
		if (type == null)
			cache = SurfaceCachingPolicy.build(this);
		else
			cache = SurfaceCachingPolicy.build(this, type);
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		return surf.calcDistances(loc);
	}

	@Override
	public double calcQuickDistance(Location loc) {
		return surf.calcQuickDistance(loc);
	}

	@Override
	public double calcDistanceX(Location loc) {
		return surf.calcDistanceX(loc);
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	@Override
	public double getDistanceSeis(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		return cache.getDistanceX(siteLoc);
	}

}
