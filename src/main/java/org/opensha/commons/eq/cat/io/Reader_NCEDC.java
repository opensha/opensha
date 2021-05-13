package org.opensha.commons.eq.cat.io;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE_TYPE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.util.ArrayList;
import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.eq.cat.util.MagnitudeType;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Reader class for lines from a Northern California Earthquake Data Center
 * catalog (NCEDC 'Readable Format'). This reader records the following data
 * for local events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 *     <li>CAT_DATA_MAGNITUDE_TYPE</li>
 * </ul>
 *
 * <i>Notes:</i> This reader is currently set to filter out lines not
 * starting with a number (no comment symbol is used in header).
 *
 * @author Peter Powers
 * @version $Id: Reader_NCEDC.java 8066 2011-07-22 23:59:04Z kmilner $
 *
 */
public class Reader_NCEDC extends AbstractReader {

	private static final String NAME = "NCEDC Readable Format";
	private static final String DESC =
		"Parses a Northern California Earthquake Data Center " +
		"NCEDC format catalog. Reader will gather: event ID, time, " +
		"longitude, latitude, depth, magnitude, and " +
		"magnitude type.";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_NCEDC(int size) {
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
		dat_magnitudeTypes = new ArrayList<Integer>(size);
	}

	@Override
	public void loadData() {
		catalog.addData(EVENT_ID, Ints.toArray(dat_eventIDs));
		catalog.addData(TIME, Longs.toArray(dat_dates));
		catalog.addData(LONGITUDE, Doubles.toArray(dat_longitudes));
		catalog.addData(LATITUDE, Doubles.toArray(dat_latitudes));
		catalog.addData(DEPTH, Doubles.toArray(dat_depths));
		catalog.addData(MAGNITUDE, Doubles.toArray(dat_magnitudes));
		catalog.addData(MAGNITUDE_TYPE,  Ints.toArray(dat_magnitudeTypes));
	}

	@Override
	public void parseLine(String line) {

		/** Sample data
        Date       Time             Lat       Lon  Depth   Mag Magt  Nst Gap  Clo  RMS  SRC   Event ID
        ----------------------------------------------------------------------------------------------
        2002/02/10 01:51:05.96  36.7667 -121.4727   6.76  3.10   ML   63  34    6 0.10 NCSN   21212190
        2002/02/16 20:10:33.75  35.1800 -119.4055  20.84  3.03   Md   28  96   16 0.19 NCSN   21213006
        1981/12/01 16:36:04.23  38.3330 -117.7627   0.63  2.79   Md   11 202   4611.48 NCSN   -1069909
        2005/11/04 14:48:38.98  38.8317 -122.8047   2.18  1.21  Mlp   12 126    1 0.06 NCSN   69047519
        2005/11/04 16:28:22.22  38.7898 -122.8042   4.80  0.48  Mlp   13 164    0 0.01 NCSN   69047520
        0         1         2         3         4         5         6         7         8         9         1
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;

			// eliminate non-event lines
			if (!StringUtils.isNumeric(line.substring(0,1))) return;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(0,4)));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(5,7)) -1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(8,10)));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(11,13)));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(14,16)));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(17,19)));
			cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(20,22)) *10));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(50,54).trim()));
			dat_magnitudeTypes.add(MagnitudeType.parseNCEDC(line.substring(55,59).trim()).id());

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(23,31).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(32,41).trim()));
			dat_depths.add(Double.parseDouble(line.substring(42,48).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(85,94).trim()));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
