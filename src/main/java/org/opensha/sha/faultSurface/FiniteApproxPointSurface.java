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

import com.google.common.base.Preconditions;

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
		this.zTop = zTop;
		this.zBot = zBot;
		this.footwall = footwall;
		this.length = length;
		
		setAveDip(dip);
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
		dipRad = Math.toRadians(aveDip);
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
			// by definition, this means we're on the hanging wall (even if footwall == true)
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
		return getCorrDistRup(rJB, zTop, zBot, dipRad, length, horzWidth, footwall);
	}
	
	@Override
	public double getDistanceSeis(Location loc) {
		double rJB = getDistanceJB(loc);

		double zTop = Math.max(GriddedSurfaceUtils.SEIS_DEPTH, this.zTop);
		double zBot = Math.max(GriddedSurfaceUtils.SEIS_DEPTH, this.zBot);
		if (distCorr instanceof DistanceCorrection2008 || distCorr instanceof DistanceCorrection2013)
			// use the old (but buggy) rRup calculation to be consistent with the old NSHM distance corrections
			return getCorrDistRupNSHM13(rJB, zTop, zBot, dipRad, horzWidth, footwall);
		return getCorrDistRup(rJB, zTop, zBot, dipRad, length, horzWidth, footwall);
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

		// rRup when rJB is 0 -- we take the minimum of the site-to-top-edge
		// and site-to-normal of rupture for the site being directly over
		// the down-dip edge of the rupture
		double rRup0 = Math.min(hypot2(horzWidth, zTop), zBot * Math.cos(dipRad));
		// rRup at cutoff rJB
		double rRupC = zBot / Math.cos(dipRad);
		// scale linearly with rJB distance
		return (rRupC - rRup0) * rJB / rCut + rRup0;
	}
	
	// when a dipping rupture is wide and the site is nearby, more than 50% of all possible azimuths should be on the
	// hanging wall, but we hardcode it to 50%. Enabling this will mix in some hanging wall in the rRup calculation for
	// the footwall case to help mitigate this and get more accurate average rRup calculations.
	private static final boolean R_RUP_ACCOUNT_FOR_FW_MICLASSIFICATION = true;

	public static double getCorrDistRup(double rJB, double zTop, double zBot, double dipRad, double length, double horzWidth, boolean footwall) {
		// special cases
		if (Precision.equals(dipRad,  PI_HALF, 0.0001) || horzWidth < 0.0001) {
			// vertical: the upper edge of the rupture is by definition the closest point to the site
			return hypot2(rJB, zTop);
		} else if (Precision.equals(rJB,  0d, 0.0001)) {
			// special case: site is within the surface projection of (directly above) the rupture
			
			// we don't know if it's directly over the top edge, bottom edge, or somewhere in-between
			// approximate it as if we're directly over the middle of the rupture; that should capture
			// average behavior
			
			LineSegment3D line = new LineSegment3D(0, 0, zTop, horzWidth, 0, zBot);
			return distanceToLineSegment3D(0.5*horzWidth, 0, line);
		} else if (footwall) {
			// special case: on the footwall, meaning the upper edge of the rupture is by definition the closest point
			// to the site
			double distFW = hypot2(rJB, zTop);
			if (R_RUP_ACCOUNT_FOR_FW_MICLASSIFICATION) {
				// except, if we're close in or horzW is large, even though the footwall flag is set, it shouldn't have
				// gotten 50% weight because less than half of azimuths will be in the footwall direction.
				// for example, directly along strike (site D, left of the '!' in the schematic below) is still on the
				// hanging wall
				
				// we can't fix the weight given to hanging wall vs footwall terms in the GMM, but we can at least give
				// a more accurate average rRup
				
				// site 'D' below
				double distHW = distanceToLineSegment3D(0.25*horzWidth, rJB, new LineSegment3D(0, 0, zTop, horzWidth, 0, zBot));
				
//				// calculate the arc length associated with the footwall distance; we'll do this in a single quadrant
//				// since it's symmetrical: half of the length, plus 1/4 of the circumference of a circle with radius rJB
//				double weightFW = 0.5*length + PI_HALF*rJB;
//				
//				// now figure out the fraction that would actually be on the hanging wall (and was incorrectly assigned to
//				// this footwall rupture)
//				double weightHW = 0.5*horzWidth;
//				return (weightFW*distFW + weightHW*distHW)/(weightFW + weightHW);
				
				double halfLength = 0.5*length;
				double halfWidth = 0.5*horzWidth;

				// azimuth from G to the rightmost '!' that is rJB away 
				double theta1 = Math.atan(halfWidth / (halfLength + rJB));

				double weightHW =  theta1;
				double weightFW = PI_HALF - theta1;

				// sum of the weights is PI/2
				Preconditions.checkState(weightHW >= 0);
				Preconditions.checkState(weightFW <= PI_HALF);

				return (weightFW*distFW + weightHW*distHW)/PI_HALF;
			} else {
				return distFW;
			}
		}
		
		// if we're here, we're on the hanging wall and rJB>0
		// approximate the calculation assuming different angles; we'll use three locations:
		/*
		 * map view schematic; legend:
		 * G: grid node center
		 * ||: rupture upper edge
		 * |: rupture lower edge
		 * A: along-strike of the rupture, but shifted to the right because HW flag means we're not left of G (put it halfway between G and .)
		 * B: site is somewhere off the end of the fault, and also past the bottom edge
		 * C: site perfectly down-dip of the rupture, and also rJB away from the surface projection of the rupture
		 * D: site that would improperly get included as footwall and is accounted for above
		 * *: origin where x=0 and y=0 (upper front corner of the rupture)
		 * .: lower front corner of the rupture
		 * 
		 * 
		 *             D !  A !     
		 *               !    !    B
		 *               !    !
		 *          *_________.-------C
		 *         ||         |
		 *         ||         |
		 *         ||         |
		 *         ||    G    |--------
		 *         ||         |
		 *         ||         |
		 *         ||_________|
		 * 
		 */
		
		// we can compute all distances to the forward edge, from '*' to '.'
		LineSegment3D line = new LineSegment3D(0, 0, zTop, horzWidth, 0, zBot);
		
		// calculate distance from edge [* .] to point A 
		// in that case, we need the distance to the "front" edge of the rupture
		// lets assume that the rupture is centered down-dip about the grid node (G)
		
		// define the origin as location '*' in the graph above, directly above the the front edge and 3/4 of the way to the right 
		// site A is then is at (0.75*, rJB)
		// front edge is along the x axis between:
		//	(-halfWidth, 0, zTop) and (halfWidth, 0, zBot)
		double distA = distanceToLineSegment3D(R_RUP_ACCOUNT_FOR_FW_MICLASSIFICATION ? 0.75*horzWidth : 0.5*horzWidth, rJB, line);
		
		// now calculate for site B where we're off the end and past the bottom
		// define rJB' = rJB*sqrt(2)/2
		// site B is then at (horzWidth + rJB', rJB')
		// front edge is along the x axis between:
		//  (-horzWidth, 0, zTop) and (0, 0, zBot)
		double rJBprime = ROOT_TWO_OVER_TWO * rJB;
		double distB = distanceToLineSegment3D(horzWidth+rJBprime, rJBprime, line);
		
		// now calculate for site C where we're in the perfectly down-dip direction
		// we'll put site C at (horzWdith+rJB, 0); y doesn't matter here since the distance is the same no matter where we are along-stike
		double distC = distanceToLineSegment3D(horzWidth+rJB, 0, line);
		
		// now compute weights between the three
		// when we're really close in, distances A and C dominate
		// when we're far (relive to width and length), distance B dominates
		
		double halfLength = 0.5*length;
		double halfWidth = 0.5*horzWidth;

		// azimuth from G to the rightmost '!' that is rJB away 
		double theta1 = Math.atan(halfWidth / (halfLength + rJB));
		// azimuth from G to the upper '-' that is rJB away
		double theta2 = Math.atan((halfLength+rJB) / halfLength);

		// range from 0 to theta1 belongs to side A
		// range from theta1 to theta2 belongs to corner B
		// range from theta2 to PI/2 belongs to side C
		// sum of the weights is PI/2

		double weightA =  theta1;
		double weightB = theta2 - theta1;
		double weightC = PI_HALF - theta2;

		Preconditions.checkState(Precision.equals(PI_HALF, weightA+weightB+weightC, 1e-4));
		Preconditions.checkState(weightA >= 0);
		Preconditions.checkState(weightB >= 0);
		Preconditions.checkState(weightC >= 0);

		return (weightA*distA + weightB*distB + weightC*distC)/PI_HALF;
	}
	
	public static class LineSegment3D {
		public final double ax, ay, az;
		public final double abX, abY, abZ;
		public final double abDotAb;

		public LineSegment3D(double ax, double ay, double az, double bx, double by, double bz) {
			this.ax = ax;
			this.ay = ay;
			this.az = az;
			this.abX = bx - ax;
			this.abY = by - ay;
			this.abZ = bz - az;
			this.abDotAb = abX * abX + abY * abY + abZ * abZ;
		}
	}
	
	public static double distanceToLineSegment3D(double px, double py, LineSegment3D line) {
		// Vector AP (Pz = 0)
		double apX = px - line.ax;
		double apY = py - line.ay;
		double apZ = -line.az;

		// Projection scalar
		double apDotAb = apX * line.abX + apY * line.abY + apZ * line.abZ;
		double t = apDotAb / line.abDotAb;
		t = Math.max(0, Math.min(1, t));

		// Closest point on segment
		double cx = line.ax + t * line.abX;
		double cy = line.ay + t * line.abY;
		double cz = line.az + t * line.abZ;

		// Distance from P to C
		double dx = px - cx;
		double dy = py - cy;
		double dz = -cz;

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