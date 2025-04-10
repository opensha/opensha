package org.opensha.sha.faultSurface;

import java.util.ListIterator;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;

/**
 * Simple surface for a single rectangle fault. Distance calculations are analytical and very fast
 */
public class RectangleSurface implements CacheEnabledSurface {

	// inputs
	private final FaultTrace trace;
	private final Location startLoc;
	private final Location endLoc;
	private final double length;
	private final double dip;
	private final double zTop;
	private final double zBot;

	// calculated
	private final double strike;
	private final double strikeRad;
	private final double dipRad;
	private final double ddw;
	private final double horzWidth;
	private final double vertWidth;
	private final boolean vertical;
	private final double sinDip;
	private final double cosDip;

	/*
	 * discretization to use for evenly discretized methods
	 */
	private double discr_km = 1d;
	private FaultTrace[] horzSpans;
	private FaultTrace[] horzSpansDiscr;

	// create cache using default caching policy
	private final SurfaceDistanceCache cache;

	public RectangleSurface(Location startLoc, Location endLoc, double dip, double zBot) {
		Preconditions.checkState(Precision.equals(startLoc.depth, endLoc.depth, 1e-3),
				"Start and end location must have same depth");
		this.startLoc = startLoc;
		this.endLoc = endLoc;
		this.trace = new FaultTrace(null, 2);
		this.trace.add(startLoc);
		this.trace.add(endLoc);
		this.length =  LocationUtils.horzDistanceFast(startLoc, endLoc);
		Preconditions.checkState(length > 0d, "length=%s must be > 0", (float)length);
		this.dip = dip;
		this.zTop = startLoc.depth;
		this.zBot = zBot;

		this.strikeRad = LocationUtils.azimuthRad(startLoc, endLoc);
		this.strike = strikeRad * GeoTools.TO_DEG;

		Preconditions.checkState(zTop < zBot, "zTop=%s must be < zBot=%s", (float)zTop, (float)zBot);

		this.vertical = dip >= 89.999; // treat ≥ ~90° as vertical to avoid tan() blow‑up
		if (vertical) {
			sinDip = 1d; // sin(90)=1
			cosDip = 0d; // cos(90)=0
			dipRad = Math.PI*0.5;
			vertWidth = zBot - zTop;
			horzWidth = 0;
			ddw = vertWidth;
		} else {
			dipRad = Math.toRadians(dip);
			sinDip = Math.sin(dipRad);
			cosDip = Math.cos(dipRad);
			vertWidth = zBot - zTop;
			horzWidth = vertWidth / Math.tan(dipRad);
			ddw = vertWidth/sinDip;
			double dipDirRad = strikeRad + Math.PI*0.5;
			Location botStartLoc = LocationUtils.location(startLoc, dipDirRad, horzWidth);
			botStartLoc = new Location(botStartLoc.lat, botStartLoc.lon, zBot);
			Location botEndLoc = LocationUtils.location(endLoc, dipDirRad, horzWidth);
			botEndLoc = new Location(botEndLoc.lat, botEndLoc.lon, zBot);
			Location topMiddle = LocationUtils.location(startLoc, strikeRad, 0.5*length);
			Location middle = LocationUtils.location(topMiddle, dipDirRad, 0.5*horzWidth);
			middle = new Location(middle.lat, middle.lon, zTop + 0.5*vertWidth);
		}

		this.cache = SurfaceCachingPolicy.build(this, CacheTypes.SINGLE); // always single for this simple representation
	}

	@Override
	public double getAveDip() {
		return dip;
	}

	@Override
	public double getAveStrike() {
		return strike;
	}

	@Override
	public double getAveLength() {
		return length;
	}

	@Override
	public double getAveWidth() {
		return ddw;
	}

	@Override
	public double getArea() {
		return length*ddw;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		return getArea()*getFractionOfSurfaceInRegion(region);
	}

