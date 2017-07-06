package org.opensha.commons.eq.cat.io;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.QUALITY;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.util.ArrayList;
import java.util.Calendar;

import org.opensha.commons.eq.cat.util.EventQuality;
import org.opensha.commons.geo.GeoTools;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Reader class for lines from a Southern California Seismic Network (SCSN) catalog.
 * This format is also sometimes referred to as 'CATREAD' or 'CALTECH'.
 * This reader records the following data for events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_QUALITY</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 *
 * <i>Notes:</i> This Reader is currently set to skip comment ('#') lines and lines
 * shorter than 60 chars; magnitude-type information is not provided with this format.
 *
 * @author Peter Powers
 * @version $Id: Reader_SCSN.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_SCSN extends AbstractReader {

	private static final String NAME = "Southern California Seismic Network (SCSN)";
	private static final String DESC =
		"Parses a Southern California Seismic Network (SCSN a.k.a " +
		"CATREAD or CALTECH) format catalog. Reader will gather: event ID, time, " +
		"longitude, latitude, depth, location quality, and magnitude.";

	private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_SCSN(int size) {
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
		catalog.addData(QUALITY, Ints.toArray(dat_eventQuality));
	}

	@Override
	public void parseLine(String line) {

		/** Sample data
        #YYY MM DD  HH mm SS.ss  LATITUDE LONGITUDE Q MAG     DEPTH NPH    RMS  EVID
        2005 12 17  00 00 56.13  34  0.24-116 18.74 A 1.5      9.89 23     0.10 14204708
        2005 12 17  00 54 44.10  35  7.31-117 33.46 A 2.2      0.76 48     0.17 14204712
        2005 12 17  02 15 27.49  34 14.38-117 26.81 A 3.5     10.70192     0.28 14204720
        2005 12 17  02 17 02.92  34 14.39-117 27.19 A 3.0     13.19 52     0.21 14204724
        0         1         2         3         4         5         6         7         8
        012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {
			count++;
			// eliminate comments, short lines (which occur in SCEDC
			// flat files by year), and non-local events
			if (line.startsWith("#") || (line.length() < 60)) return;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(0,4)));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(5,7)) -1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(8,10)));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(12,14)));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(15,17)));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(18,20)));
			cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(21,23)) *10));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(46,49).trim()));

			// extents
			dat_latitudes.add(
				GeoTools.toDecimalDegrees(
					Double.parseDouble(line.substring(24,27).trim()),
					Double.parseDouble(line.substring(28,33).trim()) ));
			dat_longitudes.add(
				GeoTools.toDecimalDegrees(
					Double.parseDouble(line.substring(33,37).trim()),
					Double.parseDouble(line.substring(38,43).trim()) ));
			dat_depths.add(Double.parseDouble(line.substring(53,59).trim()));
			dat_eventQuality.add(EventQuality.parse(line.substring(44,45)).id());

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(72,80).trim()));


		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
