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
 * Reader class for lines from an Advanced National Seismic System catalog as
 * retrieved from the NCEDC. Catalog is in NCEDC 'Readable Format'. This reader
 * records the following data for local events:<br/>
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
 * @version $Id: Reader_ANSS.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_ANSS extends AbstractReader {

	private static final String NAME = "ANSS Readable";
	private static final String DESC =
		"ANSS catalog reader will gather: " +
		"event ID, time, longitude, latitude, " +
		"depth, location quality, and magnitude,";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_ANSS(int size) {
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
        Date       Time             Lat       Lon  Depth   Mag Magt  Nst Gap  Clo  RMS  SRC   Event ID
        ----------------------------------------------------------------------------------------------
        1980/01/01 02:09:21.25  36.2478 -120.8188   6.52  3.65   Md   32  85    4 0.10  NC      -1049662
        1980/01/01 02:09:26.85  36.2533 -120.8178  10.88  3.20   ML   62  67   18 0.23  NC      -1049663
        1980/01/01 04:20:40.33  37.8628 -122.2353   8.34  2.18   Md   35  61   12 0.08  NC      -1049666
        1980/01/01 04:28:41.40  32.9000 -115.5000   5.00  3.00  Unk    4          0.00  PAS 198001014004
        1980/01/01 19:26:19.97  37.4525 -121.5290   6.56  2.41   Md   20  96   12 0.07  NC      -1049675
        1980/01/02 00:25:32.45  37.6445 -118.8763   1.08  2.18   Md    5 142    8 0.10  NC      -1049679
        0         1         2         3         4         5         6         7         8         9         1
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;

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

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(23,31).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(32,41).trim()));
			dat_depths.add(Double.parseDouble(line.substring(42,48).trim()));

			// id
			String id = line.substring(88,96).trim();
			dat_eventIDs.add((id.equals("")) ? 0 : Integer.parseInt(id));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}
}
