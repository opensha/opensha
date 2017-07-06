package org.opensha.commons.eq.cat.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.EVENT_ID;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;
import static org.opensha.commons.eq.cat.util.DataType.TIME;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Basic writer class. Class outputs formatted data from the following fields:<br/>
 * 
 * <ul>
 * <li>CAT_DATA_EVENT_ID</li>
 * <li>CAT_DATA_LONGITUDE</li>
 * <li>CAT_DATA_LATITUDE</li>
 * <li>CAT_DATA_DEPTH</li>
 * <li>CAT_DATA_MAGNITUDE</li>
 * <li>CAT_DATA_TIME</li>
 * </ul>
 * <br/>
 * Class currently uses a small external library (http://www.braju.com/) to
 * format lines for output (printf and fprintf functionality).
 * 
 * @author Peter Powers
 * @version $Id: Writer_Basic.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public class Writer_Basic extends AbstractWriter {

	private static final String NAME = "Basic Output Format";
	private static final String DESC = "Writes a catalog to file with "
		+ "EventID, lon, lat, depth, mag, and date.";

	// utility
	private PrintWriter writer;
	private SimpleDateFormat dateFormat = new SimpleDateFormat(
		"yyyy MM dd HH mm ss");
	private String eventFormat = "%09d %9.4f %8.4f %7.3f %4.2f %s\n";

	/**
	 * Constructs a new catalog file writer.
	 * 
	 * @throws IllegalArgumentException if <code>size</code> is less than 1
	 */
	public Writer_Basic() {
		super(NAME, DESC);
	}

	@Override
	public void initWriter(PrintWriter writer) {
		checkNotNull(writer, "Supplied PrintWriter is null");
		this.writer = writer;
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		dat_eventIDs = (int[]) catalog.getData(EVENT_ID);
		dat_dates = (long[]) catalog.getData(TIME);
		dat_longitudes = (double[]) catalog.getData(LONGITUDE);
		dat_latitudes = (double[]) catalog.getData(LATITUDE);
		dat_depths = (double[]) catalog.getData(DEPTH);
		dat_magnitudes = (double[]) catalog.getData(MAGNITUDE);
	}

	@Override
	public void writeLine(int index) throws IOException {
		checkArgument(index < catalog.size(), "Index [" + index
			+ "] is out of range for catalog");
		try {
			cal.setTimeInMillis(dat_dates[index]);
			Object[] dataOut = new Object[] { dat_eventIDs[index],
				dat_longitudes[index], dat_latitudes[index], dat_depths[index],
				dat_magnitudes[index], dateFormat.format(cal.getTime()) };
			writer.printf(eventFormat, dataOut);
		} catch (Exception e) {
			throw new IOException("Error writing catalog at line (" + index
				+ ")");
		}
	}

}
