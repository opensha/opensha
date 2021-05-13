package org.opensha.commons.eq.cat.filters;

import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;

import org.opensha.commons.eq.cat.MutableCatalog;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

/**
 * This class filters catalogs down to those events within some radius of a
 * point. This class will also filter events in time. If reusing a radial filter
 * be sure to (re)set all space/time parameters as unchanged ones will persist.
 * 
 * TODO test the merits of using extents filter first
 * 
 * @author P. Powers
 * @version $Id: RadialFilter.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class RadialFilter implements CatalogFilter {

	/** Maximum allowable search radius in kilometers. */
	public static final double MAX_SEARCH_RADIUS = 2000;

	// filter fields
	private Location center;
	private double radius;
	private Long timeStart;
	private Long timeEnd;

	// utility
	private ExtentsFilter extents = new ExtentsFilter();

	/**
	 * Constructs a new empty radial filter that will produce no results.
	 */
	public RadialFilter() {
		reset();
	}

	/**
	 * Constructs a new radial filter at a specified point and with a specified
	 * radius. Constructor assumes that <code>center</code> is a valid point.
	 * 
	 * @param center the search center point
	 * @param radius the radial search distance
	 */
	public RadialFilter(Location center, double radius) {
		this(center, radius, null, null);
	}

	/**
	 * Constructs a new radial filter centered at a specified point and with the
	 * specified search radius and time window. Constructor assumes that
	 * <code>center</code> is a valid point.
	 * 
	 * @param center the search center point
	 * @param radius the radial search distance
	 * @param start start time limit to radial search
	 * @param end end time limit to radial search
	 */
	public RadialFilter(Location center, double radius, Long start, Long end) {
		setCircle(center, radius);
		setTimeWindow(start, end);
	}

	/**
	 * Sets the search time window for this filter. Using <code>null</code> will
	 * prevent one or both temporal limits from being imposed.
	 * 
	 * @param start the lower time limit of the filter (in milliseconds)
	 * @param end the upper time limit of the filter (in milliseconds)
	 */
	public void setTimeWindow(Long start, Long end) {
		timeStart = start;
		timeEnd = end;
	}

	/**
	 * Sets the search center point and radius for this filter. Method assumes
	 * that <code>center</code> is a valid point.
	 * 
	 * @param center the center point to set
	 * @param radius the radial search distance
	 */
	public void setCircle(Location center, double radius) {
		// TOODO need error checking
		validateRadius(radius);
		this.center = center;
		this.radius = radius;
	}

	/**
	 * Returns the search radius of this filter.
	 * 
	 * @return the search radius
	 */
	public double getRadius() {
		return radius;
	}

	/**
	 * Returns the center point of this filter.
	 * 
	 * @return the search center
	 */
	public Location getCenter() {
		return center;
	}

	/**
	 * Returns the start time limit for this filter (in milliseconds).
	 * 
	 * @return the lower time limit of the search filter
	 */
	public long getTimeWindowStart() {
		return timeStart;
	}

	/**
	 * Returns the end time limit for this filter (in milliseconds).
	 * 
	 * @return the upper time limit of the search filter
	 */
	public long getTimeWindowEnd() {
		return timeEnd;
	}

	/**
	 * Resets this filter to no parameters.
	 */
	public final void reset() {
		center = null;
		radius = -1;
		timeStart = null;
		timeEnd = null;
	}

	/**
	 * Validates input radius.
	 * 
	 * @throws IllegalArgumentException
	 */
	private void validateRadius(double radius) {
		if (radius <= 0 || radius > MAX_SEARCH_RADIUS) {
			throw new IllegalArgumentException(
				"RadialFilter: Radius must be a positive value "
					+ "and less than" + MAX_SEARCH_RADIUS + "(km).");
		}
	}

	@Override
	public int[] process(MutableCatalog catalog) {

		// check initialization
		if (center == null) {
			return null;
			// TODO set no results flag ; run in separate threads for progress
			// bars
		}

		// set up extents filter
		double latDegrees = GeoTools.degreesLatPerKm(center) * radius;
		double lonDegrees = GeoTools.degreesLonPerKm(center) * radius;

		double latMin = center.getLatitude() - latDegrees;
		double latMax = center.getLatitude() + latDegrees;
		double lonMin = center.getLongitude() - lonDegrees;
		double lonMax = center.getLongitude() + lonDegrees;
		extents.setLatitudes(latMin, latMax).setLongitudes(lonMin, lonMax)
			.setDates(timeStart, timeEnd);

		int[] indices;
		int[] indicesOut;

		Location p;
		int count = 0;

		// run extents filter first
		indices = extents.process(catalog);
		if (indices == null) return null;

		double[] eq_latitude = (double[]) catalog.getData(LATITUDE);
		double[] eq_longitude = (double[]) catalog.getData(LONGITUDE);

		for (int i = 0; i < indices.length; i++) {
			// TODO is recreating Locations slow?
			p = new Location(eq_latitude[indices[i]], eq_longitude[indices[i]]);
			// p. setValues(eq_latitude[indices[i]], eq_longitude[indices[i]]);

			if (LocationUtils.horzDistanceFast(center, p) <= radius) {
				// overwriting:
				// selections will always be less or equal to source
				indices[count] = indices[i];
				count += 1;
			}
		}

		// return
		if (count != 0) {
			indicesOut = new int[count];
			System.arraycopy(indices, 0, indicesOut, 0, count);
		} else {
			indicesOut = null;
		}
		return indicesOut;

	}

}
