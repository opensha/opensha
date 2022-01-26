package org.opensha.sha.gui.servlets;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.sha.gui.servlets.siteEffect.WillsSiteClass;
/**
 * <p>Title: WillsSiteClassServlet  </p>
 * <p>Description: This Servlet finds the VS30 and Basin Depth for the given
 * region. this needs to be fixed with the implementation of the Appliacble Region
 * Object.
 * @author : Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class WillsSiteClassServlet  extends HttpServlet {


	//File from which we get the Vs30
	private final String WILLS_SITE_CLASS_INPUT_FILENAME = "/opt/install/apache-tomcat-5.5.20/webapps/OpenSHA/WEB-INF/dataFiles/usgs_cgs_geology_60s_mod.txt";


	/**
	 * method to get the wills site type as desired by the user
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws ServletException
	 * @return the ArrayList for the Wills Site Class Values for each site in the gridded region.
	 */
	public void doGet(HttpServletRequest request,  HttpServletResponse response)
	throws IOException, ServletException {
		System.out.println("WillsSiteClassServlet: Handling GET");
		//Vectors for computing the lat and lons for the given gridded region
		ArrayList locationVector= new ArrayList();
		try {
			// get all the input stream from the applet
			ObjectInputStream inputFromApplication = new ObjectInputStream(request.getInputStream());
			//gets the input for the minLat, maxLat, minLon, maxLon, gridSpacing  from the Application.
			double minLon = ((Double)inputFromApplication.readObject()).doubleValue();
			double maxLon = ((Double)inputFromApplication.readObject()).doubleValue();
			double minLat = ((Double)inputFromApplication.readObject()).doubleValue();
			double maxLat = ((Double)inputFromApplication.readObject()).doubleValue();
			double gridSpacing = ((Double)inputFromApplication.readObject()).doubleValue();
			//close of the input from the application
			inputFromApplication.close();
			//creating the objct for the Wills Site Class
			WillsSiteClass willsSiteClass = new  WillsSiteClass(minLon, maxLon, minLat, maxLat, gridSpacing,WILLS_SITE_CLASS_INPUT_FILENAME);
			//sending the output in the form of the arrayList back to the calling application.
			ObjectOutputStream output = new ObjectOutputStream(response.getOutputStream());
			output.writeObject(willsSiteClass.getWillsSiteClass());
			output.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * This method just calls the doPost method
	 *
	 * @param request : Request Object
	 * @param response : Response Object
	 * @throws IOException : Throws IOException during read-write from connection stream
	 * @throws ServletException
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response)
	throws IOException, ServletException {
		doGet(request,response);
	}
}
