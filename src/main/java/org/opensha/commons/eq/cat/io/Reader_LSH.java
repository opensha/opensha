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
 * Reader class for lines from a SoCal catalog relocated by Lin, Shearer, and
 * Hauksson. This reader records the following data for events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 *
 * <i>Notes:</i>
 *
 * @author Peter Powers
 * @version $Id: Reader_LSH.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class Reader_LSH extends AbstractReader {

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
	public Reader_LSH(int size) {
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
        YYYY MM DD HH mm SS.sss      EVID      LAT        LON   DEPTH     M  NP  NS    rms                        cluster-errH    errZ    errH    errZ   t
        1981  1  2 18 54 12.730   3301624 33.03907 -116.39887   8.258  1.80  11   4   0.11   0   4  1007   119      19   0.200   0.700  99.000  99.000  le
        1981  1  2 20  8 40.920   3301626 35.04041 -117.68079   3.266  2.10  14   1   0.01   0   4    45  2172    8074   0.200   0.600   0.015   0.057  qb
        1981  1  3 23 53 33.050   3301631 34.10257 -117.20084   7.564  1.50  16   6   0.13   0   4   396    70     123   0.300   0.200   0.178   0.253  le
        1981  1  4  3 56 50.180   3301634 34.26740 -117.01360   5.882  1.10   9   1   0.08   1   4   927    78      48   0.400   0.400  99.000  99.000  le
        1981  1  4  7 11 34.920  12160459 33.53425 -116.79013   3.636  1.00   6   3   0.03   1   4   489   985     228   0.200   0.600   0.031   0.107  le
        0         1         2         3         4         5         6         7         8         9         0         1         2         3         4         5
        0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(0,4).trim()));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(5,7).trim()) -1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(8,10).trim()));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(11,13).trim()));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(14,16).trim()));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(17,19).trim()));
			cal.set(Calendar.MILLISECOND, Integer.parseInt(line.substring(20,23).trim()));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(63,67).trim()));

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(33,42).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(43,53).trim()));
			dat_depths.add(Double.parseDouble(line.substring(55,61).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(24,33).trim()));


		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
