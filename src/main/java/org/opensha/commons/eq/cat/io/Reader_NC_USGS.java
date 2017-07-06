package org.opensha.commons.eq.cat.io;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.util.ArrayList;
import java.util.Calendar;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Reader class for lines from a catalog relocated by Ellsworth et al. for
 * the Hayward and Calaveras faults in the Bay Area.<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 *
 * @author Peter Powers
 * @version $Id: Reader_NC_USGS.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_NC_USGS extends AbstractReader {

	private static final String NAME = "";
	private static final String DESC = "";

	//        no duplicates
	//        "Parses a Northern California Earthquake Data Center " +
	//        "NCEDC format catalog. Reader will gather: event ID, time, " +
	//        "longitude, latitude, depth, magnitude, and magnitude type.";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_NC_USGS(int size) {
		super(NAME, DESC, size);
	}

	@Override
	public void initReader() {
		count = 0;
		dat_eventIDs       = new ArrayList<Integer>(size);
		dat_dates          = new ArrayList<Long>(size);
		dat_longitudes     = new ArrayList<Double>(size);
		dat_latitudes      = new ArrayList<Double>(size);
		dat_depths         = new ArrayList<Double>(size);
		dat_magnitudes     = new ArrayList<Double>(size);
	}

	@Override
	public void loadData() {
		catalog.addData(EVENT_ID, Ints.toArray(dat_eventIDs));
		catalog.addData(TIME, Longs.toArray(dat_dates));
		catalog.addData(LONGITUDE, Doubles.toArray(dat_longitudes));
		catalog.addData(LATITUDE, Doubles.toArray(dat_latitudes));
		catalog.addData(DEPTH, Doubles.toArray(dat_depths));
		catalog.addData(MAGNITUDE, Doubles.toArray(dat_magnitudes));
	}

	@Override
	public void parseLine(String line) {

		/** Sample data
            Lon              Lat        Depth   Mag   Date                    Event ID
        ----------------------------------------------------------------------------------------------
            -121.662987      37.309364  -6.785  1.1   1984  5  3 13 19 39.90  -1114812
            -121.562584      37.119484  -3.741  1.0   1984  5  2 17 52 12.84  -1114770
            -121.617111      37.301632  -6.485  0.6   1984  5  2 14 19 48.82  -1114760
            -121.569817      37.170494  -3.508  1.4   1984  5  2 12 23 11.52  -1114755
            -121.657913      37.278057  -6.470  1.0   1984  5  2 11 17 14.17  -1114750
            -121.324379      36.737301  -9.667  1.1   1984  5  1  1 48 36.62     17140
            -121.639877      37.259247  -6.735  2.5   1984  5  1  2 20  2.83     17143
        0         1         2         3         4         5         6         7         8         9         1
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(46,50).trim()));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(51,53).trim()) - 1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(54,56).trim()));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(57,59).trim()));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(60,62).trim()));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(63,65).trim()));
			cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(66,68).trim()) * 10));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(40,43).trim()));

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(21,30).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(4,15).trim()));
			dat_depths.add(-1 * Double.parseDouble(line.substring(31,38).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(69,78).trim()));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
