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
 * Reader class for lines from a Taiwanese catalog; relocations by Wu et al.
 * (2008). This reader records the following data for local events:<br/>
 * <ul>
 *     <li>CAT_DATA_TIME</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 * </ul>
 * 
 * @author Peter Powers
 * @version $Id: Reader_TW_RELOC.java 8066 2011-07-22 23:59:04Z kmilner $
 * 
 */
public class Reader_TW_RELOC extends AbstractReader {

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
	public Reader_TW_RELOC(int size) {
		super(NAME, DESC, size);
	}

	@Override
	public void initReader() {
		count = 0;
		dat_dates          = new ArrayList<Long>(size);
		dat_longitudes     = new ArrayList<Double>(size);
		dat_latitudes      = new ArrayList<Double>(size);
		dat_depths         = new ArrayList<Double>(size);
		dat_magnitudes     = new ArrayList<Double>(size);
	}

	@Override
	public void loadData() {
		catalog.addData(TIME, Longs.toArray(dat_dates));
		catalog.addData(LONGITUDE, Doubles.toArray(dat_longitudes));
		catalog.addData(LATITUDE, Doubles.toArray(dat_latitudes));
		catalog.addData(DEPTH, Doubles.toArray(dat_depths));
		catalog.addData(MAGNITUDE, Doubles.toArray(dat_magnitudes));
	}
    
    @Override
    public void parseLine(String line) {
        
        /** Sample data
        Date        Time        Lat     Lon       Depth Mag Nst  Dst  Gap rms   e_H  e_Z    #ph Q
        --------------------------------------------------------------------------------------------
        2005/12/27  5:00  59.13 23.7538 121.5903  49.79 2.54  9  17.4 233 0.17  1.0  0.8  F  18 C
        2005/12/27  5:25  18.54 23.4170 122.1108  12.39 4.47 78  64.5 132 0.16  0.1  0.0  F 132 C
        2005/12/27  5:30  55.11 23.0077 121.5010  21.52 2.44 13  17.2 250 0.13  0.2  0.1  F  20 C
        2005/12/27  6:05   7.09 24.8167 122.0225  97.93 3.60 33  21.5 208 0.13  0.1  0.1  F  63 C
        2005/12/27  7:38  14.42 24.8508 122.4052  13.53 2.99 21  45.8 127 0.20  0.1  0.1  F  34 C
        2005/12/27  8:28  30.15 24.3392 122.5635  39.95 4.47 69  47.3  98 0.11  0.0  0.0  F 123 B
        0         1         2         3         4         5         6         7         8         9 
        01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901
        */

        try {
            count++;
            
            // eliminate non-event lines
            if (!StringUtils.isNumeric(line.substring(0,1))) return;
            
            // date
            cal.set(Calendar.YEAR, Integer.parseInt(line.substring(0,4).trim()));
            cal.set(Calendar.MONTH, Integer.parseInt(line.substring(5,7).trim()) -1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(line.substring(8,10).trim()));
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line.substring(11,13).trim()));
            cal.set(Calendar.MINUTE, Integer.parseInt(line.substring(14,16).trim()));
            cal.set(Calendar.SECOND, Integer.parseInt(line.substring(18,20).trim()));
            cal.set(Calendar.MILLISECOND, (Integer.parseInt(line.substring(21,23).trim()) *10));
            dat_dates.add(cal.getTimeInMillis());
            
            // magnitude
            dat_magnitudes.add(Double.parseDouble(line.substring(48,52).trim()));
            
            // extents
            dat_latitudes.add(Double.parseDouble(line.substring(23,31).trim()));
            dat_longitudes.add(Double.parseDouble(line.substring(31,40).trim()));
            dat_depths.add(Double.parseDouble(line.substring(42,47).trim()));
            
        } catch (Exception e) {
        	// TODO stack trace to log
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
        }
    }

}
