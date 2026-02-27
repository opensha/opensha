package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;

/**
 * Simple {@link SurfaceDistanceCache} implementation that stores a single location/value, works well in
 * single threaded environments but has many collisions in multithreaded calculations.
 * 
 * Updated in 2024 to remove synchronization bottleneck; by storing the cached values in a wrapper object along with the
 * location, threads can safely get the current value without worrying about another thread updating it
 * @author kevin
 *
 */
public class SingleLocDistanceCache implements SurfaceDistanceCache {
	
	private CacheEnabledSurface surf;
	
	private static class LocDistCache<E> {
		final Location loc;
		final E value;
		private LocDistCache(Location loc, E value) {
			super();
			this.loc = loc;
			this.value = value;
		}
	}
	
	private LocDistCache<SurfaceDistances> surfDists;
	
	private LocDistCache<Double> quickDist;
	
	private LocDistCache<Double> distX;
	
	public SingleLocDistanceCache(CacheEnabledSurface surf) {
		this.surf = surf;
	}

	@Override
	public SurfaceDistances getSurfaceDistances(Location loc) {
		LocDistCache<SurfaceDistances> cached = surfDists;
		if (cached == null || !cached.loc.equals(loc)) {
			cached = new LocDistCache<SurfaceDistances>(loc, surf.calcDistances(loc));
			surfDists = cached;
		}
		return cached.value;
	}

	@Override
	public double getQuickDistance(Location loc) {
		LocDistCache<Double> cached = quickDist;
		if (cached == null || !cached.loc.equals(loc)) {
			cached = new LocDistCache<Double>(loc, surf.calcQuickDistance(loc));
			quickDist = cached;
		}
		return cached.value;
	}

	@Override
	public double getDistanceX(Location loc) {
		LocDistCache<Double> cached = distX;
		if (cached == null || !cached.loc.equals(loc)) {
			cached = new LocDistCache<Double>(loc, surf.calcDistanceX(loc));
			distX = cached;
		}
		return cached.value;
	}
	
	SurfaceDistances getSurfaceDistancesIfPresent(Location loc) {
		LocDistCache<SurfaceDistances> cached = surfDists;
		if (cached != null && cached.loc.equals(loc))
			return cached.value;
		return null;
	}
	
	Double getQuickDistanceIfPresent(Location loc) {
		LocDistCache<Double> cached = quickDist;
		if (cached != null && cached.loc.equals(loc))
			return cached.value;
		return null;
	}
	
	Double getDistanceXIfPresent(Location loc) {
		LocDistCache<Double> cached = distX;
		if (cached != null && cached.loc.equals(loc))
			return cached.value;
		return null;
	}
	
	void putSurfaceDistances(Location loc, SurfaceDistances dists) {
		this.surfDists = new LocDistCache<>(loc, dists);
	}
	
	void putQuickDistance(Location loc, double quickDistance) {
		this.quickDist = new LocDistCache<>(loc, quickDistance);
	}
	
	void putDistanceX(Location loc, double distX) {
		this.distX = new LocDistCache<>(loc, distX);
	}

	@Override
	public void clearCache() {
		this.surfDists = null;
		this.quickDist = null;
		this.distX = null;
	}

}
