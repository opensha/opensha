package org.opensha.sha.faultSurface;

import java.util.ListIterator;
import java.util.Random;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;

/**
 * Similar to {@link QuadSurface} but for a line (zero width). Experimental.
 * 
 * @author Kevin Milner
 * @version $Id:$
 */
public class LineSurface implements RuptureSurface, CacheEnabledSurface {
	
	final static boolean D = false;
	
	private double dipDeg; // not used, just reported when asked via getAveDip
	private double depth;

	/* actual 3d values */
	private FaultTrace trace;
	private FaultTrace traceBelowSeis;
	private boolean allSameDepth;
	private double[] segmentLengths;
	
	/*
	 * discretization to use for evenly discretized methods
	 */
	private double discr_km = 1d;
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);

	/**
	 * 
	 * @param trace
	 * @param dip in degrees
	 * @param width down dip width in km
	 */
	public LineSurface(FaultTrace trace, double dip) {
		this.trace = trace;
		this.dipDeg = dip;
		double sumLen = 0d;
		Preconditions.checkArgument(trace.size() > 1, "Must have at least 2 locations");
		double sumLenDepthProd = 0d;
		allSameDepth = true;
		boolean anyAboveSeis = false;
		segmentLengths = new double[trace.size()-1];
		for (int i=0; i<trace.size()-1; i++) {
			Location l0 = trace.get(i);
			Location l1 = trace.get(i+1);
			allSameDepth &= l0.depth == l1.depth;
			anyAboveSeis |= l0.depth > GriddedSurfaceUtils.SEIS_DEPTH | l1.depth > GriddedSurfaceUtils.SEIS_DEPTH;
			segmentLengths[i] = LocationUtils.linearDistanceFast(l0, l1);
			double depth = 0.5*(l0.depth + l1.depth);
			sumLenDepthProd += depth*segmentLengths[i];
			sumLen += segmentLengths[i];
		}
		if (allSameDepth)
			this.depth = trace.first().depth;
		else
			this.depth = sumLenDepthProd/sumLen;
		if (anyAboveSeis) {
			// make a copy below SEIS_DEPTH
			traceBelowSeis = new FaultTrace("below seis");
			for (Location loc : trace) {
				if (loc.depth > GriddedSurfaceUtils.SEIS_DEPTH)
					traceBelowSeis.add(new Location(loc.lat, loc.lon, GriddedSurfaceUtils.SEIS_DEPTH));
				else
					traceBelowSeis.add(loc);
			}
		} else {
			traceBelowSeis = trace;
		}
	}
	
	@Override
	public SurfaceDistances calcDistances(Location loc) {
		return new LazySurfaceDistances(loc);
	}
	
	private class LazySurfaceDistances implements SurfaceDistances {
		
		private Location siteLoc;
		private double[] segHorzDists;
		
		private volatile Double distRup, distJB, distSeis, distX;

		private LazySurfaceDistances(Location siteLoc) {
			this.siteLoc = siteLoc;
			this.segHorzDists = calcSegHorzDists(trace, siteLoc);
		}

		@Override
		public Location getSiteLocation() {
			return siteLoc;
		}

		@Override
		public double getDistanceRup() {
			if (distRup == null)
				distRup = distance(trace, siteLoc, true, segHorzDists);
			return distRup;
		}

		@Override
		public double getDistanceJB() {
			if (distJB == null)
				distJB = distance(trace, siteLoc, false, segHorzDists);
			return distJB;
		}

		@Override
		public double getDistanceSeis() {
			if (distSeis == null) {
				if (trace == traceBelowSeis)
					distSeis = getDistanceRup();
				else
					distSeis = distance(traceBelowSeis, siteLoc, true, segHorzDists);
			}
			return distSeis;
		}

		@Override
		public double getDistanceX() {
			if (distX == null)
				distX = distanceX(trace, siteLoc, segHorzDists);
			return distX;
		}
		
	}

	public double getDistanceRup(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceRup();
	}

	public double getDistanceJB(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceJB();
	}

	public double getDistanceSeis(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceSeis();
	}
	
	private static double[] calcSegHorzDists(FaultTrace trace, Location loc) {
		double[] segHorzDists = new double[trace.size()-1];
		for (int i=0; i<segHorzDists.length; i++)
			segHorzDists[i] = LocationUtils.distanceToLineSegmentFast(trace.get(i), trace.get(i+1), loc);
		return segHorzDists;
	}
	
	private double distance(FaultTrace trace, Location loc, boolean threeD) {
		return distance(trace, loc, threeD, calcSegHorzDists(trace, loc));
	}
	
	private double distance(FaultTrace trace, Location loc, boolean threeD, double[] segHorzDists) {
		double distance = Double.MAX_VALUE;
		for (int i = 0; i < trace.size() - 1; i++) {
			Location l0 = trace.get(i);
			Location l1 = trace.get(i+1);
			double horzDist = segHorzDists[i];
			double segDist;
			if (threeD && !allSameDepth) {
				// need to calculate a complicated 3D distance
				// will keep track of squared distances and only do 1 sqrt at the end
				double horzDistSq = horzDist*horzDist;
				if (horzDistSq > distance)
					// already further away without factoring in depth, don't bother
					continue;
				if (l0.depth == l1.depth) {
					// simple case, both depths are equal
					// (will sqrt at the end)
					segDist = horzDistSq + l0.depth*l0.depth;
				} else {
					/*
					 * need to calculate the depth of the closest location on this span (lc)
					 * 
					 * will use right triangles to find the distance l0->lc and l1->lc, and will weight depths by
					 * that distance
					 * 
					 * * l0
					 * |
					 * |
					 * |
					 * |
					 * |
					 * |
					 * |
					 * * lc         * loc
					 * |
					 * |
					 * |
					 * |
					 * * l1
					 * 
					 */
					// distance between l0 and loc
					double distL0 = LocationUtils.horzDistanceFast(loc, l0);
					// distance between l1 and loc
					double distL1 = LocationUtils.horzDistanceFast(loc, l1);
					segDist = Double.NaN;
					if ((float)horzDist < (float)distL0 && (float)horzDist < (float)distL1) {
						// i'm in the interior (as pictured above)
						double distL0_LC = Math.sqrt(distL0*distL0 - horzDistSq); // horzDist is already squared
						double distL1_LC = Math.sqrt(distL1*distL1 - horzDistSq); // horzDist is already squared
						double calcLen = distL0_LC + distL1_LC;
						if ((float)calcLen <= (float)segmentLengths[i]) {
							// this check is in here because the line segment distance (horzDist) uses slightly different math
							// than point distances, so horzDist will be slightly different distL0 or distL1 even when they should
							// be the same.
							
							// not sure why this can get so off, but it's just used to weight distances so maybe it doesn't matter
//							double fDiff = Math.abs(calcLen - segmentLengths[i])/segmentLengths[i];
//							Preconditions.checkState(fDiff < 0.05,
//									"Calculated segment length using right triangles (%s) doesn't match actual (%s);"
//									+ " horzDist=%s, distL0=%s, distL1=%s, fDiff=%s",
//									(float)calcLen, (float)segmentLengths[i], (float)horzDist, (float)distL0, (float)distL1, (float)fDiff);
							// these are intentionally flipped (l1 depth for distL0_LC) to avoid adding 1- terms
							double depthLC = (distL0_LC/calcLen)*l1.depth + (distL1_LC/calcLen)*l0.depth;
							segDist = horzDistSq + depthLC*depthLC;
						}
					}
					if (Double.isNaN(segDist)) {
						if (distL0 <= distL1) {
							// off the end and closest to l0
							segDist = horzDistSq + l0.depth*l0.depth;
						} else {
							// off the end and closest to l1
							segDist = horzDistSq + l1.depth*l1.depth;
						}
					}
				}
			} else {
				// can just use horizontal distance
				segDist = horzDist;
			}
			distance = Math.min(distance, segDist);
		}
		Preconditions.checkState(!Double.isNaN(distance));
		if (threeD) {
			if (allSameDepth) {
				// add depth term now
				double depth = trace.first().depth;
				distance = Math.sqrt(distance*distance + depth*depth);
			} else {
				// distance currently is squared, need to sqrt (only do this once here instead of once for each segment)
				distance = Math.sqrt(distance);
			}
		}
		return distance;
	}
	
	private double distanceX(FaultTrace trace, Location loc) {
		return distanceX(trace, loc, calcSegHorzDists(trace, loc));
	}
	
	private double distanceX(FaultTrace trace, Location loc, double[] segHorzDists) {
		int minIndex = -1;
		double minDist = Double.POSITIVE_INFINITY;
		for (int i=0; i<segHorzDists.length; i++) {
			if (segHorzDists[i] < minDist) {
				minDist = segHorzDists[i];
				minIndex = i;
			}
		}
		double rFirst = LocationUtils.horzDistanceFast(trace.get(0), loc);
		double rLast = LocationUtils.horzDistanceFast(trace.last(), loc);

		return (minDist < Math.min(rFirst, rLast)) ? LocationUtils.distanceToLineFast(
				trace.get(minIndex), trace.get(minIndex + 1), loc)
				: LocationUtils.distanceToLineFast(trace.first(), trace.last(), loc);
	}
	
	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}	

	@Override
	public double calcQuickDistance(Location siteLoc) {
		// just use DistanceRup
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}
	
	public double getDistanceX(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceX();
	}
	
