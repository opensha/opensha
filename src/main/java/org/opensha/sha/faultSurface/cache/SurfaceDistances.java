package org.opensha.sha.faultSurface.cache;

/**
 * Container for 2D/3D surface distances, usually all calculated at the same time. Used for caching.
 * <br><br>
 * This class can be extended if extra information is needed by a surface.
 * 
 * @author kevin
 *
 */
public class SurfaceDistances {
	private final double distanceRup, distanceJB, distanceSeis;

	public SurfaceDistances(double distanceRup, double distanceJB,
			double distanceSeis) {
		super();
		this.distanceRup = distanceRup;
		this.distanceJB = distanceJB;
		this.distanceSeis = distanceSeis;
	}

	public double getDistanceRup() {
		return distanceRup;
	}

	public double getDistanceJB() {
		return distanceJB;
	}

	public double getDistanceSeis() {
		return distanceSeis;
	}
}
