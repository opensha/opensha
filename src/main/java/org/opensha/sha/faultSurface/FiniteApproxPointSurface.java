package org.opensha.sha.faultSurface;

import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;

/**
 * Point surface implementation that approximates finite surfaces when calculating 3-D distances (e.g., rRup and
 * rSeis). It relies on the {@link PointSourceDistanceCorrection} setting to calculate overall distance corrections
 * (applied to rJB).
 * 
 * Sign of rX is set according to the passed in footwall boolean.
 * 
 * Based on the now-deleted PointSurface13b.
 * 
 * NOTE: This, like the now-deleted PointSurface13b and PointSurfaceNshm, seems to have a bug in calculating Rrup.
 * See issue #121
 */
public class FiniteApproxPointSurface extends PointSurface {
	
	// inputs
	private double zTop;
	private double zBot;
	private boolean footwall;
	private double length;
	
	// calculated
	private double dipRad;
	private double horzWidth;

	public FiniteApproxPointSurface(Location loc, double dip, double zTop, double zBot, boolean footwall,
			double length) {
		super(loc);
		this.aveDip = dip;
		this.zTop = zTop;
		this.zBot = zBot;
		this.footwall = footwall;
		this.length = length;
		
		dipRad = Math.toRadians(dip);
		calcWidths();
	}
	
	private void calcWidths() {
		if (aveDip == 90d || zBot == zTop) {
			aveWidth = zBot - zTop;
			horzWidth = 0d;
		} else {
			aveWidth = (zBot-zTop)/Math.sin(dipRad);
			horzWidth = aveWidth * Math.cos(dipRad);
		}
	}

	@Override
	public double getAveRupTopDepth() {
		return getDepth();
	}

	@Override
	public double getDepth() {
		// overridden to not key depth to point location
		return zTop;
	}
	
	public double getLowerDepth() {
		return zBot;
	}

	@Override
	public void setDepth(double depth) {
		// overridden to not cause creation of new Location in parent
		zTop = depth;
		// recalculate widths
		calcWidths();
	}

	@Override
	public void setAveDip(double aveDip) throws InvalidRangeException {
		super.setAveDip(aveDip);
		// recalculate widths
		calcWidths();
	}

	@Override
	public void setAveWidth(double aveWidth) {
		throw new UnsupportedOperationException("Width is calculated, cannot be set");
	}

	@Override
	public double getAveLength() {
		return length;
	}

	@Override
	public double getArea() {
		return getAveLength() * getAveWidth();
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		if (region.contains(getLocation()))
			return getArea();
		return 0d;
	}
	
	@Override
	public double getDistanceX(Location loc) {
		double rJB = getDistanceJB(loc);
		return footwall ? -rJB : rJB + horzWidth;
	}

	@Override
	public double getDistanceRup(Location loc) {
		double rJB = getDistanceJB(loc);

		return getDistanceRup(rJB);
	}

	public double getDistanceRup(double rJB) {
		return getCorrDistRup(rJB, zTop, zBot, dipRad, horzWidth, footwall);
	}
	
	@Override
	public double getDistanceSeis(Location loc) {
		double rJB = getDistanceJB(loc);

		return getCorrDistRup(rJB, Math.max(GriddedSurfaceUtils.SEIS_DEPTH, zTop),
				Math.max(GriddedSurfaceUtils.SEIS_DEPTH, zBot), dipRad, horzWidth, footwall);
	}

	public static double getCorrDistRup(double rJB, double zTop, double zBot, double dipRad, double horzWidth, boolean footwall) {
		if (footwall) return hypot2(rJB, zTop);

		double rCut = zBot * Math.tan(dipRad);

		if (rJB > rCut) return hypot2(rJB, zBot);

		// rRup when rJB is 0 -- we take the minimum the site-to-top-edge
		// and site-to-normal of rupture for the site being directly over
		// the down-dip edge of the rupture
		double rRup0 = Math.min(hypot2(horzWidth, zTop), zBot * Math.cos(dipRad));
		// rRup at cutoff rJB
		double rRupC = zBot / Math.cos(dipRad);
		// scale linearly with rJB distance
		return (rRupC - rRup0) * rJB / rCut + rRup0;
	}

	public boolean isOnFootwall() {
		return footwall;
	}

	/**
	 * Same as {@code Math.hypot()} without regard to under/over flow.
	 */
	private static final double hypot2(double v1, double v2) {
		return Math.sqrt(v1 * v1 + v2 * v2);
	}

	@Override
	public FiniteApproxPointSurface copyShallow() {
		FiniteApproxPointSurface copy = new FiniteApproxPointSurface(
				getLocation(), getAveDip(), zTop, zBot, footwall, length);
		return copy;
	}
}