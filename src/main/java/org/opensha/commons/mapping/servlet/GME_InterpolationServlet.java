package org.opensha.commons.mapping.servlet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;

import com.google.common.base.Preconditions;

/**
 * GMT interpolation servlet
 * 
 *  * Client ==> Server:
 * * XYZ scatter data (GeoDataSet)
 * * Interpolation region (Region)
 * * Interpolation settings (GMT_InterpolationSettings)
 * Server ==> Client:
 * * Interpolated dataset (GriddedGeoDataSet) **OR** error message
 * 
 * @author kevin
 *
 */
public class GME_InterpolationServlet extends HttpServlet {
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "GME_InterpolationServlet";

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {

		// get an ouput stream from the applet
		ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
				getOutputStream());

		try {
			//create the main directory if it does not exist already
			if (!GMT_MapGeneratorServlet.GMT_DATA_DIR.exists())
				GMT_MapGeneratorServlet.GMT_DATA_DIR.mkdir();
			Preconditions.checkState(GMT_MapGeneratorServlet.GMT_DATA_DIR.exists());

			// get an input stream from the applet
			ObjectInputStream inputFromApplet = new ObjectInputStream(request.
					getInputStream());

			// receieve the scatter data, region, and settings
			GeoDataSet scatterData = (GeoDataSet)inputFromApplet.readObject();
			
			Region region = (Region)inputFromApplet.readObject();
			
			GMT_InterpolationSettings interpSettings = (GMT_InterpolationSettings)inputFromApplet.readObject();
			
			GriddedGeoDataSet ret = doInterpolate(scatterData, region, interpSettings);

			//returns the URL to the folder where map image resides
			outputToApplet.writeObject(ret);
			outputToApplet.close();

		} catch (Throwable t) {
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

	private static GriddedGeoDataSet doInterpolate(GeoDataSet scatterData, Region region, GMT_InterpolationSettings interpSettings)
			throws IOException {
		File outputDir = GMT_MapGeneratorServlet.createUniqueDir(GMT_MapGeneratorServlet.GMT_DATA_DIR);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		String xyzName = "scatter.txt";
		
		// write out scatter data
		scatterData.setLatitudeX(true);
		ArbDiscrGeoDataSet.writeXYZFile(scatterData, new File(outputDir, xyzName));
		
		// Get the limits and discretization of the map
		double minLat = region.getMinLat();
		double maxTempLat = region.getMaxLat();
		double minLon = region.getMinLon();
		double maxTempLon = region.getMaxLon();
		double gridSpacing = interpSettings.getInterpSpacing();

		// adjust the max lat and lon to be an exact increment (needed for xyz2grd)
		double maxLat = Math.rint(((maxTempLat-minLat)/gridSpacing))*gridSpacing +minLat;
		double maxLon = Math.rint(((maxTempLon-minLon)/gridSpacing))*gridSpacing +minLon;
		String regionStr = " -R" + (float)minLon + "/" + (float)maxLon + "/" + (float)minLat + "/" + (float)maxLat+" ";

		File gmtScriptFile = new File(outputDir, "gmtScript.sh");

		ArrayList<String> gmtScript = new ArrayList<>();
		
		gmtScript.add("#!/bin/bash");
		gmtScript.add("");
		gmtScript.add("cd "+outputDir.getAbsolutePath());
		gmtScript.add("");
		gmtScript.addAll(GMT_MapGenerator.getGMTPathEnvLines());
		
		String grdFileName = "interpolated.grd";
		String interpFileName = "interpolated.txt";
		
		gmtScript.add("## Interpolation Script ##");
		gmtScript.add("");
		gmtScript.add("# do GMT interpolation on the scatter data");
		String commandLine = "${GMT_PATH}surface "+ xyzName +" -G"+ grdFileName+ " -I"+gridSpacing+
				regionStr+interpSettings.getConvergenceArg()+" "+interpSettings.getSearchArg()
						+" "+interpSettings.getTensionArg()+" -: -H0";
		gmtScript.add(commandLine);
		gmtScript.add("# write interpolated XYZ file");
		commandLine = "${GMT_PATH}grd2xyz "+ grdFileName+ " > "+interpFileName;
		gmtScript.add(commandLine);
		
		// execute the script
		FileWriter fw = new FileWriter(gmtScriptFile);
		BufferedWriter bw = new BufferedWriter(fw);
		int size = gmtScript.size();
		for (int i = 0; i < size; ++i) {
			bw.write( (String) gmtScript.get(i) + "\n");
		}
		bw.close();
		
		System.out.println("Running command GMT for map: "+outputDir.getAbsolutePath());
		//running the gmtScript file
		String[] command = {
				//					"sh", "-c", "/bin/bash " + gmtScriptFile+" 2> /dev/null > /dev/null"};
				"sh", "-c", "/bin/bash " + gmtScriptFile};
		RunScript.runScript(command);
		
		File interpFile = new File(outputDir, interpFileName);
		System.out.println("DONE, loading map from "+interpFile.getAbsolutePath());
		Preconditions.checkState(interpFile.exists(), "Interpolated file doesn't exist? %s", interpFile.getAbsoluteFile());
		return GriddedGeoDataSet.loadXYZFile(interpFile, false);
	}
	
	public static GriddedGeoDataSet interpolate(GeoDataSet scatterData, Region region, double gridSpacing) {
		GMT_InterpolationSettings interpSettings = GMT_InterpolationSettings.getDefaultSettings();
		interpSettings.setInterpSpacing(gridSpacing);
		return interpolate(scatterData, region, interpSettings);
	}
	
	private static final boolean D = true;
	
	public static GriddedGeoDataSet interpolate(GeoDataSet scatterData, Region region, GMT_InterpolationSettings interpSettings) {
		GriddedGeoDataSet ret = null;
		try{
			if (D) System.out.println("starting to make connection with servlet");
			URL gmtMapServlet = new URL(SERVLET_URL);

			URLConnection servletConnection = gmtMapServlet.openConnection();
			if(D) System.out.println("connection established");

			// inform the connection that we will send output and accept input
			servletConnection.setDoInput(true);
			servletConnection.setDoOutput(true);

			// Don't use a cached version of URL connection.
			servletConnection.setUseCaches (false);
			servletConnection.setDefaultUseCaches (false);
			// Specify the content type that we will send binary data
			servletConnection.setRequestProperty ("Content-Type","application/octet-stream");

			ObjectOutputStream outputToServlet = new
			ObjectOutputStream(servletConnection.getOutputStream());

			// send data to servlet
			outputToServlet.writeObject(scatterData);
			outputToServlet.writeObject(region);
			outputToServlet.writeObject(interpSettings);

			outputToServlet.flush();
			outputToServlet.close();

			// Receive the "actual webaddress of all the gmt related files"
			// from the servlet after it has received all the data
			ObjectInputStream inputToServlet = new
			ObjectInputStream(servletConnection.getInputStream());

			Object messageFromServlet = inputToServlet.readObject();
			inputToServlet.close();
			if(messageFromServlet instanceof GriddedGeoDataSet){
				ret = (GriddedGeoDataSet) messageFromServlet;
				if (D) System.out.println("Receiving the Input from the Servlet: "+ret.size()+" points");
			} else {
				throw (RuntimeException)messageFromServlet;
			}
		} catch(RuntimeException e) {
			throw new RuntimeException(e);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Server is down, please try again later. If the problem persists, make sure"
					+ " you are using the latest version of our applications.");
		}
		return ret;
	}
	
	public static void main(String[] args) {
		Region region = new Region(new Location(34, -118), new Location(36, -120));
		
		double minLat = region.getMinLat();
		double latSpan = region.getMaxLat() - minLat;
		double minLon = region.getMinLon();
		double lonSpan = region.getMaxLon() - minLon;
		
		int numScatter = 15;
		GeoDataSet scatterData = new ArbDiscrGeoDataSet(false);
		for (int i=0; i<numScatter; i++) {
			double lat = minLat + Math.random()*latSpan;
			double lon = minLon + Math.random()*lonSpan;
			Location loc = new Location(lat, lon);
			double val = Math.random();
			System.out.println("Location "+i+": "+loc+" = "+val);
			scatterData.set(loc, val);
		}
		
		System.out.println("Interpolated:");
		GriddedGeoDataSet interp = interpolate(scatterData, region, 0.2);
		for (int i=0; i<interp.size(); i++)
			System.out.println(interp.getLocation(i)+" =\t"+interp.get(i));
	}

}
