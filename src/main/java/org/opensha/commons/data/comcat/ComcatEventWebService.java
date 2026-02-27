package org.opensha.commons.data.comcat;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.Format;
import gov.usgs.earthquake.event.ISO8601;
import gov.usgs.earthquake.event.JsonEvent;
import gov.usgs.earthquake.event.UrlUtil;
import gov.usgs.earthquake.event.JsonUtil;

import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.util.zip.GZIPInputStream;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.zip.ZipException;

import java.nio.charset.StandardCharsets;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Class to access Comcat using USGS event web services.
 * Author: Michael Barall 05/29/2018.
 *
 * This extends the USGS EventWebService class in order to:
 *  - Examine HTTP status codes and properly attribute errors.
 *  - Make use of the detail feed, when possible.
 *  - Impose timeouts on the TCP connection to Comcat.
 *
 * One reason to use this class is that the USGS version throws exceptions
 * for both no data available and network errors, and there is no reliable way to
 * tell the difference.  (Telling the difference requires examining the HTTP status
 * code, which the USGS version does not do.)  This class examines the HTTP status
 * code so that it can distinguish between no data and network errors.
 *
 * A second reason to use this class is to make use of the detail feed, which
 * is a higher-performance system.  The USGS version sends all queries to Comcat.
 * This class can send single-event queries to the detail feed, while continuing
 * to send multi-event queries to Comcat.
 *
 * A third reason to use this class is to impose timeouts on the TCP connection
 * to Comcat.  The USGS version imposes no timeouts, which means that in rare
 * instances it can get stuck in an infinite wait state.  This class imposes
 * timeouts to ensure that it will not wait forever for a response from Comcat.
 */

public class ComcatEventWebService extends EventWebService {

	// This class holds an InputStream.
	// It is AutoCloseable, so that it may be used in a try-with-resources statement.
	// The purpose of using this class is that the InputStream can be replaced with
	// a wrapper around the InputStream.

	public static class InputStreamHolder implements AutoCloseable {

		// The contained stream.

		private InputStream stream;

		// Flag indicates if the stream has been closed.

		private boolean is_closed;

		// Constructor saves the stream and sets the flag.

		public InputStreamHolder (InputStream the_stream) {
			stream = the_stream;
			is_closed = false;
		}

		// Get the stream.

		public InputStream get_stream () {
			return stream;
		}

		// Set the stream.

		public void set_stream (InputStream the_stream) {
			stream = the_stream;
			return;
		}

		// Close the stream.

		@Override
		public void close() throws Exception {

			// Close the stream if needed

			if (!( is_closed )) {
				if (stream != null) {
					stream.close();
				}
				is_closed = true;
			}

			return;
		}
	}




	// The HTTP status code from the last operation, or -1 if unknown, or -2 if unable to connect.

	protected int http_status_code;

	// Get the HTTP status code from the last operation, or -1 if unknown, or -2 if unable to connect.

	public int get_http_status_code () {
		return http_status_code;
	}




	// The URL from the last operation, or null if none or unknown.

	protected URL last_url;

	// Get the URL from the last operation, or null if none or unknown.

	public URL get_last_url () {
		return last_url;
	}




	// Base URL to the real-time feed service, or null if none supplied.

	protected URL feedURL;

	public URL getFeedUrl() {
		return feedURL;
	}




	// Default connection timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default.
	// Note: See java.net.URLConnection for information on timeouts.

	public static final int DEFAULT_CONNECT_TIMEOUT = 15000;		// 15 seconds

	// Default read timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default.
	// Note: See java.net.URLConnection for information on timeouts.
	// Note: The value is large because it can take over 60 seconds for
	// Comcat to respond to a query that matches a large number of events.
	// Note: The system default is apparently 0, meaing no timeout, but on rare
	// occasions the use of 0 can cause the program to hang in an infinite wait.

	public static final int DEFAULT_READ_TIMEOUT = 300000;			// 5 minutes


	// The connection timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default.

	protected int connectTimeout;

	// Get the connection timeout setting, in milliseconds.

	public int getConnectTimeout () {
		return connectTimeout;
	}

	// Set the connection timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default, or -2 for our default.

	public void setConnectTimeout (int the_connectTimeout) {
		if (the_connectTimeout < -2) {
			throw new IllegalArgumentException ("Invalid connection timeout: " + the_connectTimeout);
		}
		connectTimeout = ( (the_connectTimeout == -2) ? DEFAULT_CONNECT_TIMEOUT : the_connectTimeout );
		return;
	}


	// The read timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default.

	protected int readTimeout;

