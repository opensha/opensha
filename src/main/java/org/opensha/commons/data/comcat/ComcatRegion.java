package org.opensha.commons.data.comcat;

import org.opensha.commons.geo.Location;


/**
 * Region on a sphere for filtering Comcat results.
 * Author: Michael Barall 09/19/2018.
 *
 * This interface defines the functions that must be implemented in order
 * to filter Comcat results by geographic location.
 */
public interface ComcatRegion {

	//----- Querying -----

	/**
	 * contains - Test if the region contains the given location.
	 * @param loc = Location to check.
	 * @return
	 * Returns true if loc is inside the region, false if loc is outside the region.
	 * Note: Due to rounding errors, it may be indeterminate whether points exactly on,
	 * or very close to, the boundary of the region are considered inside or outside.
	 * Note: If a point on the earth's surface has more than one possible longitude, then
	 * this function must return a correct result regardless of which longitude is used.
	 * For example, longitude -90 must return the same result as longitude +270.
	 */
	public boolean contains (Location loc);

	/**
	 * contains - Test if the region contains the given location.
	 * @param lat = Latitude to check, can be -90 to +90.
	 * @param lon = Longitude to check, can be -180 to +360.
	 * @return
	 * Returns true if lat/lon is inside the region, false if lat/lon is outside the region.
	 * Note: Due to rounding errors, it may be indeterminate whether points exactly on,
	 * or very close to, the boundary of the region are considered inside or outside.
	 * Note: If a point on the earth's surface has more than one possible longitude, then
	 * this function must return a correct result regardless of which longitude is used.
	 * For example, longitude -90 must return the same result as longitude +270.
	 */
	public boolean contains (double lat, double lon);




	//----- Bounding Box -----

	// These functions define a bounding box around the region.
	// They are used as a preliminary filter when querying Comcat.

	/**
	 * Returns the minimum latitude.
	 * The returned value can range from -90 to +90.
	 */
	public double getMinLat();

	/**
	 * Returns the maximum latitude.
	 * The returned value can range from -90 to +90, and must be greater than getMinLat().
	 */
	public double getMaxLat();

	/**
	 * Returns the minimum longitude.
	 * The returned value can range from -360 to +360.
	 */
	public double getMinLon();

	/**
	 * Returns the maximum longitude.
	 * The returned value can range from -360 to +360, and must be greater than getMinLon().
	 */
	public double getMaxLon();




	//----- Special regions -----

	/**
	 * Return true if this is a rectangular region in a Mercator projection.
	 * If this function returns true, then the region is exactly the box
	 * given by getMinLat(), getMaxLat(), getMinLon(), and getMaxLon().
	 */
	public default boolean isRectangular() {
		return false;
	}

	/**
	 * Return true if this is a circular region on the sphere.
	 * If this function returns true, then the center and radius can be
	 * obtained from getCircleCenterLat, getCircleCenterLon, and getCircleRadiusDeg.
	 */
	public default boolean isCircular() {
		return false;
	}

	/**
	 * If this is a circular region on the sphere, then get the center latitude.
	 * The returned value ranges from -90 to +90.
	 * Otherwise, throw an exception.
	 */
	public default double getCircleCenterLat() {
		throw new UnsupportedOperationException ("The region is not a circle");
	}

	/**
	 * If this is a circular region on the sphere, then get the center longitude.
	 * The returned value ranges from -180 to +180.
	 * Otherwise, throw an exception.
	 */
	public default double getCircleCenterLon() {
		throw new UnsupportedOperationException ("The region is not a circle");
	}

	/**
	 * If this is a circular region on the sphere, then get the radius in degrees.
	 * The returned value ranges from 0 to +180.
	 * Otherwise, throw an exception.
	 */
	public default double getCircleRadiusDeg() {
		throw new UnsupportedOperationException ("The region is not a circle");
	}

}
