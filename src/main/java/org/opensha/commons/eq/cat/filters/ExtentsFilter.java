package org.opensha.commons.eq.cat.filters;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import org.opensha.commons.eq.cat.MutableCatalog;

/**
 * This class filters catalogs based on space,time and magnitude parameters.
 * Filter assumes that catalogs subjected to processing are sorted ascending by
 * date. Any constructor arguments can be <code>null</code>.
 * 
 * TODO do range checking and throw exceptions TODO check and add documentation
 * for range searching; I believe it's currently >=min & <=max (i.e. inclusive)
 * 
 * @author Peter Powers
 * @version $Id: ExtentsFilter.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class ExtentsFilter implements CatalogFilter {

	// filter fields
	private Double min_lat = null;
	private Double max_lat = null;
	private Double min_lon = null;
	private Double max_lon = null;
	private Double min_dep = null;
	private Double max_dep = null;
	private Double min_mag = null;
	private Double max_mag = null;
	private Long min_dat = null;
	private Long max_dat = null;

	private int[] indexArray;

	/**
	 * Constructs an empty filter that will produce no results.
	 */
	public ExtentsFilter() {}

	/**
	 * Sets latitude filter criteria.
	 * 
	 * @param min minimum latitude
	 * @param max maximum latitude
	 * @return a reference to this filter for method chaining
	 */
	public ExtentsFilter setLatitudes(double min, double max) {
		min_lat = min;
		max_lat = max;
		return this;
	}

	/**
	 * Sets longitude filter criteria.
	 * 
	 * @param min minimum longitude
	 * @param max maximum longitude
	 * @return a reference to this filter for method chaining
	 */
	public ExtentsFilter setLongitudes(double min, double max) {
		min_lon = min;
		max_lon = max;
		return this;
	}

	/**
	 * Sets depth filter criteria.
	 * 
	 * @param min minimum depth
	 * @param max maximum depth
	 * @return a reference to this filter for method chaining
	 */
	public ExtentsFilter setDepths(double min, double max) {
		min_dep = min;
		max_dep = max;
		return this;
	}

	/**
	 * Sets magnitude filter criteria.
	 * 
	 * @param min minimum magnitude
	 * @param max maximum magnitude
	 * @return a reference to this filter for method chaining
	 */
	public ExtentsFilter setMagnitudes(double min, double max) {
		min_mag = min;
		max_mag = max;
		return this;
	}

	/**
	 * Sets date filter criteria.
	 * 
	 * @param min minimum date/time (UTC)
	 * @param max maximum date/time (UTC)
	 * @return a reference to this filter for method chaining
	 */
	public ExtentsFilter setDates(long min, long max) {
		min_dat = min;
		max_dat = max;
		return this;
	}

	@Override
	public int[] process(MutableCatalog catalog) {

		// given that catalogs are almost always sorted by date,
		// first get the index range of valid events, and then process
		// non-null extents params
		long[] eq_time = (long[]) catalog.getData(TIME);

		// get min date index
		int min_dat_idx = 0;
		if (min_dat != null) {
			min_dat_idx = catalog.size();
			long minimum_time = min_dat.longValue();
			for (int i = 0; i < catalog.size(); i++) {
				if (minimum_time > eq_time[i]) continue;
				min_dat_idx = i;
				break;
			}
		}

		// get max date index
		int max_dat_idx = catalog.size();
		if (max_dat != null) {
			max_dat_idx = 0;
			long maximum_time = max_dat.longValue();
			for (int i = catalog.size() - 1; i >= 0; i--) {
				if (maximum_time < eq_time[i]) continue;
				max_dat_idx = i + 1;
				break;
			}
		}

		// set max length int array for valid event indices
		int indexArraySize = max_dat_idx - min_dat_idx;
		// kill if dates are the same or min falls after max
		if (indexArraySize <= 0) return null;
		// System.out.println(indexArraySize);

		indexArray = new int[indexArraySize];
		for (int i = 0; i < indexArray.length; i++) {
			// initialize with valid indices
			indexArray[i] = min_dat_idx + i;
		}

		// process other extents
		double[] eq_latitude = (double[]) catalog.getData(LATITUDE);
		if (!processParams(min_lat, max_lat, eq_latitude, indexArray))
			return null;
		double[] eq_longitude = (double[]) catalog.getData(LONGITUDE);
		if (!processParams(min_lon, max_lon, eq_longitude, indexArray))
			return null;
		double[] eq_depth = (double[]) catalog.getData(DEPTH);
		if (!processParams(min_dep, max_dep, eq_depth, indexArray))
			return null;
		double[] eq_magnitude = (double[]) catalog.getData(MAGNITUDE);
		if (!processParams(min_mag, max_mag, eq_magnitude, indexArray))
			return null;

		return indexArray;
	}

	private boolean processParams(Double minParam, Double maxParam,
			double[] data, int[] eventIndices) {

		int count = 0;

		// process minimum
		if (minParam != null) {
			double minVal = minParam;
			for (int i = 0; i < eventIndices.length; i++) {
				int eventID = eventIndices[i];
				if (data[eventID] < minVal) continue;
				eventIndices[count] = eventID;
				count++;
			}
			// shortcircuit if no event mached
			if (count == 0) return false;
		}
		// System.out.println("  -- minCount: " + count);

		// process maximum
		if (maxParam != null) {
			double maxVal = maxParam;
			// only iterate up to number valid events from above
			int numevents = (count == 0) ? eventIndices.length : count;
			count = 0; // reset
			// System.out.println("  -- maxNumEvents: " + numevents);
			for (int i = 0; i < numevents; i++) {
				int eventID = eventIndices[i];
				if (data[eventID] > maxVal) continue;
				eventIndices[count] = eventID;
				count++;
			}
			if (count == 0) return false;
		}
		// System.out.println("  -- maxCount: " + count);

		// create new event index array
		if (minParam != null || maxParam != null) {
			int[] newEventIndices = new int[count];
			System.arraycopy(eventIndices, 0, newEventIndices, 0, count);
			// System.out.println("  -- newIndices: " + newEventIndices.length);
			indexArray = newEventIndices;
		}

		// System.out.println("  -- evtIndices: " + this.indexArray.length);
		return true;
	}
}
