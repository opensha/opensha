package org.opensha.commons.data.comcat;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.Format;
import gov.usgs.earthquake.event.JsonEvent;
import gov.usgs.earthquake.event.UrlUtil;

import java.io.InputStream;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import java.util.List;
import java.util.ArrayList;

import java.util.zip.GZIPInputStream;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.zip.ZipException;


/**
 * Class to access Comcat using USGS event web services.
 * Author: Michael Barall 05/29/2018.
 *
 * This extends the USGS EventWebService class in order to examine HTTP status codes
 * and properly attribute errors.
 *
 * The major reason to use this class is that the USGS version throws exceptions
 * for both no data available and network errors, and there is no reliable way to
 * tell the difference.  (Telling the difference requires examining the HTTP status
 * code, which the USGS version does not do.)
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




	// Construct a web service.

	public ComcatEventWebService (final URL serviceURL) {
		super (serviceURL);
		http_status_code = -1;
	}




	// Retrieve a list of events from Comcat.

	@Override
	public List<JsonEvent> getEvents(final EventQuery query) throws Exception {
		List<JsonEvent> events = null;

		// Set the HTTP status code to the special value for failed connection attempt.

		http_status_code = -2;

		// Construct the URL that contains the query.
		// Throws MalformedURLException (subclass of IOException) if error in forming URL.

		URL url = getUrl (query, Format.GEOJSON);

		// Create a connection.
		// This is a local operation that does not call out to the network.
		// Throws IOException if there is a problem.
		// The object returned should be an HttpURLConnection (subclass of URLConnection).

		URLConnection conn = url.openConnection();

		// Add a property to indicate we can accept gzipped data.
		// This should not throw an exception.

		conn.addRequestProperty("Accept-encoding", UrlUtil.GZIP_ENCODING);

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

}
