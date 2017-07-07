package org.opensha.sha.simulators;

import java.util.ListIterator;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * Quick partial implementation of RuptureSurface interface to allow for triangles. A rull triangular surface implementation
 * is needed in the future. Package private to discourage external use.
 * @author kevin
 *
 */
class TriangularElementSurface implements RuptureSurface {
	
	private Location a;
	private Location b;
	private Location c;
	
	private Location center; // approx center, averaged in lat/lon space
	
	private LocationList perimeter;
	
	private double ab;
	private double bc;
	private double ca;
	
	private double perimeterLen;
	private double area;

	public TriangularElementSurface(Location a, Location b, Location c) {
		this.a = a;
		this.b = b;
		this.c = c;
		
		this.center = new Location(mean(a.getLatitude(), b.getLatitude(), c.getLatitude()),
				mean(a.getLongitude(), b.getLongitude(), c.getLongitude()),
				mean(a.getDepth(), b.getDepth(), c.getDepth()));
		
		perimeter = new LocationList();
		perimeter.add(a);
		perimeter.add(b);
		perimeter.add(c);
		
		ab = LocationUtils.linearDistanceFast(a, b);
		bc = LocationUtils.linearDistanceFast(b, c);
		ca = LocationUtils.linearDistanceFast(c, a);
		
		perimeterLen = ab + bc + ca;
		
		// calculate area using Heron's formula
		double s = perimeterLen/2;
		area = Math.sqrt(s*(s-ab)*(s-bc)*(s-ca));
	}
	
	private static double mean(double... vals) {
		return StatUtils.mean(vals);
	}
	
	private static double min(double... vals) {
		return StatUtils.min(vals);
	}

	@Override
	public double getAveDip() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveStrike() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveLength() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveWidth() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getArea() {
		return area;
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		return perimeter;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return perimeter.listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		return perimeter;
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return null;
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveGridSpacing() {
		return mean(ab, bc, ca);
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		return min(LocationUtils.linearDistanceFast(a, siteLoc),
				LocationUtils.linearDistanceFast(b, siteLoc),
				LocationUtils.linearDistanceFast(c, siteLoc),
				LocationUtils.linearDistanceFast(center, siteLoc));
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getDistanceSeis(Location siteLoc) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveRupTopDepth() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveDipDirection() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public FaultTrace getUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LocationList getPerimeter() {
		return perimeter;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		double third = 1d/3d;
		double ret = 0;
		if (region.contains(a))
			ret += third;
		if (region.contains(b))
			ret += third;
		if (region.contains(c))
			ret += third;
		return ret;
	}

	@Override
	public String getInfo() {
		return "TriangularElementSurface["+a+", "+b+", "+c+"]";
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (Location loc : surface.getEvenlyDiscritizedListOfLocsOnSurface())
			track.addValue(getDistanceRup(loc));
		return track.getMin();
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		Location a2 = LocationUtils.location(a, v);
		Location b2 = LocationUtils.location(b, v);
		Location c2 = LocationUtils.location(c, v);
		return new TriangularElementSurface(a2, b2, c2);
	}

	@Override
	public RuptureSurface copyShallow() {
		return new TriangularElementSurface(a, b, c);
	}

}
