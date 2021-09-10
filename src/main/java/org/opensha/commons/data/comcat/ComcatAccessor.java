package org.opensha.commons.data.comcat;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.Format;
import gov.usgs.earthquake.event.JsonEvent;
import gov.usgs.earthquake.event.OrderBy;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.URL;
import javax.net.ssl.SSLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Locale;

import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.util.zip.ZipException;


/**
 * Class for making queries to Comcat.
 * Author: Michael Barall.
 * Includes code from an earlier version written by other author(s).
 *
 *     ********** READ THIS **********
 *
 * 1. DO NOT OVERLOAD COMCAT.  Comcat is a shared resource used by people world-wide.
 * This class can send a large number of queries to Comcat in rapid succession.
 * Doing so would have a negative effect on the operation of Comcat.  So don't do that.
 *
 * 2. COMCAT IS NOT 100% RELIABLE.  Sometimes Comcat will fail to respond to a query.
 * Sometimes it will respond after a significant delay.  Any code using this class
 * must be prepared to handle these situations.
 *
 * 3. THE COMCAT DATABASE CHANGES OVER TIME.  Earthquake magnitudes, locations, and
 * times can change.  Earthquakes can be renamed, deleted, undeleted, merged, and split.
 * Any code using this class must accept that querying Comcat at different times can
 * yield different results.
 *
 * 4. DO NOT MODIFY THIS CLASS.  If you want to change something, the correct procedure
 * is to write a subclass and override whatever methods you want to change.  See
 * org.opensha.oaf.comcat.ComcatOAFAccessor for an example.
 * Because subclasses depend on the internal design of this class remaining stable,
 * any changes carry a risk of breaking subclasses.  If you have a need that can not
 * be met through subclassing, please contact the author before making any changes.
 *
 *
 * The main entry points to this class are:
 *
 *   fetchEvent - Retrieves information about an individual earthquake.
 *
 *   fetchEventList - Retrieves a list of all earthquakes in a given geographic region,
 *                    time interval, depth range, and magnitude range.
 *
 *   fetchAftershocks - Similar to fetchEventList, except you can specify the time interval
 *                      using days after the mainshock, instead of absolute time.
 *
 * There are also some utility functions:
 *
 *   extendedInfoToMap - Stores extended rupture information into a java.util.Map.
 *
 *   idsToList - Parses a list of event IDs received from Comcat into a java.util.List<String>.
 *
 *   rupToString - Produces a String reprsentation of an ObsEqkRupture object.
 */
public class ComcatAccessor {

	// Flag is set true to write progress to System.out.
	
	protected boolean D = true;

	// The Comcat service provider.
	
	protected EventWebService service;

	// The list of HTTP status codes for the current operation.

	protected ArrayList<Integer> http_statuses;

	// HTTP status code for a locally completed operation.
	// If zero, then HTTP status must be obtained from the service provider.

	protected int local_http_status;

	// The geojson returned from the last fetchEvent, or null if none.

	protected JsonEvent last_geojson;

	// Indicates if queries to a secondary id should be refetched using the primary id.
	// Note: When an event has multiple ids, on rare occasions Comcat may return different
	// responses for different ids.  An application that cares can set this to true, so
	// that responses always come from queries on the primary id.  (Default is false.)

	protected boolean refetch_secondary;




	/**
	 * Turn verbose mode on or off.
	 */
	public void set_verbose (boolean f_verbose) {
		D = f_verbose;
		return;
	}



	
	// Construct an object to be used for accessing Comcat.

	public ComcatAccessor () {
		this (true);
	}




	// Construct an object to be used for accessing Comcat.
	// If f_create_service is true, then create a default web service with default URL.
	// Otherwise, do not create any service, in which case the subclass must create the service.
	// This constructor is intended for use by subclasses.

	protected ComcatAccessor (boolean f_create_service) {

		// Establish verbose mode

		D = true;

		// Get the Comcat service provider, if desired

		if (f_create_service) {

			try {
				//service = new EventWebService(new URL("https://earthquake.usgs.gov/fdsnws/event/1/"));
				//service = new ComcatEventWebService(new URL("https://earthquake.usgs.gov/fdsnws/event/1/"));
				URL serviceURL = new URL ("https://earthquake.usgs.gov/fdsnws/event/1/");
				URL feedURL = new URL ("https://earthquake.usgs.gov/earthquakes/feed/v1.0/detail/");
				service = new ComcatEventWebService(serviceURL, feedURL);
			} catch (MalformedURLException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}

		}

		// Set up HTTP status reporting

		http_statuses = new ArrayList<Integer>();
		local_http_status = -1;

		last_geojson = null;

		refetch_secondary = false;
	}

	/**
	 * @return EventWebService if intialized, otherwise null
	 */
	public EventWebService getEventWebService() {
		return service;
	}


	// The number of milliseconds in a day.
	
	public static final double day_millis = 24d*60d*60d*1000d;

	// Parameter name for the place description.

	public static final String PARAM_NAME_DESCRIPTION = "description";

	// Parameter name for the event id list.

	public static final String PARAM_NAME_IDLIST = "ids";

	// Parameter name for the network.

	public static final String PARAM_NAME_NETWORK = "net";

	// Parameter name for the event code.

	public static final String PARAM_NAME_CODE = "code";

	// Maximum depth allowed in Comcat searches, in kilometers.

	public static final double COMCAT_MAX_DEPTH = 1000.0;

	// Minimum depth allowed in Comcat searches, in kilometers.

	public static final double COMCAT_MIN_DEPTH = -100.0;

	// Maximum number of events that can be requested in a single call.

	public static final int COMCAT_MAX_LIMIT = 20000;

	// Initial offset into search results.

	public static final int COMCAT_INIT_INDEX = 1;

	// Maximum number of calls permitted in a single operation.

