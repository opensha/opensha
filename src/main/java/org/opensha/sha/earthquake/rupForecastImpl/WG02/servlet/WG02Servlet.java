package org.opensha.sha.earthquake.rupForecastImpl.WG02.servlet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.earthquake.rupForecastImpl.WG02.WG02_FortranWrappedERF_EpistemicList;

import com.google.common.base.Preconditions;

public class WG02Servlet extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL()
	+ "WG02_Fortran";
	
	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}
	
	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {

		// get an ouput stream from the applet
		ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
				getOutputStream());
		
		// get an input stream from the applet
		ObjectInputStream inputFromApplet = new ObjectInputStream(request.
				getInputStream());
		
		try {
			ParameterList paramList = (ParameterList) inputFromApplet.readObject();
			TimeSpan timeSpan = (TimeSpan) inputFromApplet.readObject();
			
			Preconditions.checkNotNull(paramList, "param list cannot be null!");
			Preconditions.checkNotNull(timeSpan, "time span cannot be null!");
			
			List<String> output = WG02_FortranWrappedERF_EpistemicList.runFortranCode(paramList, timeSpan);
			
			outputToApplet.writeObject(output);
		} catch (Throwable t) {
			t.printStackTrace();
			outputToApplet.writeObject(t);
		}
		
		outputToApplet.close();
	}
	
	public static List<String> access(ParameterList paramList, TimeSpan timeSpan) throws IOException,
	ClassNotFoundException {
		URL url = new URL(SERVLET_URL);
		
		URLConnection servletConnection = url.openConnection();
		
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

		//sending the parameter list to the server
		outputToServlet.writeObject(paramList);

		//sending the time span to the server
		outputToServlet.writeObject(timeSpan);

		outputToServlet.flush();
		outputToServlet.close();

		// Receive the "actual webaddress of all the gmt related files"
		// from the servlet after it has received all the data
		ObjectInputStream inputToServlet = new
		ObjectInputStream(servletConnection.getInputStream());

		Object messageFromServlet = inputToServlet.readObject();
		inputToServlet.close();
		if (messageFromServlet instanceof RuntimeException)
			throw (RuntimeException)messageFromServlet;
		return (List<String>)messageFromServlet;
	}

}
