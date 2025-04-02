package org.opensha.sha.faultSurface;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.util.NSHMP_Util.DistanceCorrection2008;

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
		if (Precision.equals(rJB,  0d, 0.0001))
			// rJB == 0: inside the surface projection, assume halfway away from trace
			return 0.5*horzWidth;
		return footwall ? -rJB : rJB + horzWidth;
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		if (Precision.equals(length, 0d, 0.001) && Precision.equals(horzWidth, 0d, 0.001))
			// zero length and width, bypass any distance corrections
			return LocationUtils.horzDistanceFast(getLocation(), siteLoc);
		return super.getDistanceJB(siteLoc);
	}

	@Override
	public double getDistanceRup(Location loc) {
		double rJB = getDistanceJB(loc);

		return getDistanceRup(rJB);
	}

	public double getDistanceRup(double rJB) {
		if (distCorr instanceof DistanceCorrection2008 || distCorr instanceof DistanceCorrection2013)
			// use the old (but buggy) rRup calculation to be consistent with the old NSHM distance corrections
			return getCorrDistRupNSHM13(rJB, zTop, zBot, dipRad, horzWidth, footwall);
		return getCorrDistRup(rJB, zTop, zBot, dipRad, horzWidth, footwall);
	}
	
	@Override
	public double getDistanceSeis(Location loc) {
		double rJB = getDistanceJB(loc);

		double zTop = Math.max(GriddedSurfaceUtils.SEIS_DEPTH, this.zTop);
		double zBot = Math.max(GriddedSurfaceUtils.SEIS_DEPTH, this.zBot);
		if (distCorr instanceof DistanceCorrection2008 || distCorr instanceof DistanceCorrection2013)
			// use the old (but buggy) rRup calculation to be consistent with the old NSHM distance corrections
			return getCorrDistRupNSHM13(rJB, zTop, zBot, dipRad, horzWidth, footwall);
		return getCorrDistRup(rJB, zTop, zBot, dipRad, horzWidth, footwall);
	}

	private static final double PI_HALF = Math.PI/2d; // 90 degrees
	
	private static double ROOT_TWO_OVER_TWO = Math.sqrt(2)/2;
	
	public static double getCorrDistRupNSHM13(double rJB, double zTop, double zBot, double dipRad, double horzWidth, boolean footwall) {
		// this is the (buggy) distance correction used by the USGS NSHM in 2013 and at least through 2023; it is labled
		// 2013 because it was implemented in OpenSHA for the 2013 update.
		// It can return unphysical values, e.g., where rRup < zTop. See https://github.com/opensha/opensha/issues/124
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

	public static double getCorrDistRup(double rJB, double zTop, double zBot, double dipRad, double horzWidth, boolean footwall) {
		// special cases
		if (Precision.equals(dipRad,  PI_HALF, 0.0001) || horzWidth < 0.0001) {
			// vertical: the upper edge of the rupture is by definition the closest point to the site
			return hypot2(rJB, zTop);
//		} else if (rJB == 0d) {
		} else if (Precision.equals(rJB,  0d, 0.0001)) {
			// special case: site is within the surface projection of (directly above) the rupture
			
			// we don't know if it's directly over the top edge, bottom edge, or somewhere in-between
			// approximate it as if we're directly over the middle of the rupture; that should capture
			// average behaviour
			
			// c=0.5 indicates middle, but this works for other fractions as well
			double c = 0.5;
			double xSite = c * horzWidth;
			double zSite = 0.0;

			// segment direction
			double dx = horzWidth;
			double dz = (zBot - zTop);

			// vector from top edge => site
			double vx = xSite - 0.0; // = cW
			double vz = zSite - zTop; // = 0 - zTop => -zTop

			// dot products
			double denom = dx*dx + dz*dz; 
			double dot   = vx*dx + vz*dz;
			double tStar = dot / denom;

			// clamp
			if (tStar < 0.0) tStar = 0.0;
			if (tStar > 1.0) tStar = 1.0;

			// nearest point
			double xFault = dx * tStar;
			double zFault = zTop + dz * tStar;

			double dxSite = xSite - xFault;
			double dzSite = zSite - zFault;

			return hypot2(dxSite, dzSite);
		} else if (footwall) {
			// special case: on the footwall meaning the upper edge of the rupture is by definition the closest point to the site
			return hypot2(rJB, zTop);
		}
		
		// if we're here, we're on the hanging wall and rJB>0
		// approximate the calculation assuming different angles; we'll use three locations:
		/*
		 * map view schematic; legend:
		 * G: grid node center
		 * ||: rupture upper edge
		 * |: rupture lower edge
		 * A: site perfectly along-strike of the rupture, and rJB away from the surface projection of the rupture
		 * B: site is somewhere off the end of the fault, and also past the bottom edge
		 * C: site perfectly down-dip of the rupture, and also rJB away from the surface projection of the rupture
		 * *: origin where x=0 and y=0 when we're doing the site A calculation
		 * .: origin where x=0 and y=0 when we're doing the site B calculation
		 * ^: origin where x=0 and y=0 when we're doing the site C calculation
		 * 
		 * 
		 *       A          
		 *                  B
		 * 
		 *   ____*____.
		 * ||         |
		 * ||         |
		 * ||         |
		 * I|    G    ^       C
		 * ||         |
		 * ||         |
		 * ||_________|
		 * 
		 */
		
		// first calculate as though we're in the perfectly on-strike direction (the trace points perfectly toward or away from us)
		// that's site 'A' in the schematic above
		// in that case, we need the distance to the "front" edge of the rupture
		// lets assume that the rupture is centered down-dip about the grid node (G)
		double halfWidth = 0.5*horzWidth;
		// define the origin as location '*' in the graph above, directly above the middle of the front edge 
		// site A is then is at (0, rJB)
		// front edge is along the x axis between:
		//	(-halfWidth, 0, zTop) and (halfWidth, 0, zBot)
		double alongStrikeDist = distanceToLineSegment3D(0d, rJB, -halfWidth, 0, zTop, halfWidth, 0, zBot);
		
		// now calculate for site B where we're off the end and past the bottom
		// define the origin as location '.' in the graph above, directly above the bottom-front corner
		// define rJB' = rJB*sqrt(2)/2
		// site B is then at (rJB', rJB')
		// front edge is along the x axis between:
		//  (-horzWidth, 0, zTop) and (0, 0, zBot)
		double rJBprime = ROOT_TWO_OVER_TWO * rJB;
		double offCornerDist = distanceToLineSegment3D(rJBprime, rJBprime, -horzWidth, 0, zTop, 0, 0, zBot);
		
		// now calculate as though we're in the perfectly down-dip direction
		// define the origin as location '^' in the graph above, directly above the middle of the bottom edge
		// site C is then at (rJB, 0)
		// we'll define a line down-dip of the fault from 'I' to '^':
		//	  (-horzWidth, 0, zTop) to (0, 0, zBot)
		double downDipDist = distanceToLineSegment3D(rJB, 0, -horzWidth, 0, zTop, 0, 0, zBot);
		
		
		// split the difference between them
		// weight the off-corner distance higher because it's really in charge of half of the range of angles 
		return 0.25*alongStrikeDist + 0.5*offCornerDist + 0.25*downDipDist;
		// alternatively you could just use the offCornerDist as an approximation; it does better with the 3 though
//		return offCornerDist;
	}
	
	/**
	 * 3D distance from location on surface at (px, py) to the line segment
	 * between points (ax, ay, az) and (bx, by, bz)
	 * @param px
	 * @param py
	 * @param ax
	 * @param ay
	 * @param az
	 * @param bx
	 * @param by
	 * @param bz
	 * @return Euclidean distance
	 */
	public static double distanceToLineSegment3D(double px, double py, 
			double ax, double ay, double az, 
			double bx, double by, double bz) {
		// Vector AB
		double abX = bx - ax;
		double abY = by - ay;
		double abZ = bz - az;

		// Vector AP (P is the surface point, so Pz = 0)
		double apX = px - ax;
		double apY = py - ay;
		double apZ = -az; // Since Pz = 0, we get APz = 0 - Az

		// Compute dot products
		double abDotAb = abX * abX + abY * abY + abZ * abZ;
		double apDotAb = apX * abX + apY * abY + apZ * abZ;

		// Projection scalar
		double t = apDotAb / abDotAb;

		// Clamp t to the segment [0,1]
		t = Math.max(0, Math.min(1, t));

		// Compute closest point C on segment
		double cx = ax + t * abX;
		double cy = ay + t * abY;
		double cz = az + t * abZ;

		// Compute Euclidean distance from P to C
		double dx = px - cx;
		double dy = py - cy;
		double dz = -cz; // Since Pz = 0, distance in z is just -Cz

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
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