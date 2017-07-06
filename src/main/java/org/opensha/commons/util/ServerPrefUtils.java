package org.opensha.commons.util;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class contains constants for the ServerPrefs enum. It also always contains a pointer to
 * the ServerPrefs instance in use, <code>ServerPrefUtils.SERVER_PREFS</code>.
 * 
 * It should be used when looking up the current tomcat URL, or other server preferences.
 * 
 * @author kevin
 *
 */
public class ServerPrefUtils {
	
	public static final DateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
	
	/**
	 * Hostname for all production services
	 */
	static final String OPENSHA_SERVER_PRODUCTION_HOST = "opensha.usc.edu";
	
	/**
	 * Hostname for all development services
	 */
	static final String OPENSHA_SERVER_DEV_HOST = "opensha.usc.edu";
	
//	static final String OPENSHA_TOMCAT_WEBAPPS_DIR = "/usr/local/tomcat/default/webapps";
	static final String OPENSHA_TOMCAT_WEBAPPS_DIR = "/var/lib/tomcat/webapps/";
	
	/**
	 * Directories for storing data and temporary files
	 */
	static final File DATA_DIR = new File("/export/opensha-00/data/");
	static final File TEMP_DIR = new File("/export/opensha-00/tmp/");
	
	/**
	 * This is the path to the WEB-INF dir for production OpenSHA servlets
	 */
	static final File OPENSHA_TOMCAT_PRODUCTION_DIR =
		new File(OPENSHA_TOMCAT_WEBAPPS_DIR+"/OpenSHA/WEB-INF/");
	
	/**
	 * This is the path to the WEB-INF dir for development OpenSHA servlets
	 */
	static final File OPENSHA_TOMCAT_DEV_DIR =
		new File(OPENSHA_TOMCAT_WEBAPPS_DIR+"/OpenSHA_dev/WEB-INF/");
	
	/**
	 * This is the URL to the production OpenSHA servlets.
	 */
	static final String OPENSHA_SERVLET_PRODUCTION_URL =
		"http://"+OPENSHA_SERVER_PRODUCTION_HOST+":8080/OpenSHA/";
	
	/**
	 * This is the URL to the development OpenSHA servlets
	 */
	static final String OPENSHA_SERVLET_DEV_URL =
		"http://"+OPENSHA_SERVER_DEV_HOST+":8080/OpenSHA_dev/";
	
	/**
	 * This is the preferences enum for OpenSHA...it should always be link to the production prefs
	 * when applications are final and being distributed, the the development prefs should be used when
	 * changes are being made that would break the currently released apps.
	 * 
	 * In practice, this means that it should be development prefs on trunk and nightly builds, and production
	 * prefs on release branches and distribution applications.
	 */
	public static final ServerPrefs SERVER_PREFS = ServerPrefs.DEV_PREFS;
	
	public static void debug(String debugName, String message) {
		String date = "[" + df.format(new Date()) + "]";
		System.out.println(debugName + " " + date + ": " + message);
	}
	
	public static void fail(ObjectOutputStream out, String debugName, String message) throws IOException {
		debug(debugName, "Failing: " + message);
		out.writeObject(new Boolean(false));
		out.writeObject(message);
		out.flush();
		out.close();
	}

}
