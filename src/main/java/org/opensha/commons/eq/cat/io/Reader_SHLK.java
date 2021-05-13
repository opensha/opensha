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
 * Reader class for lines from a SoCal catalog relocated by Shearer, Hauksson
 * Lin and Kilb. This reader records the following data for events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 *
 * <i>Notes:</i> This is good for SHLK1.02. If older SHLK versions are imported,
 * some columns need to be shifted. See catalog readme. Can also be used to
 * import the Imperial Valley catalog which uses a different velocity model.
 *
 * @author Peter Powers
 * @version $Id: Reader_SHLK.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class Reader_SHLK extends AbstractReader {

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
	public Reader_SHLK(int size) {
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
        YYYY MM DD HH mm SS.sss      EVID      LAT        LON  DEPTH    M  NP  NS   rms  rmed                            errH    errZ t
        1984  1  1 19 28 47.955     28550 35.18440 -119.02270  0.780 1.60  15  11  0.17  0.04  2 0 0     0     0    0   0.000   0.000 l
        1984  1  1 19 29 27.328     28281 35.17560 -119.02250 15.890 1.67  11   5  0.11  0.07  2 0 0     0     0    0   0.000   0.000 l
        1984  1  1 19 38 19.311     28553 33.96348 -116.28541  3.848 2.13  24  10  0.13  0.02  4 0 1   193   372   36   0.020   0.046 l
        0         1         2         3         4         5         6         7         8         9         0         1         2         3
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
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
			dat_magnitudes.add(Double.parseDouble(line.substring(61,65).trim()));

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(33,42).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(43,53).trim()));
			dat_depths.add(Double.parseDouble(line.substring(54,60).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(24,33).trim()));


		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
