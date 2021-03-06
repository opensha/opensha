package org.opensha.sha.gui.servlets;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.SystemUtils;
import org.opensha.commons.mapping.servlet.GMT_MapGeneratorServlet;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;

import com.google.common.base.Preconditions;


/**
 * <p>Title: DisaggregationPlotServlet </p>
 * <p>Description: this servlet runs the GMT script based on the parameters and generates the
 * image file and returns that back to the calling application applet </p>

 * @author :Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class DisaggregationPlotServlet
extends HttpServlet {

	private static final String GMT_URL_PATH = GMT_MapGeneratorServlet.GMT_URL_PATH;
	private static final File GMT_DATA_DIR = GMT_MapGeneratorServlet.GMT_DATA_DIR;
	private final static String GMT_SCRIPT_FILE = "gmtScript.txt";
	private final static String METADATA_FILE_NAME = "metadata.txt";

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		
		System.out.println("DisaggregationPlotServlet: Handling GET");

		// get an ouput stream from the applet
		ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
				getOutputStream());

		try {
			//create the main directory if it does not exist already
			if (!GMT_DATA_DIR.exists())
				GMT_DATA_DIR.mkdir();
			Preconditions.checkState(GMT_DATA_DIR.exists());

			// get an input stream from the applet
			ObjectInputStream inputFromApplet = new ObjectInputStream(request.
					getInputStream());

			//Name of the directory in which we are storing all the gmt data for the user
			File newDir = GMT_MapGeneratorServlet.createUniqueDir(GMT_DATA_DIR);

			//create a gmt directory for each user in which all his gmt files will be stored
			Preconditions.checkState(newDir.exists() || newDir.mkdir());
			//reading the gmtScript file that user sent as the attachment and create
			//a new gmt script inside the directory created for the user.
			//The new gmt script file created also has one minor modification
			//at the top of the gmt script file I am adding the "cd ... " command so
			//that it should pick all the gmt related files from the directory cretade for the user.
			//reading the gmt script file sent by user as te attchment

			File gmtScriptFile = new File(newDir, GMT_SCRIPT_FILE);

			System.out.println("DisaggregationPlotServlet: fetching disagg data");
			//gets the object for the GMT_MapGenerator script
			DisaggregationPlotData data = (DisaggregationPlotData)inputFromApplet.readObject();
			System.out.println("DisaggregationPlotServlet: creating disagg GMT script");
			ArrayList<String> gmtMapScript =
					DisaggregationCalculator.createGMTScriptForDisaggregationPlot(data, newDir.getAbsolutePath());

			//Metadata content: Map Info
			System.out.println("DisaggregationPlotServlet: fetching disagg metadata");
			String metadata = (String) inputFromApplet.readObject();

			System.out.println("DisaggregationPlotServlet: writing disagg script");
			//creating a new gmt script for the user and writing it ot the directory created for the user
			FileWriter fw = new FileWriter(gmtScriptFile);
			BufferedWriter bw = new BufferedWriter(fw);
			int size = gmtMapScript.size();
			for (int i = 0; i < size; ++i) {
				bw.write( (String) gmtMapScript.get(i) + "\n");
			}
			bw.close();

			File metadataFile = new File(newDir, METADATA_FILE_NAME);

			System.out.println("DisaggregationPlotServlet: writing disagg metadata");
			//creating the metadata (map Info) file in the new directory created for user
			fw = new FileWriter(metadataFile);
			bw = new BufferedWriter(fw);
			bw.write(" " + (String) metadata + "\n");
			bw.close();

			//running the gmtScript file
			System.out.println("DisaggregationPlotServlet: running disagg script");
			String[] command = {
					"sh", "-c", "sh " + gmtScriptFile};
			RunScript.runScript(command);
			System.out.println("DisaggregationPlotServlet: done running disagg script");

			System.out.println("DisaggregationPlotServlet: zipping output files");
			//create the Zip file for all the files generated
			FileUtils.createZipFile(newDir.getAbsolutePath());
			//URL path to folder where all GMT related files and map data file for this
			//calculations reside.
			String mapImagePath = GMT_URL_PATH+newDir.getName()+File.separator;
			System.out.println("DisaggregationPlotServlet: sending image path to application");
			//returns the URL to the folder where map image resides
			outputToApplet.writeObject(mapImagePath);
			outputToApplet.close();

		}catch (Exception e) {
			//sending the error message back to the application
			outputToApplet.writeObject(new RuntimeException(e.getMessage()));
			outputToApplet.close();
		}
		System.out.println("DisaggregationPlotServlet: DONE with disagg plot");
	}

	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}
}