	public static final int COMCAT_MAX_CALLS = 25;

	// Value to use for no minimum magnitude in Comcat searches.

	public static final double COMCAT_NO_MIN_MAG = -10.0;

	// Default maximum depth for Comcat searches, in kilometers.
	// This is chosen to respect the limits for both Comcat (1000.0 km) and OpenSHA (700.0).

	public static final double DEFAULT_MAX_DEPTH = 700.0;

	// Default minimum depth for Comcat searches, in kilometers.
	// This is chosen to respect the limits for both Comcat (-100.0 km) and OpenSHA (-5.0).

	public static final double DEFAULT_MIN_DEPTH = -5.0;

	// The following four parameters control how to break up a large query (exceeding the size limit)
	// into a series of smaller queries.  Ideally, this would be done simply by incrementing the
	// offset value by the size limit after each query.  But currently (as of 06/2019) that approach
	// does not work reliably.  So these parameters offer some options.
	//
	// Multi-query operations can be handled either by increasing the offset value after each query,
	// or by descreasing the end time after each query.
	//
	// To increase the offset value after each query, set OVERLAP_TIME = -1L.  In this case, you
	// can set OVERLAP_FRACTION = 0 for each succeeding query to begin after the previous query,
	// or OVERLAP_FRACTION > 1 to allow for some overlap between successive queries (i.e., events
	// at the end of the previous query are re-fetched at the beginning of the next query.)
	// Overlap provides increased assurance that all matching events are found.
	//
	// To decrease the end time after each query, set OVERLAP_TIME >= 0L.  A positive value
	// creates that much overlap between the time intervals of successive queries.  If
	// OVERLAP_RESAMPLE > 1 then each query that returns a number of events equal to or close
	// to the size limit is repeated with the start time increased, so that all returned events
	// come from queries that are comfortably below the size limit.

	// The fraction (i.e., value 10 represents 1/10) of count that is kept as offset overlap between queries in a multi-query operation.
	// This is meant to compensate for the offset= parameter not being honored exactly.
	// (Set to zero if offset overlap is not desired.)
	// Note: This is ignored if OVERLAP_TIME >= 0L.

	public static final int OVERLAP_FRACTION = 10;

	// The minimum limit needed for offset overlap to be applied between queries in a multi-query operation.
	// (Set to a very large value, larger than the Comcat size limit, if offset overlap is not desired.)
	// Note: This is ignored if OVERLAP_TIME >= 0L.

	public static final int OVERLAP_MIN_LIMIT = 10000;

	// The time, in milliseconds, of time overlap between queries in a multi-query operation.
	// (Set to -1L if multi-query operations use offset.)
	// Note: If OVERLAP_TIME >= 0L then multi-query operations are done by adjusting the end time
	// of each query.  Otherwise, they are done by adjusting the offset parameter.

	public static final long OVERLAP_TIME = 60000L;

	// Control resampling, when time overlap is being used between queries in a multi-query operation.
	// The value is the reciprocal of a fraction.  If less than that fraction of the limit is
	// remaining, then Comcat is resampled by adjusting the start time.
	// (Set to zero if resampling is not desired, or if time overlap is not being used.)
	// Note: This is ignored if OVERLAP_TIME < 0L.

