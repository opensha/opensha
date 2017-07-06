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
 * Reader class for lines from a catalog formatted with <code>Writer_Basic</code>.
 * This reader records the following data for local events:<br/>
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 *
 * <i>Notes:</i> This reader assumes no comment or otherwise odd lines.
 *
 * @author Peter Powers
 * @version $Id: Reader_Basic.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public class Reader_Basic extends AbstractReader {

    private static final String NAME = "Basic Format Reader";
    private static final String DESC =
        "Parses a simply formatted catalog. " +
        "Reader will gather: event ID, time, " +
        "longitude, latitude, depth, and magnitude.";

    private int count;

	/**
	 * Constructs a new catalog file reader that will use the supplied size
	 * to initialize internal data arrays.
	 *
	 * @param size to use when initializing internal data arrays
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Reader_Basic(int size) {
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
        #ID       LON        LAT      DEPTH  MAG  yyyy MM dd HH mm ss
        -01058774 -120.7083  36.4222  37.410 1.37 1981 01 01 08 49 39
        -01058781 -120.9427  36.1355   8.410 1.16 1981 01 01 11 45 16
        -01058783 -121.4362  36.7665   8.710 1.22 1981 01 01 13 52 25
        -01058785 -121.2480  36.6347   6.830 0.93 1981 01 01 16 48 16
        -01058788 -121.6315  37.3127   7.190 1.48 1981 01 01 17 56 47
        -01058793 -121.2908  36.6850   7.190 1.29 1981 01 01 19 46 25
        -01058796 -120.6462  35.1858   2.620 1.73 1981 01 01 22 29 39
        0         1         2         3         4         5         6
        01234567890123456789012345678901234567890123456789012345678901
        */

        try {
            count++;

            // date
            cal.set(Calendar.YEAR, Integer.parseInt(line.substring(42,46)));
            cal.set(Calendar.MONTH, Integer.parseInt(line.substring(47,49)) -1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(50,52)));
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(53,55)));
            cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(56,58)));
            cal.set(Calendar.SECOND, Integer.parseInt(line.substring(59,61)));
         	// needed or empty milliseconds field is only filled when getTime()
            // is called and uses the current time from epoch yielding
            // inconsistent results each time source data is parsed
            cal.set(Calendar.MILLISECOND, 0);
            dat_dates.add(cal.getTimeInMillis());
            
            // magnitude
            dat_magnitudes.add(Double.parseDouble(line.substring(37,41).trim()));

            // extents
            dat_latitudes.add(Double.parseDouble(line.substring(20,28).trim()));
            dat_longitudes.add(Double.parseDouble(line.substring(10,19).trim()));
            dat_depths.add(Double.parseDouble(line.substring(29,36).trim()));

            // id
            dat_eventIDs.add(Integer.parseInt(line.substring(0,9).trim()));

        } catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
        }
    }

}
