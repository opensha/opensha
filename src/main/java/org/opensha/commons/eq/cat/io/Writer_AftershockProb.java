package org.opensha.commons.eq.cat.io;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.opensha.commons.eq.cat.*;

/**
 * Basic writer class. Class outputs formatted data from the following fields:<br/>
 * 
 * <ul>
 *     <li>CAT_DATA_EVENT_ID</li>
 *     <li>CAT_DATA_LONGITUDE</li>
 *     <li>CAT_DATA_LATITUDE</li>
 *     <li>CAT_DATA_DEPTH</li>
 *     <li>CAT_DATA_MAGNITUDE</li>
 *     <li>CAT_DATA_AFTEERSHOCK_PROBABILITY</li>
 *     <li>CAT_DATA_TIME</li>
 * </ul>
 * <br/>
 * Class currently uses a small external library (http://www.braju.com/) to format
 * lines for output (printf and fprintf functionality).
 * 
 * @author Peter Powers
 * @version $Id: Writer_AftershockProb.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
@Deprecated
public class Writer_AftershockProb extends AbstractWriter {

    private static final String NAME = 
        "Basic Format with Aftershock Probabilities";
    private static final String DESC =
        "Writes a catalog to file with EventID, lon, lat, depth, " +
        "mag, date, and aftershock probability.";
    
    // utility
    private PrintWriter writer;
    private SimpleDateFormat dateFormat = 
        new SimpleDateFormat("yyyy MM dd HH mm ss");
    private String eventFormat = "%09d %9.4f %8.4f %7.3f %4.2f %s %e\n";
    
    
    public Writer_AftershockProb() {
    	super(NAME, DESC);
    }
    
    @Override
    public void initWriter(PrintWriter writer) {
        this.writer = writer;
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
//        try {
//            dat_eventIDs = 
//                (int[])catalog.getData(Catalog.CAT_DATA_EVENT_ID);
//            dat_dates = 
//                (long[])catalog.getData(Catalog.CAT_DATA_TIME);
//            dat_longitudes = 
//                (float[])catalog.getData(Catalog.CAT_DATA_LONGITUDE);
//            dat_latitudes = 
//                (float[])catalog.getData(Catalog.CAT_DATA_LATITUDE);
//            dat_depths = 
//                (float[])catalog.getData(Catalog.CAT_DATA_DEPTH);
//            dat_magnitudes = 
//                (float[])catalog.getData(Catalog.CAT_DATA_MAGNITUDE);
//            dat_aftershockProb = 
//                (float[])catalog.getData(
//                        TriggeringCatalog.CAT_DATA_AFTERSHOCK_PROBABILITY);
//        } catch (CatalogException ce) {
//            throw ce;
//        }
    }
    
    @Override
    public void writeLine(int index) {
//        try {
//            
//            cal.setTimeInMillis(dat_dates[index]);
//            
//            Object[] dataOut = new Object[] {
//                    dat_eventIDs[index],
//                    dat_longitudes[index],
//                    dat_latitudes[index],
//                    dat_depths[index],
//                    dat_magnitudes[index],
//                    dateFormat.format(cal.getTime()),
//                    dat_aftershockProb[index] };
//            writer.printf(eventFormat, dataOut);
//            
//        } catch (Exception e) {
//            throw new CatalogException(
//                    "Writer: Error writing Basic format at line (" 
//                    + index + ")",
//                    index);
//        }
    }

}
