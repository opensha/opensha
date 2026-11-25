package org.opensha.commons.mapping.servlet;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.GMT_RenderArguments;
import org.opensha.commons.mapping.gmt.SecureMapGenerator;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServletObjectInput;
import org.opensha.commons.util.ServletRequestPolicies;

/**
 * <p>Title: GMT_MapGeneratorServlet </p>
 * <p>Description: this servlet runs the GMT script based on the parameters and generates the
 * image file and returns that back to the calling application applet </p>
 * 
 * ****** ORIGINAL VERSION - <b>VERY</b> insecure *******
 * This is the order of operations:
 * Client ==> Server:
 * * directory name (String), or null for auto dirname
 * * GMT script (ArrayList<String>)
 * * XYZ Dataset (XYZ_DatasetAPI)
 * * Filename for XYZ Dataset (String)
 * * Metadata (String)
 * * Metadata filename (String)
 * Server ==> Client:
 * * IMG path **OR** error message
 * 
 * * ****** NEW VERSION - more secure *******
 * This is the order of operations:
 * Client ==> Server:
 * * optional directory name hint (String)
 * * GMT Map specification (GMT_Map)
 * * Metadata (String)
 * * Legacy metadata filename (String, ignored)
 * Server ==> Client:
 * * Directory URL path **OR** error message
 * 
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author :Nitin Gupta, Vipin Gupta, and Kevin Milner
 * @version 1.0
 */

public class GMT_MapGeneratorServlet
extends HttpServlet {
	
	public static final String GMT_URL_PATH = "https://"+ServerPrefUtils.SERVER_PREFS.getHostName()+"/gmtData/";
	public static final File GMT_DATA_DIR = new File(ServerPrefUtils.SERVER_PREFS.getTempDir(), "gmtData");
	private static final Pattern PLOT_DIR_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
	
	private GMT_MapGenerator gmt = new GMT_MapGenerator();

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {

		// get an ouput stream from the applet
		ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
				getOutputStream());

		try {
			String dirName;
			GMT_Map map;
			String metadata;
			// The GMT_Map cast happens after deserialization. Filter the entire client-supplied graph as it is read.
			try (ServletObjectInput input = ServletObjectInput.open(request, ServletRequestPolicies.GMT)) {
				dirName = (String) input.readObject();
				map = (GMT_Map) input.readObject();
				metadata = (String) input.readObject();
				// Consume the legacy metadata filename to preserve the request protocol.
				input.readObject();
			}

			String mapImagePath = createMap(gmt, map, dirName, metadata, null);
			
			//returns the URL to the folder where map image resides
			outputToApplet.writeObject(mapImagePath);
			outputToApplet.close();

		}catch (Throwable t) {
			//sending the error message back to the application
			outputToApplet.writeObject(new RuntimeException(t));
			outputToApplet.close();
		}
	}
	
	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}
	
	/**
	 * Creates a new plot directory under a server-controlled parent. A name hint must be one safe path component.
	 * Missing or invalid hints use a timestamp, and existing names receive a numeric suffix.
	 */
	public static File createPlotDirectory(File parentDir, String nameHint) throws IOException {
		Path parent = parentDir.toPath();
		if (!Files.isDirectory(parent))
			Files.createDirectories(parent);

		String baseName = nameHint;
		if (baseName == null || !PLOT_DIR_NAME.matcher(baseName).matches())
			baseName = Long.toString(System.currentTimeMillis());

		for (int suffix = 0; ; suffix++) {
			String name = suffix == 0 ? baseName : baseName + "_" + suffix;
			try {
				return Files.createDirectory(parent.resolve(name)).toFile();
			} catch (FileAlreadyExistsException e) {
				// Try the next name without reusing an existing directory, file, or symlink.
			}
		}
	}

	public static File createUniqueDir(File parentDir) {
		try {
			return createPlotDirectory(parentDir, null);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create a unique map directory under " + parentDir, e);
		}
	}

	public static String createMap(SecureMapGenerator gmt, GMT_Map map, String plotDirName, String metadata,
			String ignoredMetadataFileName) throws IOException, GMT_MapException {
		GMT_RenderFiles.validateMap(map);
		GMT_RenderArguments.validateRemoteMap(map);
		File newDir = createPlotDirectory(GMT_DATA_DIR, plotDirName);
		plotDirName = newDir.getName();

		ArrayList<String> gmtMapScript = gmt.getGMT_ScriptLines(map, newDir.getAbsolutePath());

		//creating the XYZ file from the XYZ file from the XYZ dataSet
		if (map.getGriddedData() != null) {
			GeoDataSet griddedData = map.getGriddedData();
			griddedData.setLatitudeX(true);
			ArbDiscrGeoDataSet.writeXYZFile(griddedData, new File(newDir, map.getXyzFileName()));
		}

		Path script = newDir.toPath().resolve(GMT_RenderFiles.SCRIPT_FILE);
		Files.write(script, gmtMapScript, Charset.defaultCharset(),
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		System.out.println("Running command GMT for map: " + plotDirName);
		RunScript.runScriptChecked("/bin/bash", script.toAbsolutePath().toString());

		if (gmt instanceof GMT_MapGenerator) {
			GMT_RenderFiles.writeHtmlMetadata(newDir.toPath(), metadata);
		} else {
			Files.writeString(newDir.toPath().resolve(GMT_RenderFiles.TEXT_METADATA_FILE), " " + metadata + "\n",
					Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}

		System.out.println("Zipping results for map: "+plotDirName);
		//create the Zip file for all the files generated
		FileUtils.createZipFile(newDir.getAbsolutePath());
		//URL path to folder where all GMT related files and map data file for this
		//calculations reside.
		String mapImagePath = GMT_URL_PATH + plotDirName + "/";
		
		System.out.println("DONE. Map URL for '"+plotDirName+"': "+mapImagePath);
		
		return mapImagePath;
	}

}
