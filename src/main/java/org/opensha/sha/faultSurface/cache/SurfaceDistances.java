package org.opensha.sha.faultSurface.cache;

import java.util.function.Function;

import org.opensha.commons.geo.Location;

/**
 * Container for 2D/3D surface distances, usually all calculated at the same time. Used for caching.
 * <br><br>
 * This class can be extended if extra information is needed by a surface.
 * 
 * @author kevin
 *
 */
public interface SurfaceDistances {
	
	public Location getSiteLocation();

	public double getDistanceRup();

	public double getDistanceJB();
	
	public double getDistanceX();
	
	/**
	 * {@link SurfaceDistances} implementation where all values are precomputed
	 */
	public static class Precomputed implements SurfaceDistances {
		private final Location siteLoc;
		private final double distanceRup, distanceJB, distanceX;
		
		public Precomputed(Location siteLoc, double distanceRup, double distanceJB,
				double distanceX) {
			super();
			this.siteLoc = siteLoc;
			this.distanceRup = distanceRup;
			this.distanceJB = distanceJB;
			this.distanceX = distanceX;
		}

		@Override
		public Location getSiteLocation() {
			return siteLoc;
		}

		@Override
		public double getDistanceRup() {
			return distanceRup;
		}

		@Override
		public double getDistanceJB() {
			return distanceJB;
		}

		@Override
		public double getDistanceX() {
			return distanceX;
		}
	}
	
	/**
	 * {@link SurfaceDistances} implementation where all values are precomputed except for DistanceX, which is lazily
	 * initialized on first request.
	 */
	public static class PrecomputedLazyX implements SurfaceDistances {
		private final Location siteLoc;
		private final double distanceRup, distanceJB;
		private volatile Double distanceX;
		private final Function<Location, Double> distanceXCalc;
		
		public PrecomputedLazyX(Location siteLoc, double distanceRup, double distanceJB,
				Function<Location, Double> distanceXCalc) {
			super();
			this.siteLoc = siteLoc;
			this.distanceRup = distanceRup;
			this.distanceJB = distanceJB;
			this.distanceXCalc = distanceXCalc;
		}

		@Override
		public Location getSiteLocation() {
			return siteLoc;
		}

		@Override
		public double getDistanceRup() {
			return distanceRup;
		}

		@Override
		public double getDistanceJB() {
			return distanceJB;
		}

		@Override
		public double getDistanceX() {
			if (distanceX == null)
				distanceX = distanceXCalc.apply(siteLoc);
			return distanceX;
		}
	}
}
