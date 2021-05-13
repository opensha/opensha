package org.opensha.commons.eq.cat.io;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE_TYPE;
import static org.opensha.commons.eq.cat.util.DataType.QUALITY;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.util.ArrayList;
import java.util.Calendar;

import org.opensha.commons.eq.cat.util.EventQuality;
import org.opensha.commons.eq.cat.util.MagnitudeType;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Reader class for lines from a Southern California Earthquake Center
 * Data Center (SCEDC 'Readable Format') catalog. This reader
 * records the following data for local events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_QUALITY</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 *     <li>CAT_DATA_MAGNITUDE_TYPE</li>
 * </ul>
 *
 * <i>Notes:</i> This reader is currently set to filter out non-local events,
 * lines shorter than 60 chars, and comment ('#') lines; the magnitude type is
 *  still recorded, even though all events are local.
 *
 * @author Peter Powers
 * @version $Id: Reader_SCEDC.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_SCEDC extends AbstractReader {

	private static final String NAME = "SCEDC Readable Format";
	private static final String DESC =
		"Parses a Southern California Earthquake Data Center " +
		"SCEDC format catalog. Reader will gather: event ID, time, " +
		"longitude, latitude, depth, location quality, magnitude, and " +
		"magnitude type.";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_SCEDC(int size) {
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
		dat_eventQuality   = new ArrayList<Integer>(size);
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
		catalog.addData(QUALITY, Ints.toArray(dat_eventQuality));
	}

	@Override
	public void parseLine(String line) {

		/** Sample data
        #YYY/MM/DD HH:mm:SS.ss ET MAG  M    LAT     LON     DEPTH Q  EVID      NPH NGRM
        2005/10/14 09:06:35.82 le 0.80 h    36.017 -118.353   5.5 C 10147461    9   75
        2005/10/14 09:07:31.86 le 1.32 l    34.196 -117.619   9.3 A 10147457   24  236
        2005/10/18 05:39:12.85 le 1.50 l    34.530 -116.273   5.4 C 10148409   20  225
        2005/10/18 07:31:03.47 le 4.37 l    34.012 -116.775  18.6 A 10148421  255 1776

        0         1         2         3         4         5         6         7         8
        012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;

			// eliminate comments, short lines (which occur in SCEDC
			// flat files by year), and non-local events
			if (line.startsWith("#") || (line.length() < 60) ||
					!line.substring(23,25).equals("le")) return;

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
			dat_magnitudes.add(Double.parseDouble(line.substring(26,30).trim()));
			dat_magnitudeTypes.add(MagnitudeType.parseSCEDC(line.substring(31,32)).id());

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(35,42).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(43,51).trim()));
			dat_depths.add(Double.parseDouble(line.substring(52,57).trim()));
			dat_eventQuality.add(EventQuality.parse(line.substring(58,59)).id());

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(60,68).trim()));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
