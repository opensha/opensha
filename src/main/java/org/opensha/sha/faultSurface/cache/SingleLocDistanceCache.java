package org.opensha.sha.faultSurface.cache;

import org.opensha.commons.geo.Location;

/**
 * Simple {@link SurfaceDistanceCache} implementation that stores a single location/value, works well in
 * single threaded environments but has many collisions in multithreaded calculations.
 * @author kevin
 *
 */
public class SingleLocDistanceCache implements SurfaceDistanceCache {
	
	private CacheEnabledSurface surf;
	
	private Location siteLocForDistCalcs;
	private SurfaceDistances surfDists;
	
	private Location siteLocForDistXCalc;
	private double distX;
	
	public SingleLocDistanceCache(CacheEnabledSurface surf) {
		this.surf = surf;
	}

	@Override
	public synchronized SurfaceDistances getSurfaceDistances(Location loc) {
		if (siteLocForDistCalcs == null || !siteLocForDistCalcs.equals(loc)) {
			surfDists = surf.calcDistances(loc);
			siteLocForDistCalcs = loc;
		}
		return surfDists;
	}

	@Override
	public synchronized double getDistanceX(Location loc) {
		if (siteLocForDistXCalc == null || !siteLocForDistXCalc.equals(loc)) {
			distX = surf.calcDistanceX(loc);
			siteLocForDistXCalc = loc;
		}
		return distX;
	}
	
	synchronized SurfaceDistances getSurfaceDistancesIfPresent(Location loc) {
		if (loc.equals(siteLocForDistCalcs))
			return surfDists;
		return null;
	}
	
	synchronized Double getDistanceXIfPresent(Location loc) {
		if (loc.equals(siteLocForDistXCalc))
			return distX;
		return null;
	}
	
	synchronized void putSurfaceDistances(Location loc, SurfaceDistances dists) {
		this.siteLocForDistCalcs = loc;
		this.surfDists = dists;
	}
	
	synchronized void putDistanceX(Location loc, double distX) {
		this.siteLocForDistXCalc = loc;
		this.distX = distX;
	}

}
