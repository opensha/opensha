package org.opensha.sha.gui.servlets;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.mapping.servlet.GMT_MapGeneratorServlet;
import org.opensha.commons.mapping.servlet.GMT_RenderSlot;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServletObjectInput;
import org.opensha.commons.util.ServletRequestPolicies;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;


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
			DisaggregationPlotData data;
			String metadata;
			// Restrict the classes and sizes of client-supplied plot data before allocating arrays or starting GMT.
			try (ServletObjectInput input = ServletObjectInput.open(request, ServletRequestPolicies.DISAGGREGATION)) {
				data = (DisaggregationPlotData) input.readObject();
				metadata = (String) input.readObject();
			}

			String mapImagePath = renderPlot(data, metadata);
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

	private static String renderPlot(DisaggregationPlotData data, String metadata) throws IOException {
		try (GMT_RenderSlot slot = GMT_RenderSlot.acquire()) {
			File newDir = GMT_MapGeneratorServlet.createUniqueDir(GMT_DATA_DIR);
			Path scriptFile = newDir.toPath().resolve(GMT_SCRIPT_FILE);
			List<String> script = DisaggregationCalculator.createGMTScriptForDisaggregationPlot(
					data, newDir.getAbsolutePath());
			Files.write(scriptFile, script, Charset.defaultCharset(), StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE);
			Files.writeString(newDir.toPath().resolve(METADATA_FILE_NAME), " " + metadata + "\n",
					Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			RunScript.runScriptChecked("/bin/bash", scriptFile.toAbsolutePath().toString());
			FileUtils.createZipFile(newDir.getAbsolutePath());
			return GMT_URL_PATH + newDir.getName() + "/";
		}
	}

	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}
}
