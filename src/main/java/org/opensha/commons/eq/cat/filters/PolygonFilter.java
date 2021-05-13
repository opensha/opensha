package org.opensha.commons.eq.cat.filters;

import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;

import java.awt.geom.Path2D;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.opensha.commons.eq.cat.MutableCatalog;
import org.opensha.commons.geo.GeoTools;

import com.google.common.primitives.Doubles;

/**
 * This class filters catalogs down to those events that fall within a polygon
 * of lat/lon points.
 * 
 * TODO: Algorithm results as yet are unspecified for polygons that traverse the
 * International Date Line or encircle polar regions. TODO clean up error
 * checking etc
 * 
 * @author P. Powers
 * @version $Id: PolygonFilter.java 8066 2011-07-22 23:59:04Z kmilner $
 */
public class PolygonFilter implements CatalogFilter {

	// polygon shape
	private Path2D poly;

	// utility
	private ExtentsFilter extents = new ExtentsFilter();
	private double latMin;
	private double latMax;
	private double lonMin;
	private double lonMax;

	/**
	 * Constructs a new empty polygon filter that will pruduce no results.
	 */
	public PolygonFilter() {}

	/**
	 * Constructs a new polygon filter with the specified vertices.
	 * 
	 * @param lats latitude vertex values of the polygon
	 * @param lons longitude vertex values of the polygon
	 */
	public PolygonFilter(double[] lats, double[] lons) {
		setPolygon(lats, lons);

	}

	/**
	 * Sets the vertices of the polygon filter.
	 * 
	 * @param lats latitude vertex values of the polygon
	 * @param lons longitude vertex values of the polygon arrays are different
	 *        lengths
	 */
	public void setPolygon(double[] lats, double[] lons) {
		validateInput(lats, lons);
		setPath(lats, lons);
		latMin = Doubles.min(lats);
		latMax = NumberUtils.max(lats);
		lonMin = NumberUtils.min(lons);
		lonMax = NumberUtils.max(lons);
	}
	
	@Override
	public int[] process(MutableCatalog catalog) {

		// abort if uninitialized
		if (poly == null) {
			return null;
		}

		extents.setLatitudes(new Float(latMin), new Float(latMax));
		extents.setLongitudes(new Float(lonMin), new Float(lonMax));

		int[] indices;
		int[] indicesOut;

		// local speed/convenience variables
		int count = 0;

		indices = extents.process(catalog);
		if (indices == null) return null;

		double[] eq_latitude = (double[]) catalog.getData(LATITUDE);
		double[] eq_longitude = (double[]) catalog.getData(LONGITUDE);

		for (int i = 0; i < indices.length; i++) {
			if (poly
				.contains(eq_longitude[indices[i]], eq_latitude[indices[i]])) {
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

	/**
	 * Sets the path of the polygon filter
	 */
	private void setPath(double[] lats, double[] lons) {
		poly = new Path2D.Double(Path2D.WIND_EVEN_ODD, lats.length);
		poly.moveTo(lons[0], lats[0]);
		for (int i = 1; i < lats.length; i++) {
			poly.lineTo(lons[i], lats[i]);
		}
		poly.closePath();
	}

	/**
	 * Checks if polygon is composed of points within valid lat and lon ranges.
	 * 
	 * @throws IllegalArgumentException
	 */
	private static void validateInput(double[] lats, double[] lons) {

		// check array length
		if (!ArrayUtils.isSameLength(lats, lons)) {
			throw new IllegalArgumentException(
				"PolygonFilter: Vertex arrays must be the same length");
		}

		// check ranges
		try {
			GeoTools.validateLats(lats);
			GeoTools.validateLons(lons);
		} catch (IllegalArgumentException iae) {
			throw new IllegalArgumentException("PolygonFilter: "
				+ iae.getMessage());
		}
	}
}