	// Get the read timeout setting, in milliseconds.

	public int getReadTimeout () {
		return readTimeout;
	}

	// Set the read timeout, in milliseconds.
	// Use 0 for no timeout, or -1 for the system default, or -2 for our default.

	public void setReadTimeout (int the_readTimeout) {
		if (the_readTimeout < -2) {
			throw new IllegalArgumentException ("Invalid read timeout: " + the_readTimeout);
		}
		readTimeout = ( (the_readTimeout == -2) ? DEFAULT_READ_TIMEOUT : the_readTimeout );
		return;
	}


	// Flag which is true to enable timeout value readback from the system.
	// It is false by default.

	protected boolean enableTimeoutReadback;

	// Get the timeout readback flag.

	public boolean getEnableTimeoutReadback () {
		return enableTimeoutReadback;
	}

	// Set the timeout readback flag.

	public void setEnableTimeoutReadback (boolean the_enableTimeoutReadback) {
		enableTimeoutReadback = the_enableTimeoutReadback;
		return;
	}


	// The connection timeout from the last query, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	protected int lastConnectTimeout;

	// Get the connection timeout from the last query, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	public int getLastConnectTimeout () {
		return lastConnectTimeout;
	}


	// The read timeout from the last query, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	protected int lastReadTimeout;

	// Get the read timeout from the last query, in milliseconds.
	// It is 0 if no timeout, -1 if unknown (e.g., if timeout readback is disabled).

	public int getLastReadTimeout () {
		return lastReadTimeout;
	}




	// Construct a web service.

	public ComcatEventWebService (final URL serviceURL) {
		super (serviceURL);
		this.feedURL = null;
		http_status_code = -1;
		last_url = null;

		connectTimeout = DEFAULT_CONNECT_TIMEOUT;
		readTimeout = DEFAULT_READ_TIMEOUT;
		enableTimeoutReadback = false;
		lastConnectTimeout = -1;
		lastReadTimeout = -1;
	}

	// Construct a web service, using both Comcat and the real-time feed.
	// If feedURL is null, then the real-time feed is not used.

	public ComcatEventWebService (final URL serviceURL, final URL feedURL) {
		super (serviceURL);
		this.feedURL = feedURL;
		http_status_code = -1;
		last_url = null;

		connectTimeout = DEFAULT_CONNECT_TIMEOUT;
		readTimeout = DEFAULT_READ_TIMEOUT;
		enableTimeoutReadback = false;
		lastConnectTimeout = -1;
		lastReadTimeout = -1;
	}




	// Retrieve a list of events from Comcat.

	@Override
	public List<JsonEvent> getEvents(final EventQuery query) throws Exception {
		List<JsonEvent> events = null;

		// Set the HTTP status code to the special value for failed connection attempt.

		http_status_code = -2;

		// Timeouts are unknown at this point.

		lastConnectTimeout = -1;
		lastReadTimeout = -1;

		// Construct the URL that contains the query.
		// Throws MalformedURLException (subclass of IOException) if error in forming URL.

		last_url = null;
		URL url = getUrl (query, Format.GEOJSON);
		last_url = url;

		// Create a connection.
		// This is a local operation that does not call out to the network.
		// Throws IOException if there is a problem.
		// The object returned should be an HttpURLConnection (subclass of URLConnection).

		URLConnection conn = url.openConnection();

		// Add a property to indicate we can accept gzipped data.
		// This should not throw an exception.

		conn.addRequestProperty("Accept-encoding", UrlUtil.GZIP_ENCODING);

		// Set the timeouts, if desired.

		if (connectTimeout >= 0) {
			conn.setConnectTimeout (connectTimeout);
		}

		if (readTimeout >= 0) {
			conn.setReadTimeout (readTimeout);
		}

		// Read back timeouts, if desired.

		if (enableTimeoutReadback) {
			lastConnectTimeout = conn.getConnectTimeout ();
			lastReadTimeout = conn.getReadTimeout ();
		}

		// Connect to Comcat.
		// Throws SocketTimeoutException (subclass of IOException) if timeout trying to establish connection.
		// Throws IOException if there is some other problem.

		conn.connect();

		// Here is where we can examine the HTTP status code.
		// The connection should be an HTTP connection.

		if (conn instanceof HttpURLConnection) {
			HttpURLConnection http_conn = (HttpURLConnection)conn;

			// This is the HTTP status code

			http_status_code = http_conn.getResponseCode();

			switch (http_status_code) {
			
			case HttpURLConnection.HTTP_OK:				// 200 = OK
			case HttpURLConnection.HTTP_PARTIAL:		// 206 = Partial content
			case -1:									// -1 = unknown status (getResponseCode returns -1 if it can't determine the status code)
				break;									// continue

			case HttpURLConnection.HTTP_NO_CONTENT:		// 204 = No data matches the selection
			case HttpURLConnection.HTTP_NOT_FOUND:		// 404 = Not found, also used by Comcat to indicate no data matches the selection
			case HttpURLConnection.HTTP_CONFLICT:		// 409 = Conflict, used by Comcat to indicate event deleted
				return new ArrayList<JsonEvent>();		// return empty list to indicate nothing found

			default:									// any other status code is an error
				throw new IOException ("Call to Comcat failed with HTTP status code " + http_status_code);
			}

		} else {
		
			// Connection is not HTTP, so set status code to unknown status

			http_status_code = -1;
		}

		try (

			// Open an input stream on the connection.
			// Throws SocketTimeoutException (subclass of IOException) if timeout while reading.
			// Throws UnknownServiceException (subclass of IOException) if input is not supported.
			// Might throw FileNotFoundException (subclass of IOException) if server indicated no data available.
			// Throws IOException if there is some other problem.

			InputStreamHolder stream_holder = new InputStreamHolder (conn.getInputStream());
		
		){

			// Test if we received a gzipped response.
			// Note getContentEncoding should not throw an exception, but it can return null, in
			// which case the equals function will be false.

			if (UrlUtil.GZIP_ENCODING.equals(conn.getContentEncoding())) {

				// Make a gzip wrapper around the stream.
				// Throws ZipException (subclass of IOException) if input is not supported.
				// Throws IOException if there is an I/O error.

				GZIPInputStream zip_stream = new GZIPInputStream (stream_holder.get_stream());
				stream_holder.set_stream (zip_stream);
			}

			// Parse the input stream.
			// Throws IOException if there is an I/O error reading from the network.
			// Throws Exception if there is a problem parsing the data.
			// Might throw other exceptions.
		
			events = parseJsonEventCollection (stream_holder.get_stream());
		}

		return events;
	}




	/**
	 * Parse the response from event web service into an array of JSONEvent
	 * objects.
	 *
	 * @param input
	 *          input stream response from event web service.
	 * @return list of parsed events
	 * @throws Exception
	 *           if format is unexpected.
	 */
	// The following is the exact code from EventWebService.java, except that this:
	//   new InputStreamReader(input)
	// is changed to this:
	//   new InputStreamReader(input, StandardCharsets.UTF_8)
	// The change fixes issues on systems where UTF-8 is not the default character set.
	@Override
	protected List<JsonEvent> parseJsonEventCollection(final InputStream input)
			throws Exception {
		JSONParser parser = new JSONParser();

		// parse feature collection into objects
		JSONObject feed = JsonUtil.getJsonObject(parser
				.parse(new InputStreamReader(input, StandardCharsets.UTF_8)));
		if (feed == null) {
			throw new Exception("Expected feed object");
		}

		// check feed type
		String type = JsonUtil.getString(feed.get("type"));
		if (type == null) {
			throw new Exception("Expected geojson type");
		}

		ArrayList<JsonEvent> events = new ArrayList<JsonEvent>();

		if (type.equals("Feature")) {
			// detail feed with one event

			events.add(new JsonEvent(feed));
		} else if (type.equals("FeatureCollection")) {
			// summary feed with many events

			JSONArray features = JsonUtil.getJsonArray(feed.get("features"));
			if (features == null) {
				throw new Exception("Expected features");
			}

			// parse features into events
			Iterator<?> iter = features.iterator();
			while (iter.hasNext()) {
				JSONObject next = JsonUtil.getJsonObject(iter.next());
				if (next == null) {
					throw new Exception("Expected feature");
				}
				events.add(new JsonEvent(next));
			}
		}

		return events;
	}




	/**
	 * Convert an EventQuery object into an EventWebService URL, using a specific
	 * return format.
	 *
	 * @param query
	 *          the query.
	 * @param format
	 *          the format.
	 * @return a URL for query and format.
	 * @throws MalformedURLException
	 */
	@Override
	public URL getUrl(final EventQuery query, final Format format)
			throws MalformedURLException {

		// See if the query is ComcatEventQuery

		ComcatEventQuery ceq = null;
		if (query instanceof ComcatEventQuery) {
			ceq = (ComcatEventQuery)query;
		}

		// If we have a real-time feed URL ...

		if (feedURL != null) {

			// If this query contains an event ID ...

			if (query.getEventId() != null) {

				// If the return format is geojson ...

				if (format == Format.GEOJSON || (format == null && query.getFormat() == Format.GEOJSON)) {
				
					// If there are no other conditions in the query ...

					if (   query.getAlertLevel() == null
						&& query.getCatalog() == null
						&& query.getContributor() == null
						&& query.getEndTime() == null
						&& query.getEventType() == null
						&& query.getIncludeAllMagnitudes() == null
						&& query.getIncludeAllOrigins() == null
						&& query.getIncludeArrivals() == null
						&& (ceq == null || ceq.getIncludeDeleted() == null)
						&& (ceq == null || ceq.getIncludeSuperseded() == null)
						&& query.getKmlAnimated() == null
						&& query.getKmlColorBy() == null
						&& query.getLatitude() == null
						&& query.getLimit() == null
						&& query.getLongitude() == null
						&& query.getMagnitudeType() == null
						&& query.getMaxCdi() == null
						&& query.getMaxDepth() == null
						&& query.getMaxGap() == null
						&& query.getMaxLatitude() == null
						&& query.getMaxLongitude() == null
						&& query.getMaxMagnitude() == null
						&& query.getMaxMmi() == null
						&& query.getMaxRadius() == null
						&& query.getMaxSig() == null
						&& query.getMinCdi() == null
						&& query.getMinDepth() == null
						&& query.getMinFelt() == null
						&& query.getMinGap() == null
						&& query.getMinLatitude() == null
						&& query.getMinLongitude() == null
						&& query.getMinMagnitude() == null
						&& query.getMinMmi() == null
						&& query.getMinRadius() == null
						&& query.getMinSig() == null
						&& query.getOffset() == null
						&& query.getOrderBy() == null
						&& query.getProductType() == null
						&& query.getReviewStatus() == null
						&& query.getStartTime() == null
						&& query.getUpdatedAfter() == null
					) {
						// Return a URL for the real-time feed

						return new URL (feedURL, query.getEventId() + ".geojson");
					}
				}
			}
		}

		// fill hashmap with parameters
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("alertlevel", query.getAlertLevel());
		params.put("catalog", query.getCatalog());
		params.put("contributor", query.getContributor());
		params.put("endtime", ISO8601.format(query.getEndTime()));
		params.put("eventid", query.getEventId());
		params.put("eventtype", query.getEventType());
		params.put("format", format == null ? query.getFormat() : format);
		params.put("includeallmagnitudes", query.getIncludeAllMagnitudes());
		params.put("includeallorigins", query.getIncludeAllOrigins());
		params.put("includearrivals", query.getIncludeArrivals());
		if (ceq != null) {params.put("includedeleted", ceq.getIncludeDeleted());}
		if (ceq != null) {params.put("includesuperseded", ceq.getIncludeSuperseded());}
		params.put("kmlanimated", query.getKmlAnimated());
		params.put("kmlcolorby", query.getKmlColorBy());
		params.put("latitude", query.getLatitude());
		params.put("limit", query.getLimit());
		params.put("longitude", query.getLongitude());
		params.put("magnitudetype", query.getMagnitudeType());
		params.put("maxcdi", query.getMaxCdi());
		params.put("maxdepth", query.getMaxDepth());
		params.put("maxgap", query.getMaxGap());
		params.put("maxlatitude", query.getMaxLatitude());
		params.put("maxlongitude", query.getMaxLongitude());
		params.put("maxmagnitude", query.getMaxMagnitude());
		params.put("maxmmi", query.getMaxMmi());
		params.put("maxradius", query.getMaxRadius());
		params.put("maxsig", query.getMaxSig());
		params.put("mincdi", query.getMinCdi());
		params.put("mindepth", query.getMinDepth());
		params.put("minfelt", query.getMinFelt());
		params.put("mingap", query.getMinGap());
		params.put("minlatitude", query.getMinLatitude());
		params.put("minlongitude", query.getMinLongitude());
		params.put("minmagnitude", query.getMinMagnitude());
		params.put("minmmi", query.getMinMmi());
		params.put("minradius", query.getMinRadius());
		params.put("minsig", query.getMinSig());
		params.put("offset", query.getOffset());
		params.put("orderby", query.getOrderBy());
		params.put("producttype", query.getProductType());
		params.put("reviewstatus", query.getReviewStatus());
		params.put("starttime", ISO8601.format(query.getStartTime()));
		params.put("updatedafter", ISO8601.format(query.getUpdatedAfter()));

		String queryString = UrlUtil.getQueryString(params);
		return new URL(getServiceUrl(), "query" + queryString);
	}

}
