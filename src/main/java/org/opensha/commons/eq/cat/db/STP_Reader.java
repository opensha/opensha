package org.opensha.commons.eq.cat.db;

import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.eq.cat.MutableCatalog;
import org.opensha.commons.eq.cat.io.AbstractReader;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Custom reader class for STP. Class does not use <code>processFile()</code>
 * to call abstract implementations but is managed by an STP_Client. Clients
 * construct a reader, call <code>parseLine()</code> as neceessary and finally
 * <code>getCatalog()</code>. Implementors should not call
 * <code>initReader()</code> or <code>loadData()</code>.
 *
 * @author Peter Powers
 * @version $Id: STP_Reader.java 8066 2011-07-22 23:59:04Z kmilner $
 */
public class STP_Reader extends AbstractReader {

	private static final String NAME = "STP (Seismic Transfer Protocol) Reader";
	private static final String DESC =
		"Reads ID, date, lat, lon, depth, and magnitude from " +
		"STP data stream";

    private static final String STP_DATE_FORMAT = "yyyy/MM/dd,kk:mm:ss.SSS";
    // SDF not synchronized, one instance per reader
    private SimpleDateFormat stpDateFormat;
    private int count = 0;

    /**
     * Constructs a new STP request reader.
     * @param size initial size of import data arrays
     */
    public STP_Reader(int size) {
        super(NAME, DESC, size);
        catalog = new MutableCatalog();
        stpDateFormat = new SimpleDateFormat(STP_DATE_FORMAT);
        stpDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        initReader();
    }

    /**
     * Returns the catalog created by this reader. Call once all parseLine()
     * requests are complete.
     *
     * @return the catalog
     */
    public MutableCatalog getCatalog() {
        loadData();
        return (MutableCatalog) catalog;
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
        #     EVID ET YYYY/MM/DD HH:mm:SS.sss   LAT       LON        DEPTH MAG   MT   Q
           9819221 le 2002/09/04,12:24:15.340   30.0948   -116.8223   7.00  4.15  l 0.3
           9820489 le 2002/09/07,00:02:10.910   36.0505   -117.8767   3.32  3.53  l 1.0
          13813696 le 2002/09/17,15:00:05.170   33.5032   -116.7787  15.99  3.73  l 1.0
          13820088 le 2002/09/19,06:21:04.930   34.8662   -116.3953   3.51  3.84  l 1.0
        0         1         2         3         4         5         6         7         8
        012345678901234567890123456789012345678901234567890123456789012345678901234567890
        */
        String[] event_dat = StringUtils.split(line);

        try {
            count++;

            cal.setTime(stpDateFormat.parse(event_dat[2]));
            dat_dates.add(cal.getTimeInMillis());

            dat_eventIDs.add(Integer.parseInt(event_dat[0]));
            dat_longitudes.add(Double.parseDouble(event_dat[4]));
            dat_latitudes.add(Double.parseDouble(event_dat[3]));
            dat_depths.add(Double.parseDouble(event_dat[5]));
            dat_magnitudes.add(Double.parseDouble(event_dat[6]));

        } catch (Exception e) {
			throw new IllegalArgumentException(
				"Error reading catalog file format at line: " + count);
        }
    }

}
