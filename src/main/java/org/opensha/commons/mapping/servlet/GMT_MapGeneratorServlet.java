package org.opensha.commons.mapping.servlet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.opensha.commons.mapping.gmt.SecureMapGenerator;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;

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
 * * Metadata filename (String)
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
	private final static String GMT_SCRIPT_FILE = "gmtScript.txt";
	private static final Pattern PLOT_DIR_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
	
	private GMT_MapGenerator gmt = new GMT_MapGenerator();

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {

		// get an ouput stream from the applet
		ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
				getOutputStream());

		try {
			// get an input stream from the applet
			ObjectInputStream inputFromApplet = new ObjectInputStream(request.
					getInputStream());

			//receiving the name of the input directory
			String dirName = (String) inputFromApplet.readObject();

			//gets the object for the GMT_MapGenerator script
			GMT_Map map = (GMT_Map)inputFromApplet.readObject();

			//Metadata content: Map Info
			String metadata = (String) inputFromApplet.readObject();

			//Name of the Metadata file
			String metadataFileName = (String) inputFromApplet.readObject();
			
			String mapImagePath = createMap(gmt, map, dirName, metadata, metadataFileName);
			
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
			String metadataFileName) throws IOException, GMT_MapException {
		File newDir = createPlotDirectory(GMT_DATA_DIR, plotDirName);
		plotDirName = newDir.getName();

		File gmtScriptFile = new File(newDir, GMT_SCRIPT_FILE);
		
		ArrayList<String> gmtMapScript = gmt.getGMT_ScriptLines(map, newDir.getAbsolutePath());

		System.out.println("Writing file and data for map: "+plotDirName);
		//creating a new gmt script for the user and writing it ot the directory created for the user
		FileWriter fw = new FileWriter(gmtScriptFile);
		BufferedWriter bw = new BufferedWriter(fw);
		int size = gmtMapScript.size();
		for (int i = 0; i < size; ++i) {
			bw.write( (String) gmtMapScript.get(i) + "\n");
		}
		bw.close();

		// I use the new File().getName() here to  make sure the filename isn't a relative path
		// that could overwrite something important, like "../../myfile"
		String metadataFile = newDir + "/" + new File(metadataFileName).getName();
		//creating the metadata (map Info) file in the new directory created for user
		fw = new FileWriter(metadataFile);
		bw = new BufferedWriter(fw);
		bw.write(" " + (String) metadata + "\n");
		bw.close();

		//creating the XYZ file from the XYZ file from the XYZ dataSet
		if (map.getGriddedData() != null) {
			GeoDataSet griddedData = map.getGriddedData();
			griddedData.setLatitudeX(true);
			ArbDiscrGeoDataSet.writeXYZFile(griddedData, newDir + "/" + new File(map.getXyzFileName()).getName());
		}

		System.out.println("Running command GMT for map: "+plotDirName);
		//running the gmtScript file
		String[] command = {
//				"sh", "-c", "/bin/bash " + gmtScriptFile+" 2> /dev/null > /dev/null"};
				"sh", "-c", "/bin/bash " + gmtScriptFile};
		RunScript.runScript(command);

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
