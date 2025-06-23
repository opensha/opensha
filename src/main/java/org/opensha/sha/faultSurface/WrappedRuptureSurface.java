package org.opensha.sha.faultSurface;

import java.util.ListIterator;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;

/**
 * Abstract class where all methods are passed through to another rupture surface, allowing subclasses to override
 * methods as needed without implemeting everything
 * 
 * @author kevin
 *
 */
public abstract class WrappedRuptureSurface implements RuptureSurface {
	
	private RuptureSurface surf;

	public WrappedRuptureSurface(RuptureSurface surf) {
		this.surf = surf;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		// passthrough
		return surf.getLocationsIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		// passthrough
		return surf.getEvenlyDiscritizedPerimeter();
	}

	@Override
	public LocationList getPerimeter() {
		// passthrough
		return surf.getPerimeter();
	}

	@Override
	public boolean isPointSurface() {
		// passthrough
		return surf.isPointSurface();
	}

	@Override
	public double getAveDip() {
		// passthrough
		return surf.getAveDip();
	}

	@Override
	public double getAveStrike() {
		// passthrough
		return surf.getAveStrike();
	}

	@Override
	public double getAveLength() {
		// passthrough
		return surf.getAveLength();
	}

	@Override
	public double getAveWidth() {
		// passthrough
		return surf.getAveWidth();
	}

	@Override
	public double getAveHorizontalWidth() {
		// passthrough
		return surf.getAveHorizontalWidth();
	}

	@Override
	public double getArea() {
		// passthrough
		return surf.getArea();
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		// passthrough
		return surf.getAreaInsideRegion(region);
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		// passthrough
		return surf.getEvenlyDiscritizedListOfLocsOnSurface();
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		// passthrough
		return surf.getEvenlyDiscritizedUpperEdge();
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		// passthrough
		return surf.getEvenlyDiscritizedLowerEdge();
	}

	@Override
	public double getAveGridSpacing() {
		// passthrough
		return surf.getAveGridSpacing();
	}

	@Override
	public double getQuickDistance(Location siteLoc) {
		// passthrough
		return surf.getQuickDistance(siteLoc);
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		// passthrough
		return surf.getDistanceRup(siteLoc);
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		// passthrough
		return surf.getDistanceJB(siteLoc);
	}

	@Override
	public double getDistanceSeis(Location siteLoc) {
		// passthrough
		return surf.getDistanceSeis(siteLoc);
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		// passthrough
		return surf.getDistanceX(siteLoc);
	}

	@Override
	public double getAveRupTopDepth() {
		// passthrough
		return surf.getAveRupTopDepth();
	}

	@Override
	public double getAveRupBottomDepth() {
		// passthrough
		return surf.getAveRupBottomDepth();
	}

	@Override
	public double getAveDipDirection() {
		// passthrough
		return surf.getAveDipDirection();
	}

	@Override
	public FaultTrace getUpperEdge() {
		// passthrough
		return surf.getUpperEdge();
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		// passthrough
		return surf.getFirstLocOnUpperEdge();
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		// passthrough
		return surf.getLastLocOnUpperEdge();
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		// passthrough
		return surf.getFractionOfSurfaceInRegion(region);
	}

	@Override
	public String getInfo() {
		// passthrough
		return surf.getInfo();
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		// passthrough
		return surf.getMinDistance(surface);
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		// passthrough
		return surf.getMoved(v);
	}

	@Override
	public RuptureSurface copyShallow() {
		// passthrough
		return surf.copyShallow();
	}

}
