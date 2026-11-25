package org.opensha.sha.mapping;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

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
import org.opensha.commons.mapping.servlet.GMT_MapGeneratorServlet;
import org.opensha.commons.mapping.servlet.GMT_RenderFiles;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;



/**
 * <p>Title: GMT_HazusMapServlet </p>
 * <p>Description: this servlet runs the GMT script based on the parameters and generates the
 * image file and returns that back to the calling application applet </p>
 * 
 * This is the order of operations:
 * Client ==> Server:
 * * optional directory name hint (String)
 * * GMT Map specification array (GMT_Map[4])
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

public class GMT_HazusMapServlet
extends HttpServlet {
	
	public static final String GMT_URL_PATH = "https://"+ServerPrefUtils.SERVER_PREFS.getHostName()+"/gmtData/";
	public static final File GMT_DATA_DIR = new File(ServerPrefUtils.SERVER_PREFS.getTempDir(), "gmtData");
	
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
			GMT_Map maps[] = (GMT_Map[])inputFromApplet.readObject();

			//Metadata content: Map Info
			String metadata = (String) inputFromApplet.readObject();

			// Consume the legacy metadata filename to preserve the request protocol.
			inputFromApplet.readObject();
			
			String mapImagePath = createMap(gmt, maps, dirName, metadata, null);
			
			//returns the URL to the folder where map image resides
			outputToApplet.writeObject(mapImagePath);
			outputToApplet.close();

		}catch (Throwable t) {
			//sending the error message back to the application
			t.printStackTrace();
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
	
	public static String createMap(GMT_MapGenerator gmt, GMT_Map[] maps, String plotDirName, String metadata,
			String ignoredMetadataFileName) throws IOException, GMT_MapException {
		if (maps == null || maps.length != 4)
			throw new GMT_MapException("HAZUS requires four map specifications");
		for (GMT_Map map : maps) {
			GMT_RenderFiles.validateMap(map);
			GMT_RenderArguments.validateRemoteMap(map);
		}

		File newDir = GMT_MapGeneratorServlet.createPlotDirectory(GMT_DATA_DIR, plotDirName);
		plotDirName = newDir.getName();

		ArrayList<String> scriptLines = new ArrayList<>();
		ArrayList<String> hazusFileNames = new ArrayList<>();
		for (int i = 0; i < maps.length; i++) {
			GMT_Map map = maps[i];
			String imt;
			String hazusPrefix;
			if (i == 0) {
				imt = SA_Param.NAME;
				hazusPrefix = GMT_MapGeneratorForShakeMaps.SA_03;
			} else if (i == 1) {
				imt = SA_Param.NAME;
				hazusPrefix = GMT_MapGeneratorForShakeMaps.SA_10;
			} else if (i == 2) {
				imt = PGA_Param.NAME;
				hazusPrefix = GMT_MapGeneratorForShakeMaps.PGA;
			} else { // i == 3
				imt = PGV_Param.NAME;
				hazusPrefix = GMT_MapGeneratorForShakeMaps.PGV;
			}
			ArrayList<String> gmtMapScript = gmt.getGMT_ScriptLines(map, newDir.getAbsolutePath());
			
			hazusFileNames.add(hazusPrefix + ".shp");
			hazusFileNames.add(hazusPrefix + ".shx");
			hazusFileNames.add(hazusPrefix + ".dbf");
			
			GMT_MapGeneratorForShakeMaps.addHAZUS_Lines(gmtMapScript, map, imt, hazusPrefix);
			
			scriptLines.addAll(gmtMapScript);
			
			GeoDataSet griddedData = map.getGriddedData();
			ArbDiscrGeoDataSet.writeXYZFile(griddedData, new File(newDir, map.getXyzFileName()));
		}
		
		Path script = newDir.toPath().resolve(GMT_RenderFiles.SCRIPT_FILE);
		Files.write(script, scriptLines, Charset.defaultCharset(),
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		RunScript.runScript(new String[] { "/bin/bash", script.toAbsolutePath().toString() });

		Files.writeString(newDir.toPath().resolve(GMT_RenderFiles.MAP_METADATA_FILE), " " + metadata + "\n",
				Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

		// create the Zip file for all the files generated
		FileUtils.createZipFile(newDir.getAbsolutePath());
		
		// create a HAZUS zip file
		String hazusZipName = newDir + "/HAZUS.zip";
		FileUtils.createZipFile(hazusZipName, newDir.getAbsolutePath(), hazusFileNames);
		
		//URL path to folder where all GMT related files and map data file for this
		//calculations reside.
		String mapImagePath = GMT_URL_PATH + plotDirName + "/";
		
		return mapImagePath;
	}

}