	public static final int OVERLAP_RESAMPLE = 0;
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return means the event is either not found or deleted in Comcat.
	 * A ComcatException means that there was an error accessing Comcat.
	 * Note: This entry point always returns extended information.
	 */
	public ObsEqkRupture fetchEvent(String eventID, boolean wrapLon) {
		return fetchEvent(eventID, wrapLon, true, false);
	}
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return means the event is either not found or deleted in Comcat.
	 * A ComcatException means that there was an error accessing Comcat.
	 */
	public ObsEqkRupture fetchEvent (String eventID, boolean wrapLon, boolean extendedInfo) {
		return fetchEvent (eventID, wrapLon, extendedInfo, false);
	}
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param superseded = True to include superseded and deletion products in the geojson.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return means the event is either not found or deleted in Comcat.
	 * A ComcatException means that there was an error accessing Comcat.
	 * Note: This function is overridden in org.opensha.oaf.comcat.ComcatOAFAccessor.
	 */
	public ObsEqkRupture fetchEvent (String eventID, boolean wrapLon, boolean extendedInfo, boolean superseded) {

		// Initialize HTTP statuses

		http_statuses.clear();
		local_http_status = -1;

		last_geojson = null;

		// Set up query on event id

		ComcatEventQuery query = new ComcatEventQuery();
		query.setEventId(eventID);

		// Ask for superseded and deletion products if desired

		if (superseded) {
			query.setIncludeSuperseded (true);
		}

		// Call Comcat to get the list of events satisfying the query

		List<JsonEvent> events = getEventsFromComcat (query);

		// If no events received, then not found

		if (events.isEmpty()) {
			return null;
		}

		// Error if more than one event was returned

		if (events.size() != 1) {
			reportQueryError          ("ComcatAccessor: Received more than one match, count = " + events.size(), null);
			throw new ComcatException ("ComcatAccessor: Received more than one match, count = " + events.size());
		}
		
		JsonEvent event = events.get(0);

		// If we are refetching events following query on a secondary id ...

		if (refetch_secondary) {

			// Get the authoritative event id

			String auth_id = null;
			try {
				auth_id = event.getEventId().toString();
			} catch (Exception e) {
				auth_id = null;
			}

			if (auth_id == null || auth_id.isEmpty()) {
				return null;	// treat as not-found if there is no authoritative id
			}

			// If the authoritative id is not our query id ...

			if (!( auth_id.equals (eventID) )) {
			
				// Set up query on event id

				query = new ComcatEventQuery();
				query.setEventId(auth_id);

				// Ask for superseded and deletion products if desired

				if (superseded) {
					query.setIncludeSuperseded (true);
				}

				// Call Comcat to get the list of events satisfying the query

				events = getEventsFromComcat (query);

				// If no events received, then not found

				if (events.isEmpty()) {
					return null;
				}

				// Error if more than one event was returned

				if (events.size() != 1) {
					reportQueryError          ("ComcatAccessor: Received more than one match, count = " + events.size(), null);
					throw new ComcatException ("ComcatAccessor: Received more than one match, count = " + events.size());
				}
		
				event = events.get(0);

				// At this point we need to check that eventID is one of the ids returned in the geojson.
				// Fortunately, eventToObsRup() will perform the check.
			}
		}

		// Save the geojson so that the caller can retrieve it

		last_geojson = event;

		// Convert event to our form, treating any failure as if nothing was returned

		ObsEqkRupture rup = null;

		try {
			rup = eventToObsRup (event, wrapLon, extendedInfo, eventID);
		} catch (Exception e) {
			rup = null;
		}
		
		return rup;
	}



	
	/**
	 * Fetch all aftershocks of the given event. Returned list will not contain the mainshock
	 * even if it matches the query.
	 * @param mainshock = Mainshock.
	 * @param minDays = Start of time interval, in days after the mainshock.
	 * @param maxDays = End of time interval, in days after the mainshock.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @return
	 * Note: The mainshock parameter must be a return value from fetchEvent() above.
	 * Note: As a special case, if maxDays == minDays, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 */
	public ObsEqkRupList fetchAftershocks(ObsEqkRupture mainshock, double minDays, double maxDays,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon) {

		long eventTime = mainshock.getOriginTime();
		long startTime = eventTime + (long)(minDays*day_millis);
		long endTime = eventTime + (long)(maxDays*day_millis);

		String exclude_id = mainshock.getEventId();

		boolean extendedInfo = false;

		return fetchEventList (exclude_id, startTime, endTime,
								minDepth, maxDepth, region, wrapLon, extendedInfo,
								COMCAT_NO_MIN_MAG, COMCAT_MAX_LIMIT, COMCAT_MAX_CALLS);
	}



	
	/**
	 * Fetch all aftershocks of the given event. Returned list will not contain the mainshock
	 * even if it matches the query.
	 * @param mainshock = Mainshock.
	 * @param minDays = Start of time interval, in days after the mainshock.
	 * @param maxDays = End of time interval, in days after the mainshock.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @return
	 * Note: The mainshock parameter must be a return value from fetchEvent() above.
	 * Note: As a special case, if maxDays == minDays, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 */
	public ObsEqkRupList fetchAftershocks(ObsEqkRupture mainshock, double minDays, double maxDays,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, double minMag) {

		long eventTime = mainshock.getOriginTime();
		long startTime = eventTime + (long)(minDays*day_millis);
		long endTime = eventTime + (long)(maxDays*day_millis);

		String exclude_id = mainshock.getEventId();

		boolean extendedInfo = false;

		return fetchEventList (exclude_id, startTime, endTime,
								minDepth, maxDepth, region, wrapLon, extendedInfo,
								minMag, COMCAT_MAX_LIMIT, COMCAT_MAX_CALLS);
	}



	
	/**
	 * Fetch a list of events satisfying the given conditions.
	 * @param exclude_id = An event id to exclude from the results, or null if none.
	 * @param startTime = Start of time interval, in milliseconds after the epoch.
	 * @param endTime = End of time interval, in milliseconds after the epoch.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Maximum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @param limit_per_call = Maximum number of events to fetch in a single call to Comcat, or 0 for default.
	 * @param max_calls = Maximum number of calls to ComCat, or 0 for default.
	 * @return
	 * Note: As a special case, if endTime == startTime, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 */
	public ObsEqkRupList fetchEventList (String exclude_id, long startTime, long endTime,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, boolean extendedInfo,
			double minMag, int limit_per_call, int max_calls) {

		// The list of ruptures we are going to build

		final ObsEqkRupList rups = new ObsEqkRupList();

		// The visitor that builds the list

		ComcatVisitor visitor = new ComcatVisitor() {
			@Override
			public int visit (ObsEqkRupture rup, JsonEvent geojson) {
				rups.add(rup);
				return 0;
			}
		};

		// Visit each event

		String productType = null;

		visitEventList (visitor, exclude_id, startTime, endTime,
			minDepth, maxDepth, region, wrapLon, extendedInfo,
			minMag, productType, limit_per_call, max_calls);

		// Return the list
		
		return rups;
	}