//	private EvenlyGriddedSurface getGridded() {
//		if (gridSurf == null) {
//			double lower = avgUpperDepth + width;
//			gridSurf = new StirlingGriddedSurface(trace, dip, avgUpperDepth, lower, discr_km);
//		}
//		return gridSurf;
//	}

	@Override
	public double getAveDip() {
		return dipDeg;
	}

	@Override
	public double getAveStrike() {
		return trace.getAveStrike();
	}

	@Override
	public double getAveLength() {
		return trace.getTraceLength();
	}

	@Override
	public double getAveWidth() {
		return 0d;
	}

	@Override
	public double getAveHorizontalWidth() {
		return 0d;
	}

	@Override
	public double getArea() {
		return 0d;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		return 0d;
	}

	private LocationList disretizedTrace;

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		if (disretizedTrace == null) {
			synchronized (this) {
				if (disretizedTrace == null) {
					disretizedTrace = FaultUtils.resampleTrace(trace, getNumDiscrAlongStrike()).unmodifiableList();
				}
			}
		}
		return disretizedTrace;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getEvenlyDiscritizedListOfLocsOnSurface().listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		return getEvenlyDiscritizedListOfLocsOnSurface();
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		LocationList discretized = getEvenlyDiscritizedListOfLocsOnSurface();
		FaultTrace ret = new FaultTrace(null, discretized.size());
		ret.addAll(discretized);
		return ret;
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		return getEvenlyDiscritizedListOfLocsOnSurface();
	}

	@Override
	public double getAveGridSpacing() {
		return discr_km;
	}
	
	/**
	 * Sets grid spacing used for all evenly discretized methods
	 * @param gridSpacing
	 */
	public synchronized void setAveGridSpacing(double gridSpacing) {
		this.discr_km = gridSpacing;
		this.disretizedTrace = null;
	}

	@Override
	public double getAveRupTopDepth() {
		return depth;
	}

	@Override
	public double getAveRupBottomDepth() {
		return depth;
	}

	@Override
	public double getAveDipDirection() {
		return trace.getAveStrike() + 90d;
	}

	@Override
	public FaultTrace getUpperEdge() {
		return trace;
	}

	@Override
	public LocationList getPerimeter() {
		return trace;
	}
	
	// add 1e-5 here so that it rounds up if exactly even, so for a 1km trace with 1km spacing, we need 2 points
	private int getNumDiscrAlongStrike() {
		int val = (int)Math.ceil((1e-5+getAveLength())/discr_km);
		return val > 2 ? val : 2;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return trace.get(0);
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		return trace.last();
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not yet implemented");
	}

	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		throw new RuntimeException("not yet implemented");
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		FaultTrace traceMoved = new FaultTrace(trace.getName());
		for (Location loc : trace)
			traceMoved.add(LocationUtils.location(loc, v));
		return new LineSurface(traceMoved, dipDeg);
	}

	@Override
	public LineSurface copyShallow() {
		return new LineSurface(trace, dipDeg);
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

	public static void main(String[] args) {
		int numBenchmark = 1000000;
		Location[] testLocs = new Location[Integer.min(numBenchmark, 10000)];
		Random r = new Random(1234*testLocs.length);
		for (int i=0; i<testLocs.length; i++)
			testLocs[i] = new Location(34d + r.nextDouble()*2, -120d + r.nextDouble()*2);
		
//		Double fixedDepth = 2d;
		Double fixedDepth = null;
		
		FaultTrace trace = new FaultTrace(null);
		trace.add(new Location(34, -118, fixedDepth == null ? 2d : fixedDepth));
		trace.add(new Location(34.7, -117.4, fixedDepth == null ? 5d : fixedDepth));
		trace.add(new Location(35, -117, fixedDepth == null ? 3d : fixedDepth));
		
		LineSurface lineSurf = new LineSurface(trace, 0d);
		QuadSurface quadSurf = new QuadSurface(trace, 90d, 0.000001d);
		
//		double eps = 0.5;
		double minEPS = 0.1;
		double fractEPS = 0.05;
		
		// first validate
		for (int i=0; i<testLocs.length; i++) {
			SurfaceDistances lineDists = lineSurf.calcDistances(testLocs[i]);
			SurfaceDistances quadDists = quadSurf.calcDistances(testLocs[i]);
			double eps = Math.max(minEPS, fractEPS*quadDists.getDistanceRup());
			Preconditions.checkState(Precision.equals(lineDists.getDistanceJB(), quadDists.getDistanceJB(), eps),
					"rJB mismatch; line=%s, quad=%s", (float)lineDists.getDistanceJB(), (float)quadDists.getDistanceJB());
			Preconditions.checkState(Precision.equals(lineDists.getDistanceRup(), quadDists.getDistanceRup(), eps),
					"rRup mismatch; line=%s, quad=%s", (float)lineDists.getDistanceRup(), (float)quadDists.getDistanceRup());
//			Preconditions.checkState(Precision.equals(lineDists.getDistanceSeis(), quadDists.getDistanceSeis(), eps),
//					"rSeis mismatch; line=%s, quad=%s", (float)lineDists.getDistanceSeis(), (float)quadDists.getDistanceSeis());
//			Preconditions.checkState((float)lineDists.getDistanceJB() == (float)quadDists.getDistanceJB());
//			Preconditions.checkState((float)lineDists.getDistanceRup() == (float)quadDists.getDistanceRup());
//			Preconditions.checkState((float)lineDists.getDistanceSeis() == (float)quadDists.getDistanceSeis());
//			Preconditions.checkState((float)lineDists.getDistanceJB() == (float)quadDists.getDistanceJB());
		}
	}

}
