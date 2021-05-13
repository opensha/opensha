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
 * Reader class for lines from a catalog relocated by Thurber et al. for
 * the Parkfield region.<br/>
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
 * @version $Id: Reader_NC_PARK.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_NC_PARK extends AbstractReader {

	private static final String NAME = "";
	private static final String DESC = "";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_NC_PARK(int size) {
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
          YYYY MM DD HH MM SS.ss       lat         lon    dep   M    EventID
        ----------------------------------------------------------------------------------------------
          2001  5  1  6 22  7.06 35.990392 -120.548444  3.759 1.2   21161434
          2001  5  1  8 49 51.61 36.057106 -120.623071  1.800 1.1   21161445
          2001  5  1  8 53 21.42 35.958028 -120.514316 10.222 2.0   21161447
        0         1         2         3         4         5         6         7
        01234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {

			count++;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(2,6).trim()));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(7,9).trim()) - 1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(10,12).trim()));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(13,15).trim()));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(16,18).trim()));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(19,21).trim()));
			cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(22,24).trim()) * 10));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(54,57).trim()));

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(24,34).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(35,46).trim()));
			dat_depths.add(Double.parseDouble(line.substring(47,53).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(59,68).trim()));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