	/**
	 * Fetch a list of events satisfying the given conditions.
	 * @param exclude_id = An event id to exclude from the results, or null if none.
	 * @param startTime = Start of time interval, in milliseconds after the epoch.
	 * @param endTime = End of time interval, in milliseconds after the epoch.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Maximum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @return
	 * Note: As a special case, if endTime == startTime, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 */
	public ObsEqkRupList fetchEventList (String exclude_id, long startTime, long endTime,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, boolean extendedInfo,
			double minMag) {

		return fetchEventList (exclude_id, startTime, endTime,
			minDepth, maxDepth, region, wrapLon, extendedInfo,
			minMag, COMCAT_MAX_LIMIT, COMCAT_MAX_CALLS);
	}



	
	/**
	 * Visit a list of events satisfying the given conditions.
	 * @param visitor = The visitor that is called for each event, cannot be null.
	 * @param exclude_id = An event id to exclude from the results, or null if none.
	 * @param startTime = Start of time interval, in milliseconds after the epoch.
	 * @param endTime = End of time interval, in milliseconds after the epoch.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Maximum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @param productType = Required product type, or null if none.
	 * @param limit_per_call = Maximum number of events to fetch in a single call to Comcat, or 0 for default.
	 * @param max_calls = Maximum number of calls to ComCat, or 0 for default.
	 * @return
	 * Returns the result code from the last call to the visitor.
	 * Note: As a special case, if endTime == startTime, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 * Note: This function is overridden in org.opensha.oaf.comcat.ComcatOAFAccessor.
	 */
	public int visitEventList (ComcatVisitor visitor, String exclude_id, long startTime, long endTime,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, boolean extendedInfo,
			double minMag, String productType, int limit_per_call, int max_calls) {

		// Initialize HTTP statuses

		http_statuses.clear();
		local_http_status = -1;

		// Check the visitor

		if (visitor == null) {
			throw new IllegalArgumentException ("ComcatAccessor.visitEventList: No visitor supplied");
		}

		// Start a query

		ComcatEventQuery query = new ComcatEventQuery();

		// Insert depth into query

		if (!( minDepth < maxDepth )) {
			throw new IllegalArgumentException ("ComcatAccessor: Min depth must be less than max depth: minDepth = " + minDepth + ", maxDepth = " + maxDepth);
		}
		
		query.setMinDepth(new BigDecimal(String.format(Locale.US, "%.3f", minDepth)));
		query.setMaxDepth(new BigDecimal(String.format(Locale.US, "%.3f", maxDepth)));

		// Insert time into query

		long timeNow = System.currentTimeMillis();

		if (!( startTime < timeNow )) {
			throw new IllegalArgumentException ("ComcatAccessor: Start time must be less than time now: startTime = " + startTime + ", timeNow = " + timeNow);
		}

		if (!( startTime <= endTime )) {
			throw new IllegalArgumentException ("ComcatAccessor: Start time must be less than end time: startTime = " + startTime + ", endTime = " + endTime);
		}

		query.setStartTime(new Date(startTime));

		long original_end_time;

		if (endTime == startTime) {
			query.setEndTime(new Date(timeNow));
			original_end_time = timeNow;
		} else {
			query.setEndTime(new Date(endTime));
			original_end_time = endTime;
		}

		long current_end_time = original_end_time;
		
		// If the region is a circle, use Comcat's circle query

		if (region.isCircular()) {
			query.setLatitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getCircleCenterLat())));
			query.setLongitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getCircleCenterLon())));
			query.setMaxRadius(new BigDecimal(String.format(Locale.US, "%.5f", region.getCircleRadiusDeg())));
		}

		// Otherwise, use Comcat's rectangle query to search the bounding box of the region

		else {
			query.setMinLatitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getMinLat())));
			query.setMaxLatitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getMaxLat())));
			query.setMinLongitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getMinLon())));
			query.setMaxLongitude(new BigDecimal(String.format(Locale.US, "%.5f", region.getMaxLon())));
		}

		// Set a flag to indicate if we need to do region filtering

		boolean f_region_filter = false;

		if (!( region.isRectangular() || region.isCircular() )) {
			f_region_filter = true;
		}

		// Insert minimum magnitude in the query

		if (minMag >= -9.0) {
			query.setMinMagnitude(new BigDecimal(String.format(Locale.US, "%.3f", minMag)));
		}

		// Insert product type in the query

		if (productType != null) {
			query.setProductType (productType);
		}

		// Set the sort order to descending origin time

		query.setOrderBy (OrderBy.TIME);

		// Calculate our limit and insert it in the query

		int my_limit = limit_per_call;

		if (my_limit <= 0) {
			my_limit = COMCAT_MAX_LIMIT;
		}

		query.setLimit(my_limit);

		// Calculate our maximum number of calls

		int my_max_calls = max_calls;

		if (my_max_calls <= 0) {
			my_max_calls = COMCAT_MAX_CALLS;
		}

		// Initialize the offset but don't insert in the query yet

		int offset = COMCAT_INIT_INDEX;

		// Set up the event id filter, to remove duplicate events and our excluded event

		HashSet<String> event_filter = new HashSet<String>();

		if (exclude_id != null) {
			event_filter.add (exclude_id);
		}

		// The number of events processed

		int total_count = 0;

		// Result code to return

		int result = 0;

		// The list of ruptures we are going to build

		ObsEqkRupList rups = new ObsEqkRupList();

		// Loop until we reach our maximum number of calls

		for (int n_call = 1; ; ++n_call) {

			// If not at start, insert offset into query

			if (offset != COMCAT_INIT_INDEX) {
				query.setOffset(offset);
			}

			// If end time has changed, insert end time into query

			if (current_end_time != original_end_time) {
				query.setEndTime(new Date(current_end_time));
			}

			//  // Display the query URL
			//  
			//  if (D) {
			//  	try {
			//  		System.out.println(service.getUrl(query, Format.GEOJSON));
			//  	} catch (MalformedURLException e) {
			//  		e.printStackTrace();
			//  	}
			//  }

			// Call Comcat to get the list of events satisfying the query

			List<JsonEvent> events = getEventsFromComcat (query);

			//  // Display the number of events received
			//  
			//  int count = events.size();
			//  if (D) {
			//  	System.out.println ("Count of events received = " + count);
			//  }

			// Get the number of events received

			int count = events.size();

			// Set flag indicating if this is the last query

			boolean f_done = false;

			// Stop if we didn't get all we asked for

			if (count < my_limit) {
				f_done = true;
			}

			// If resampling is desired ...

			if (OVERLAP_TIME >= 0L && OVERLAP_RESAMPLE > 1) {

				// The resampling threshold

				int resample_threshold = my_limit;
				resample_threshold -= (my_limit / OVERLAP_RESAMPLE);

				// If number of events exceeds the threshold ...

				if (count > resample_threshold) {

					// Accumulate the time of each returned earthquake

					long[] event_times = new long[count];
					int n = 0;

					for (JsonEvent event : events) {
						Date d = event.getTime();
						if (d != null) {
							event_times[n] = d.getTime();
							++n;
						}
					}

					// If the number of times exceeds the threshold ...

					if (n > resample_threshold) {

						// Sort the times into ascending order
						// (Comcat supposedly returns times in descending order,
						// but we don't rely on that.)

						Arrays.sort (event_times, 0, n);

						// Set the new start time

						long new_start_time = event_times[n - resample_threshold];
						query.setStartTime(new Date(new_start_time));

						// Call Comcat to get the list of events satisfying the query

						events = getEventsFromComcat (query);

						// Restore the original start time

						query.setStartTime(new Date(startTime));

						// This is not the last query

						f_done = false;
					}
				}
			}

			// Loop over returned events

			int filtered_count = 0;

			int filtered_by_conversion = 0;
			int filtered_by_location = 0;
			int filtered_by_id = 0;

			long last_received_time = current_end_time;

			for (JsonEvent event : events) {

				// Record the time

				Date received_time = event.getTime();
				if (received_time != null) {
					last_received_time = Math.min (last_received_time, received_time.getTime());
				}

				// Convert to our form

				ObsEqkRupture rup = null;

				try {
					rup = eventToObsRup (event, wrapLon, extendedInfo, null);
				} catch (Exception e) {
					rup = null;
				}

				// Skip this event if we couldn't convert it

				if (rup == null) {
					++filtered_by_conversion;
					continue;
				}

				// Do region filtering if required

				if (f_region_filter) {
					if (!( region.contains(rup.getHypocenterLocation()) )) {
						++filtered_by_location;
						continue;
					}
				}

				// Do event id filtering (must be the last filter done)

				if (!( event_filter.add (rup.getEventId()) )) {
					++filtered_by_id;
					continue;
				}

				// Visit the event

				result = visitor.visit (rup, event);
				++filtered_count;
				++total_count;

				// Stop if requested

				if (result != 0) {

					if (D) {
						System.out.println("Visitor requested stop, total number of events returned = " + total_count);
						if (filtered_by_conversion != 0 || filtered_by_location != 0 || filtered_by_id != 0) {
							System.out.println ("Events filtered due to conversion = " + filtered_by_conversion
												+ ", location = " + filtered_by_location
												+ ", id = " + filtered_by_id);
						}
					}
		
					return result;
				}
			}

			// Display the number of events that survived filtering

			if (D) {
				System.out.println ("Count of events after filtering = " + filtered_count);
				if (filtered_by_conversion != 0 || filtered_by_location != 0 || filtered_by_id != 0) {
					System.out.println ("Events filtered due to conversion = " + filtered_by_conversion
										+ ", location = " + filtered_by_location
										+ ", id = " + filtered_by_id);
				}
			}

			// Adjust the end time, if multi-query operations are being done via end time

			if (OVERLAP_TIME >= 0L) {

				// Adjust end time to the last time received, plus overlap, forcing end time to decrease

				current_end_time = Math.min (current_end_time - 1L, last_received_time + OVERLAP_TIME);

				// If exhausted time interval, stop

				if (current_end_time < startTime) {
					break;
				}
			}

			// Otherwise, multi-query operations are being done via offset

			else {

				// Advance the offset

				offset += count;

				// If offset overlap is desired, apply it

				if (OVERLAP_FRACTION > 1 && my_limit >= OVERLAP_MIN_LIMIT) {
					int overlap = count / OVERLAP_FRACTION;
					offset -= overlap;
				}
			}

			// Stop if this is the last query

			if (f_done) {
				break;
			}

			// If reached the maximum permitted number of calls, it's an error

			if (n_call >= my_max_calls) {
				reportQueryError          ("ComcatAccessor: Exceeded maximum number of Comcat calls in a single operation", null);
				throw new ComcatException ("ComcatAccessor: Exceeded maximum number of Comcat calls in a single operation");
			}

		}

		// Display final result
		
		if (D) {
			System.out.println("Total number of events returned = " + total_count);
		}
		
		return result;
	}



	
	/**
	 * Visit a list of events satisfying the given conditions.
	 * @param visitor = The visitor that is called for each event, cannot be null.
	 * @param exclude_id = An event id to exclude from the results, or null if none.
	 * @param startTime = Start of time interval, in milliseconds after the epoch.
	 * @param endTime = End of time interval, in milliseconds after the epoch.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Maximum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @param productType = Required product type, or null if none.
	 * @return
	 * Returns the result code from the last call to the visitor.
	 * Note: As a special case, if endTime == startTime, then the end time is the current time.
	 * Note: This function can retrieve a maximum of about 150,000 earthquakes.  Comcat will
	 * time out if the query matches too many earthquakes, typically with HTTP status 504.
	 */
	public int visitEventList (ComcatVisitor visitor, String exclude_id, long startTime, long endTime,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, boolean extendedInfo,
			double minMag, String productType) {

		return visitEventList (visitor, exclude_id, startTime, endTime,
			minDepth, maxDepth, region, wrapLon, extendedInfo,
			minMag, productType, COMCAT_MAX_LIMIT, COMCAT_MAX_CALLS);

	}




	// Convert a JsonEvent into an ObsEqkRupture.
	// If wrapLon is false, longitudes range from -180 to +180.
	// If wrapLon is true, longitudes range from 0 to +360.
	// If extendedInfo is true, extended information is added to the ObsEqkRupture,
	//  which presently is the place description, list of ids, network, and event code.
	// If query_id is non-null, then the event is dropped if the list of ids does not include query_id.
	//  This can be used to reject (probably rare) cases where Comcat does not include the queried
	//  event id in the list of ids (e.g., "us1000h4p4").
	// If the conversion could not be performed, then the function either returns null
	//  or throws an exception.  Note that the event.getXXXXX functions can return null,
	//  which will lead to NullPointerException being thrown.
	// Note: A subclass can override this method to add more extended information.
	
	protected ObsEqkRupture eventToObsRup (JsonEvent event, boolean wrapLon, boolean extendedInfo, String query_id) {
		double lat = event.getLatitude().doubleValue();
		double lon = event.getLongitude().doubleValue();

		GeoTools.validateLon(lon);
		if (wrapLon && lon < 0.0) {
			lon += 360.0;
			GeoTools.validateLon(lon);
		}

		double dep = event.getDepth().doubleValue();
		//if (dep < 0.0) {
		//	// some regional networks can report negative depths, but the definition of what they're relative to can vary between
		//	// networks (see http://earthquake.usgs.gov/data/comcat/data-eventterms.php#depth) so we decided to just discard any
		//	// negative depths rather than try to correct with a DEM (which may be inconsistant with the networks). More discussion
		//	// in e-mail thread 2/8-9/17 entitled "ComCat depths in OAF app"
		//	dep = 0.0;
		//}
		// (07/14/2021) We now permit negative depth. The change is motivated by the Antelope Valley
		// earthquake (nc73584926), which had several aftershocks reporting negative depth.
		Location hypo = new Location(lat, lon, dep);

		double mag=0.0;
		try{
			mag = event.getMag().doubleValue();
		}catch(Exception e){
			//System.out.println(event.toString());
			return null;
		}

		String event_id = event.getEventId().toString();
		if (event_id == null || event_id.isEmpty()) {
			return null;	// this ensures returned events always have an event ID
		}

		if (query_id != null) {
			if (!( idsToList(event.getIds(), event_id).contains(query_id) )) {
				return null;	// ensures returned events always contain the queried ID
			}
		}

		ObsEqkRupture rup = new ObsEqkRupture(event_id, event.getTime().getTime(), hypo, mag);
		
		if (extendedInfo) {
			// adds the place description ("10km from wherever"). Needed for ETAS_AftershockStatistics forecast document -NVDE 
			rup.addParameter(new StringParameter(PARAM_NAME_DESCRIPTION, event.getPlace()));
			// adds the event id list, which can be used to resolve duplicates
			rup.addParameter(new StringParameter(PARAM_NAME_IDLIST, event.getIds()));
			// adds the seismic network, which is needed for reporting to PDL
			rup.addParameter(new StringParameter(PARAM_NAME_NETWORK, event.getNet()));
			// adds the event code, which is needed for reporting to PDL
			rup.addParameter(new StringParameter(PARAM_NAME_CODE, event.getCode()));
		}
		
		return rup;
	}




	// Get the HTTP status code from the last operation, or -1 if unknown, or -2 if unable to connect.

	public int get_http_status_code () {
		if (local_http_status != 0) {
			return local_http_status;
		}
		if (service instanceof ComcatEventWebService) {
			return ((ComcatEventWebService)service).get_http_status_code();
		}
		return -1;
	}


	// Get the number of stored HTTP statuses available, for the last multi-event fetch.

	public int get_http_status_count () {
		return http_statuses.size();
	}


	// Get the i-th stored HTTP status, for the last multi-event fetch.

	public int get_http_status_code (int i) {
		return http_statuses.get(i).intValue();
	}




	// Get the URL from the last operation, or null if none or unknown.

	public URL get_last_url () {
		if (service instanceof ComcatEventWebService) {
			return ((ComcatEventWebService)service).get_last_url();
		}
		return null;
	}


	// Get the URL from the last operation as a string, or "<null>" if none or unknown.

	public String get_last_url_as_string () {
		URL result = get_last_url();
		return ((result == null) ? "<null>" : result.toString());
	}




	// Set the connection timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default, or -2 for the ComcatEventWebService default.
	// The call is ignored if the service is not ComcatEventWebService.

	public void setConnectTimeout (int the_connectTimeout) {
		if (service instanceof ComcatEventWebService) {
			((ComcatEventWebService)service).setConnectTimeout (the_connectTimeout);
		}
		return;
	}


	// Set the read timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default, or -2 for the ComcatEventWebService default.
	// The call is ignored if the service is not ComcatEventWebService.

	public void setReadTimeout (int the_readTimeout) {
		if (service instanceof ComcatEventWebService) {
			((ComcatEventWebService)service).setReadTimeout (the_readTimeout);
		}
		return;
	}


	// Set the timeout readback flag.
	// The timeout readback flag is false by default.
	// The call is ignored if the service is not ComcatEventWebService.

	public void setEnableTimeoutReadback (boolean the_enableTimeoutReadback) {
		if (service instanceof ComcatEventWebService) {
			((ComcatEventWebService)service).setEnableTimeoutReadback (the_enableTimeoutReadback);
		}
		return;
	}


	// Get the connection timeout from the last operation, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	public int getLastConnectTimeout () {
		if (service instanceof ComcatEventWebService) {
			return ((ComcatEventWebService)service).getLastConnectTimeout();
		}
		return -1;
	}


	// Get the read timeout from the last operation, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	public int getLastReadTimeout () {
		if (service instanceof ComcatEventWebService) {
			return ((ComcatEventWebService)service).getLastReadTimeout();
		}
		return -1;
	}




	// Get the geojson from the last fetchEvent, or null if none.
	// Note: JsonEvent is a subclass of org.json.simple.JSONObject.

	public JsonEvent get_last_geojson () {
		return last_geojson;
	}




	// Set the flag that controls whether queries to a secondary id are refetched using the primary id.

	public void set_refetch_secondary (boolean f_refetch_secondary) {
		refetch_secondary = f_refetch_secondary;
		return;
	}




	// Report an error during a Comcat operation.
	// Parameters:
	//  message = Message describing the error, cannot be null or empty.
	//  e = Exception thrown by service, can be null if error does not originate in the service.
	// Note: This function is intended only to report or log errors.
	// It is not intended to be used for any sort of error recovery.
	// (Error recovery should be done by catching ComcatException.)
	// This function should never throw an exception.
	// It is called when ComcatException is about to be thrown.

	protected void reportQueryError (String message, Exception e) {
		try {
			System.out.println (message);
		} catch (Exception e2) {
		}
		return;
	}
	



	/**
	 * Send a query to Comcat and return the matching list of events.
	 * @param query = Query to perform.
	 * @return
	 * Returns a list of events matching the query.
	 * If nothing matches the query, returns an empty list.
	 * The return is never null.
	 * If there is an error, throws ComcatException.
	 * The operation's HTTP status is added to the list of status codes.
	 *
	 * Implementation note:
	 * With Comcat, it is necessary to examine the HTTP status code to accurately tell
	 * the difference between an I/O error and a query that returns no data.  The
	 * ComcatEventWebService provider attempts to do so.  If HTTP status is not available
	 * (because the provider is not ComcatEventWebService, or the protocol stack did not
	 * obtain the HTTP status), this function makes an attempt to tell the difference,
	 * but the attempt does not succeed in all circumstances.
	 */
	protected List<JsonEvent> getEventsFromComcat (EventQuery query) {

		List<JsonEvent> events = null;
		local_http_status = 0;

			// Display the query URL

		if (D) {
			try {
				System.out.println (service.getUrl(query, Format.GEOJSON));
			} catch (MalformedURLException e) {
				System.out.println ("Comcat query with unknown URL");
			}
		}

		try {
			events = service.getEvents(query);

		} catch (SocketTimeoutException e) {
			// This exception (subclass of IOException) indicates a timeout connecting to or
			// reading from Comcat.  We use the HTTP status code to differentiate.
			if (get_http_status_code() == -1) {
				http_statuses.add (new Integer(get_http_status_code()));
				reportQueryError          ("ComcatAccessor: Timeout error (SocketTimeoutException) while accessing Comcat", e);
				throw new ComcatException ("ComcatAccessor: Timeout error (SocketTimeoutException) while accessing Comcat", e);
			}
			if (get_http_status_code() == -2) {
				http_statuses.add (new Integer(get_http_status_code()));
				reportQueryError          ("ComcatAccessor: Timeout error (SocketTimeoutException) while connecting to Comcat", e);
				throw new ComcatException ("ComcatAccessor: Timeout error (SocketTimeoutException) while connecting to Comcat", e);
			}
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: Timeout error (SocketTimeoutException) while reading data from Comcat", e);
			throw new ComcatException ("ComcatAccessor: Timeout error (SocketTimeoutException) while reading data from Comcat", e);

		} catch (UnknownServiceException e) {
			// This exception (subclass of IOException) indicates an I/O error.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: I/O error (UnknownServiceException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: I/O error (UnknownServiceException) while accessing Comcat", e);

		} catch (ZipException e) {
			// This exception (subclass of IOException) indicates a data error.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: Data error (ZipException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: Data error (ZipException) while accessing Comcat", e);

		} catch (UnknownHostException e) {
			// This exception (subclass of IOException) indicates bad host name, DNS failure, or connectivity issue.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: Unable to find host (UnknownHostException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: Unable to find host (UnknownHostException) while accessing Comcat", e);

		} catch (SSLException e) {
			// This exception (subclass of IOException) indicates a failure in setting up SSL.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: SSL error (SSLException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: SSL error (SSLException) while accessing Comcat", e);

		} catch (MalformedURLException e) {
			// This exception (subclass of IOException) indicates a bad query URL.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: Bad query URL (MalformedURLException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: Bad query URL (MalformedURLException) while accessing Comcat", e);

		} catch (FileNotFoundException e) {
			// If the HTTP status is unknown, then we don't know if this is error or not-found.
			// EventWebService typically throws this exception when an eventID is not found (in response
			// to Comcat HTTP status 404), so we treat it as not-found if the HTTP status is unknown.
			if (get_http_status_code() == -1) {
				events = new ArrayList<JsonEvent>();
			}
			// Otherwise it's an I/O error
			else {
				http_statuses.add (new Integer(get_http_status_code()));
				reportQueryError          ("ComcatAccessor: I/O error (FileNotFoundException) while accessing Comcat", e);
				throw new ComcatException ("ComcatAccessor: I/O error (FileNotFoundException) while accessing Comcat", e);
			}

		} catch (IOException e) {
			// If the HTTP status is unknown, then we don't know if this is error or not-found.
			// EventWebService typically throws this exception when an eventID refers to a deleted event
			// (in response to Comcat HTTP status 409), but we nonetheless treat it as an I/O error.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: I/O error (IOException) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: I/O error (IOException) while accessing Comcat", e);

		} catch (Exception e) {
			// An exception not an I/O exception probably indicates bad data received.
			http_statuses.add (new Integer(get_http_status_code()));
			reportQueryError          ("ComcatAccessor: Data error (Exception) while accessing Comcat", e);
			throw new ComcatException ("ComcatAccessor: Data error (Exception) while accessing Comcat", e);
		}

		// Add the HTTP status to the list

		http_statuses.add (new Integer(get_http_status_code()));

		// The event list should not be null, but if it is, replace it with an empty List

		if (events == null) {
			events = new ArrayList<JsonEvent>();
		}

		// Display the number of events received

		if (D) {
			System.out.println ("Count of events received = " + events.size());
		}

		// Return the list of events

		return events;
	}




	// Option codes for extendedInfoToMap.

	public static final int EITMOPT_NO_CHANGE = 0;			// Make no changes, include null and empty strings in map
	public static final int EITMOPT_OMIT_NULL = 1;			// Omit null strings from the map, but keep empty strings
	public static final int EITMOPT_OMIT_EMPTY = 2;			// Omit empty strings from the map, but keep null strings
	public static final int EITMOPT_OMIT_NULL_EMPTY = 3;	// Omit both null strings and empty strings from the map
	public static final int EITMOPT_NULL_TO_EMPTY = 4;		// Convert null strings to empty strings
	public static final int EITMOPT_EMPTY_TO_NULL = 5;		// Convert empty strings to null strings


	/**
	 * Extract the extended info from a ObsEqkRupture, and put all the values into a Map.
	 * @param rup = The ObsEqkRupture to examine.
	 * @param option = An option code, as listed above.
	 * @return
	 * Returns a Map whose keys are the the names of parameters (PARAM_NAME_XXXXX as
	 * defined above), and whose values are the strings returned by Comcat.
	 * Any non-string parameters are converted to strings.
	 */
	public static Map<String, String> extendedInfoToMap (ObsEqkRupture rup, int option) {
		HashMap<String, String> eimap = new HashMap<String, String>();

		// Loop over parameters containing extended info

		ListIterator<Parameter<?>> iter = rup.getAddedParametersIterator();
		if (iter != null) {
			while (iter.hasNext()) {
				Parameter<?> param = iter.next();

				// Get the name and value of the parameter

				String key = param.getName();
				String value = null;
				Object o = param.getValue();
				if (o != null) {
					value = o.toString();
				}

				// Handle null strings

				if (value == null) {
					switch (option) {

					case EITMOPT_OMIT_NULL:
					case EITMOPT_OMIT_NULL_EMPTY:
						continue;

					case EITMOPT_NULL_TO_EMPTY:
						value = "";
						break;
					}
				}

				// Handle empty strings

				else if (value.isEmpty()) {
					switch (option) {

					case EITMOPT_OMIT_EMPTY:
					case EITMOPT_OMIT_NULL_EMPTY:
						continue;

					case EITMOPT_EMPTY_TO_NULL:
						value = null;
						break;
					}
				}

				// Add to the map

				eimap.put (key, value);
			}
		}

		return eimap;
	}




	/**
	 * Convert an id list received from Comcat into a list of strings, each containing a single id.
	 * @param ids = The ids received from Comcat.  Can be null or empty if none received.
	 * @param preferred_id = A preferred id.  Can be null or empty if none desired.
	 * @return
	 * Returns a List of strings, each of which is an event id.
	 * The ids received from Comcat are a comma-separated list of event ids, with commas at
	 * the beginning and end of the list, for example: ",us1000edv8,hv70203677,".
	 * It is not clear if the ordering of ids in the list has any significance.
	 * This function treats the initial and final commas as optional, and allows spaces before
	 * and after a comma (which are removed).
	 * If preferred_id is non-null and non-empty, then preferred_id appears as the first
	 * item in the list (whether or not it also appears in ids).
	 * Except for preferred_id, the event ids are listed in the same order they appear in ids.
	 * It is guaranteed that the returned list contains no duplicates, even in the unlikely
	 * (maybe impossible) event that Comcat returns a list containing a duplicate.
	 */
	public static List<String> idsToList (String ids, String preferred_id) {
		ArrayList<String> idlist = new ArrayList<String>();
		HashSet<String> idset = new HashSet<String>();

		// If there is a preferred id, make it the first element in the list

		if (preferred_id != null) {
			if (!( preferred_id.isEmpty() )) {
				idset.add (preferred_id);
				idlist.add (preferred_id);
			}
		}

		// If we have a list of ids ...

		if (ids != null) {

			// Break the ids into individual events

			int n = ids.length();
			int begin = 0;		// Beginning of the current substring

			while (begin < n) {

				// The end of the current substring is the next comma, or the end of the string

				int end = ids.indexOf (",", begin);
				if (end < 0) {
					end = n;
				}

				// The event id is the current substring, with leading and trailing spaces removed

				String id = ids.substring (begin, end).trim();

				// If it's non-empty ...

				if (!( id.isEmpty() )) {
				
					// If it has not been seen before ...

					if (idset.add(id)) {
					
						// Add it to the list of ids

						idlist.add (id);
					}
				}
			
				// Advance to the next candidate substring, which begins after the comma

				begin = end + 1;
			}
		}

		// Return the List

		return idlist;
	}




	/**
	 * Convert a rupture to a string.
	 * @param rup = The ObsEqkRupture to convert.
	 * @return
	 * Returns string describing the rupture contents.
	 * This function is mainly for testing.
	 */
	public static String rupToString (ObsEqkRupture rup) {
		StringBuilder result = new StringBuilder();

		String rup_event_id = rup.getEventId();
		long rup_time = rup.getOriginTime();
		double rup_mag = rup.getMag();
		Location hypo = rup.getHypocenterLocation();
		double rup_lat = hypo.getLatitude();
		double rup_lon = hypo.getLongitude();
		double rup_depth = hypo.getDepth();

		String rup_time_string = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
			.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(rup_time));

		result.append ("ObsEqkRupture:" + "\n");
		result.append ("rup_event_id = " + rup_event_id + "\n");
		result.append ("rup_time = " + rup_time + " (" + rup_time_string + ")" + "\n");
		result.append ("rup_mag = " + rup_mag + "\n");
		result.append ("rup_lat = " + rup_lat + "\n");
		result.append ("rup_lon = " + rup_lon + "\n");
		result.append ("rup_depth = " + rup_depth + "\n");

		ListIterator<Parameter<?>> iter = rup.getAddedParametersIterator();
		if (iter != null) {
			while (iter.hasNext()) {
				Parameter<?> param = iter.next();
				result.append (param.getName() + " = " + param.getValue() + "\n");
			}
		}

		return result.toString();
	}

}
