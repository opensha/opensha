package org.opensha.sha.faultSurface;

import java.util.ListIterator;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

public interface Surface3D {

	/**
	 * This returns what's given by getEvenlyDiscritizedListOfLocsOnSurface()
	 * as an interator
	 * 
	 * @return 
	 */
	public abstract ListIterator<Location> getLocationsIterator();

	/**
	 * This returns a list of locations that are evenly spread (at least 
	 * approximately) over the surface perimeter, with a spacing given by
	 * what's returned by the getGridSpacing() method.  Further details 
	 * are specified by the implementing class.  These locations should
	 * be ordered starting along the top and moving along following
	 * the Aki and Richards convention.
	 * @return
	 */
	public abstract LocationList getEvenlyDiscritizedPerimeter();

	/** 
	 * Get a list of locations that constitutes the perimeter of
	 * the surface (not necessarily evenly spaced) 
	 */
	public abstract LocationList getPerimeter();

	/**
	 * This indicates whether this is a point surface
	 * @return
	 */
	public abstract boolean isPointSurface();

}