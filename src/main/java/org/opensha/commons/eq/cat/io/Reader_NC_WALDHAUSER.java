package org.opensha.commons.eq.cat.io;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.util.ArrayList;
import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;

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
 * @version $Id: Reader_NC_WALDHAUSER.java 8066 2011-07-22 23:59:04Z kmilner $
 *
 */
public class Reader_NC_WALDHAUSER extends AbstractReader {

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
	public Reader_NC_WALDHAUSER(int size) {
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

		/**
    	Sample data
        YYYY MM DD HH MM SS.sss    lat        lon          dep      eh1     eh2   az    ez     mag   EventID
        -----------------------------------------------------------------------------------------------------
    	1984  1  1  1 19 11.322    36.08678   -120.23164   10.963   0.088   0.030  88   0.077  1.8   -1109403
        1984  1  1  1 58  2.432    36.87623   -120.90443    3.126   0.122   0.034  46   0.790  0.0        346
        1984  1  1  1 59 27.084    36.86955   -120.90911    1.408   0.147   0.046  61   1.282  1.5   -1109406
        1984  1  1  2 28  4.250    37.51576   -118.75564    7.427   0.034   0.021  44   0.046  1.2   -1109408
        1984  1  1  3  8 58.221    40.57806   -124.53957   20.005   1.836   0.129  99   0.104  2.0   -1109409
        1984  1  1  3 15 36.690    37.56104   -118.84277   10.414   0.040   0.027  25   0.051  1.1   -1109410
        1984  1  1  4 46 38.797    38.80844   -122.84272    1.991   0.059   0.052 150   0.099  1.0   -1109412
        0         1         2         3         4         5         6         7         8         9         0
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		 */

		try {

			count++;

			// eliminate non-event lines
			if (!StringUtils.isNumeric(line.substring(0,1))) return;

			// date
			cal.set(Calendar.YEAR, Integer.parseInt(line.substring(0,4).trim()));
			cal.set(Calendar.MONTH, Integer.parseInt(line.substring(5,7).trim()) - 1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(8,10).trim()));
			cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(11,13).trim()));
			cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(14,16).trim()));
			cal.set(Calendar.SECOND, Integer.parseInt(line.substring(17,19).trim()));
			cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(20,23).trim())));
			dat_dates.add(cal.getTimeInMillis());

			// magnitude
			dat_magnitudes.add(Double.parseDouble(line.substring(87,90).trim()));

			// extents
			dat_latitudes.add(Double.parseDouble(line.substring(26,35).trim()));
			dat_longitudes.add(Double.parseDouble(line.substring(38,48).trim()));
			dat_depths.add(Double.parseDouble(line.substring(50,57).trim()));

			// id
			dat_eventIDs.add(Integer.parseInt(line.substring(91,101).trim()));

		} catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
		}
	}

}