	private LocationList surfLocs;

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		if (surfLocs == null) {
			synchronized (this) {
				if (surfLocs == null) {
					int numDDW = getNumDiscrDownDip();
					int size = getNumDiscrAlongStrike()*numDDW;
					LocationList locList = new LocationList(size);
					for (int i=0; i<numDDW; i++)
						locList.addAll(getEvenlyDiscretizedHorizontalSpan(i));
					surfLocs = locList.unmodifiableList();
				}
			}
		}
		return surfLocs;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getEvenlyDiscritizedListOfLocsOnSurface().listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		// build permineter
		LocationList perim = new LocationList();
		LocationList upper = getEvenlyDiscritizedUpperEdge();
		LocationList lower = getEvenlyDiscritizedLowerEdge();
		LocationList right = GriddedSurfaceUtils.getEvenlyDiscretizedLine(upper.last(), lower.last(), discr_km);
		LocationList left = GriddedSurfaceUtils.getEvenlyDiscretizedLine(lower.first(), upper.first(), discr_km);
		// top, forwards
		perim.addAll(upper);
		// "right", except the first point
		perim.addAll(right.subList(1, right.size()));
		// bottom, backwards
		perim.addAll(getReversed(lower).subList(1, lower.size()));
		// "left"
		perim.addAll(left.subList(1, left.size()));
		return perim;
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return getEvenlyDiscretizedHorizontalSpan(0);
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		return getEvenlyDiscretizedHorizontalSpan(getNumDiscrDownDip()-1);
	}

	@Override
	public FaultTrace getUpperEdge() {
		return trace;
	}

	@Override
	public LocationList getPerimeter() {
		// build permineter
		LocationList perim = new LocationList();
		// top, forwards
		for (Location loc : trace)
			perim.add(loc);
		// bottom, backwards
		perim.addAll(getReversed(getHorizontalPoints(ddw)));
		// close it
		perim.add(perim.get(0));
		return perim;
	}

	private static LocationList getReversed(LocationList locs) {
		LocationList reversed = new LocationList();
		for (int i=locs.size(); --i>=0;)
			reversed.add(locs.get(i));
		return reversed;
	}

	// add 1e-5 here so that it rounds up if exactly even, so for a 1km trace with 1km spacing, we need 2 points
	private int getNumDiscrAlongStrike() {
		int val = (int)Math.ceil((1e-5+getAveLength())/discr_km);
		return val > 2 ? val : 2;
	}

	private int getNumDiscrDownDip() {
		int val = (int)Math.ceil((1e-5+ddw)/discr_km);
		return val > 2 ? val : 2;
	}

	private synchronized FaultTrace getHorizontalSpan(int index) {
		if (horzSpans != null) {
			Preconditions.checkState(index <= horzSpans.length);
			if (horzSpans[index] != null)
				return horzSpans[index];
		} else {
			horzSpans = new FaultTrace[getNumDiscrDownDip()];
		}
		if (index == 0) {
			horzSpans[index] = trace;
		} else {
			FaultTrace locs = new FaultTrace("SubTrace "+index);
			double widthDownDip = index*ddw/(horzSpans.length-1d);
			double hDistance = widthDownDip * Math.cos( dipRad );
			double vDistance = widthDownDip * Math.sin(dipRad);
			LocationVector dir = new LocationVector(dip, hDistance, vDistance);
			for (Location traceLoc : trace)
				locs.add(LocationUtils.location(traceLoc, dir));
			horzSpans[index] = locs;
		}
		return horzSpans[index];
	}

	private synchronized FaultTrace getEvenlyDiscretizedHorizontalSpan(int index) {
		if (horzSpansDiscr != null) {
			Preconditions.checkState(index <= horzSpansDiscr.length);
			if (horzSpansDiscr[index] != null)
				return horzSpansDiscr[index];
		} else {
			horzSpansDiscr = new FaultTrace[getNumDiscrDownDip()];
		}
		FaultTrace subTrace = getHorizontalSpan(index);
		horzSpansDiscr[index] = FaultUtils.resampleTrace(subTrace, getNumDiscrAlongStrike());
		return horzSpansDiscr[index];
	}

	/**
	 * This returns basically a fault trace, but at the given depth down dip
	 * of the fault. If width is passed in, the bottom trace is given.
	 * 
	 * Points given in same order as top fault trace.
	 * @param widthDownDip
	 * @return
	 */
	private LocationList getHorizontalPoints(double widthDownDip) {
		LocationList locs = new LocationList();
		double hDistance = widthDownDip * Math.cos( dipRad );
		double vDistance = widthDownDip * Math.sin(dipRad);
		LocationVector dir = new LocationVector(dip, hDistance, vDistance);
		for (Location traceLoc : trace) {
			locs.add(LocationUtils.location(traceLoc, dir));
		}
		return locs;
	}

	@Override
	public double getAveGridSpacing() {
		return discr_km;
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

	@Override
	public double getAveRupTopDepth() {
		return zTop;
	}

	@Override
	public double getAveDipDirection() {
		return strike + 90d;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return startLoc;
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		return endLoc;
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		// TODO could be more efficient in rect space
		int numRows = getNumDiscrDownDip();
		int numCols = getNumDiscrAlongStrike();
		double gridSpacingDown = getAveWidth()/(numRows-1);
		double gridSpacingAlong = getAveLength()/(numCols-1);
		// this is not simply trivial because we are not grid centered
		double areaInside = 0d;
		double sumArea = 0d;
		for (int row=0; row<numRows; row++) {
			// it's a top or bottom so this point represents a half cell
			double myWidth = row == 0 || row == numRows-1 ? 0.5*gridSpacingDown : gridSpacingDown;
			FaultTrace span = getEvenlyDiscretizedHorizontalSpan(row);
			Preconditions.checkState(span.size() == numCols);
			for (int col=0; col<numCols; col++) {
				// it's a left or right so this point represents a half cell
				double myLen = col == 0 || col == numCols-1 ? 0.5*gridSpacingAlong : gridSpacingAlong;
				double myArea = myWidth * myLen;
				sumArea += myArea;
				if (region.contains(span.get(col)))
					areaInside += myArea;
			}
		}
		return areaInside/sumArea;
	}

	@Override
	public String getInfo() {
		return null;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		Location startLoc = LocationUtils.location(this.startLoc, v);
		Location endLoc = LocationUtils.location(this.endLoc, v);
		return new RectangleSurface(startLoc, endLoc, cosDip, zBot);
	}

	@Override
	public RuptureSurface copyShallow() {
		return new RectangleSurface(startLoc, endLoc, cosDip, zBot);
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		// ---- horizontal position relative to trace start -------------------
		double dist = LocationUtils.horzDistanceFast(startLoc, loc);  // km
		double az   = LocationUtils.azimuthRad(startLoc, loc);        // rad
		double delta = az - strikeRad;                                 // rad

		double x = dist * Math.cos(delta); // along‑strike (km)
		double y = dist * Math.sin(delta); // +ve down‑dip (km)

		// ---- vertical coordinate ------------------------------------------
		double zRel = -zTop; // site depth is 0

		// ---- rJB -----------------------------------------------------------
		double xClamped = clamp(x, 0.0, length);
		double yClamped = clamp(y, 0.0, horzWidth);
		double rJB = hypot(x - xClamped, y - yClamped);

		// ---- rRup ----------------------------------------------------------
		double rRup;
		if (vertical) {
			if (zTop == 0d)
				rRup = rJB;
			else
				rRup = hypot(rJB, zTop);
		} else {
			double dot = y * sinDip - zRel * cosDip; // signed dist to plane
			double yProj = y - dot * sinDip;
			double xProj = x;

			double xPlane = clamp(xProj, 0.0, length);
			double yPlane = clamp(yProj, 0.0, horzWidth);
			double zPlane = yPlane * Math.tan(dipRad);

			rRup = hypot3(x - xPlane, y - yPlane, zRel - zPlane);
		}

		// ---- rSeis ---------------------------------------------------------
		double seisDepth = GriddedSurfaceUtils.SEIS_DEPTH;
		double rSeis;
		if (zTop > seisDepth) {
			rSeis = rRup;
		} else {
			if (vertical) {
				double distToPlane = Math.abs(y);
				double zClamped = clamp(0.0, Math.max(seisDepth, zTop), zBot);
				rSeis = hypot(distToPlane, -zClamped);
			} else {
				double yStart = (seisDepth - zTop) / Math.tan(dipRad);
				double yEnd   = horzWidth;
				if (yStart > yEnd) yEnd = yStart; // rectangle collapses to line

				double dot = y * sinDip - zRel * cosDip;
				double yProj = y - dot * sinDip;
				double xProj = x;

				double xPlane = clamp(xProj, 0.0, length);
				double yPlane = clamp(yProj, yStart, yEnd);
				double zPlane = yPlane * Math.tan(dipRad);

				rSeis = hypot3(x - xPlane, y - yPlane, zRel - zPlane);
			}
		}
		return new SurfaceDistances(rRup, rJB, rSeis);
	}

	private double calcDistanceJB(Location loc) {
		// ---- horizontal position relative to trace start -------------------
		double dist = LocationUtils.horzDistanceFast(startLoc, loc);  // km
		double az   = LocationUtils.azimuthRad(startLoc, loc);        // rad
		double delta = az - strikeRad;                                 // rad

		double x = dist * Math.cos(delta); // along‑strike (km)
		double y = dist * Math.sin(delta); // +ve down‑dip (km)

		// ---- rJB -----------------------------------------------------------
		double xClamped = clamp(x, 0.0, length);
		double yClamped = clamp(y, 0.0, horzWidth);
		return hypot(x - xClamped, y - yClamped);
	}

	@Override
	public double calcQuickDistance(Location loc) {
		return calcDistanceJB(loc);
	}

	@Override
	public double calcDistanceX(Location loc) {
		return GriddedSurfaceUtils.getDistanceX(trace, loc);
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

	private static double clamp(double v, double min, double max) {
		return (v < min ? min : (v > max ? max : v));
	}

	private static double hypot(double a, double b) {
		return Math.hypot(a, b);
	}

	private static double hypot3(double a, double b, double c) {
		return Math.sqrt(a * a + b * b + c * c);
	}

}
