package org.opensha.commons.data.siteData.servlet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.siteData.ServletEnabledSiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;

import com.google.common.collect.Lists;

public class SiteDataServletAccessor<Element> {
	
	private String url;
	private ServletEnabledSiteData<Element> data;
	
	private int maxLocsPerRequest = 100000;
	
	public SiteDataServletAccessor(ServletEnabledSiteData<Element> data, String servletURL) {
		this.url = servletURL;
		this.data = data;
	}
	
	public int getMaxLocsPerRequest() {
		return maxLocsPerRequest;
	}

	public void setMaxLocsPerRequest(int maxLocsPerRequest) {
		this.maxLocsPerRequest = maxLocsPerRequest;
	}

	public Element getValue(Location loc) throws IOException {
		return (Element)getResult(locToArray(loc));
	}
	
	private static double[] locToArray(Location loc) {
		if (loc.getDepth() == 0d)
			return new double[] {loc.getLatitude(), loc.getLongitude()};
		return new double[] {loc.getLatitude(), loc.getLongitude(), loc.getDepth()};
	}
	
	public Location getClosestLocation(Location loc) throws IOException {
		return (Location)getResult(loc, AbstractSiteDataServlet.OP_GET_CLOSEST);
	}
	
	public ArrayList<Element> getValues(LocationList locs) throws IOException {
		ArrayList<Element> result = null;
		
		if (maxLocsPerRequest > 0 && locs.size() > maxLocsPerRequest) {
			result = new ArrayList<Element>();
			
			int done = 0;
			int tot = locs.size();
			for (LocationList partialLocs : locs.split(maxLocsPerRequest)) {
				float frac = (float)done / (float)tot * 100f;
//				System.out.println("Requesting " + partialLocs.size() + " values (" + frac + " % done)");
				result.addAll((ArrayList<Element>)getResult(locListToArrays(partialLocs)));
				done += partialLocs.size();
			}
		} else {
//			System.out.println("Requesting " + locs.size() + " values");
			result = (ArrayList<Element>)getResult(locListToArrays(locs));
		}
		
		return result;
	}
	
	private static List<double[]> locListToArrays(LocationList locs) {
		List<double[]> ret = new ArrayList<>();
		for (Location loc : locs)
			ret.add(locToArray(loc));
		return ret;
	}
	
	private Object getResult(Object request) throws IOException {
		return getResult(request, null);
	}
	
	private Object getResult(Object request, String operation) throws IOException {
		URLConnection servletConnection = this.openServletConnection();
		
		ObjectOutputStream outputToServlet = new
				ObjectOutputStream(servletConnection.getOutputStream());
		
		ParameterList serverParams = data.getServerSideParams();
		if (serverParams != null && serverParams.size() > 0) {
			List<Object> paramVals = Lists.newArrayList();
			for (Parameter param : serverParams)
				paramVals.add(param.getValue());
//			System.out.println("Sending params: "+paramVals.size());
			outputToServlet.writeObject(paramVals);
		}
		
		// we have an operation to specify
		if (operation != null && operation.length() > 0)
			outputToServlet.writeObject(operation);
		
		outputToServlet.writeObject(request);
		
		ObjectInputStream inputFromServlet = new
		ObjectInputStream(servletConnection.getInputStream());
		
		try {
			Object result = inputFromServlet.readObject();
			
			checkForError(result, inputFromServlet);
			
			inputFromServlet.close();
			
			return result;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void checkForError(Object obj, ObjectInputStream inputFromServlet) throws IOException, ClassNotFoundException {
		if (obj instanceof Boolean) {
			String message = (String)inputFromServlet.readObject();
			
			throw new RuntimeException("Status Request Failed: " + message);
		}
	}
	
	protected URLConnection openServletConnection() throws IOException {
		URL servlet = new URL(url);
//		System.out.println("Connecting to: " + url + " ...");
		URLConnection servletConnection = servlet.openConnection();
		
		// inform the connection that we will send output and accept input
		servletConnection.setDoInput(true);
		servletConnection.setDoOutput(true);

		// Don't use a cached version of URL connection.
		servletConnection.setUseCaches (false);
		servletConnection.setDefaultUseCaches (false);
		// Specify the content type that we will send binary data
		servletConnection.setRequestProperty ("Content-Type","application/octet-stream");
		
		return servletConnection;
	}
}
