package org.opensha.commons.data.siteData.servlet;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.data.siteData.ServletEnabledSiteData;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServletObjectInput;
import org.opensha.commons.util.ServletRequestPolicies;
import org.opensha.commons.util.ServletWorkLimits;

public abstract class AbstractSiteDataServlet<Element> extends HttpServlet {

	public static String OP_GET_CLOSEST = "Get Closest Location";

	private volatile ServletEnabledSiteData<Element> data;

	private String debugName;
	
	public AbstractSiteDataServlet(ServletEnabledSiteData<Element> data) {
		super();
		
		this.data = data;
		this.debugName = data.getShortName() + " servlet";
	}
	
	public AbstractSiteDataServlet() {
		// if you use this, you better set the data!
	}
	
	public void setData(ServletEnabledSiteData<Element> data) {
		this.debugName = data.getShortName() + " servlet";
		this.data = data;
	}
	
	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// call the doGet method
		doGet(request,response);
	}
	
	// Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		debug("Handling GET");
		try (ObjectOutputStream out = new ObjectOutputStream(response.getOutputStream())) {
			Object result;
			try {
				result = getResult(request);
			} catch (Exception e) {
				e.printStackTrace(System.out);
				fail(out, "Servlet Exception: " + e.getMessage());
				return;
			}
			out.writeObject(result);
		}
	}

	private Object getResult(HttpServletRequest request) throws IOException, ClassNotFoundException {
		ServletEnabledSiteData<Element> provider = data;
		ParameterList parameters = provider.getServerSideParams();
		int parameterCount = parameters == null ? 0 : parameters.size();
		List<?> options = List.of();
		Object locations;
		boolean closest;

		// Bound and filter client-supplied locations and provider options before deserializing either.
		try (ServletObjectInput in = ServletObjectInput.open(request, ServletRequestPolicies.SITE_DATA)) {
			Object value = in.readObject();
			if (parameterCount > 0) {
				if (!(value instanceof List<?>))
					throw new IllegalArgumentException("Expected " + parameterCount + " provider options");
				options = (List<?>) value;
				if (options.size() != parameterCount)
					throw new IllegalArgumentException("Expected " + parameterCount + " provider options, received "
							+ options.size());
				value = in.readObject();
			}
			closest = OP_GET_CLOSEST.equals(value);
			if (closest) {
				value = in.readObject();
				if (!(value instanceof Location))
					throw new IllegalArgumentException("A location must be given for closest location operation");
			} else if (value instanceof String) {
				throw new IllegalArgumentException("Unknown site-data operation");
			}
			locations = decodeLocations(value);
		}

		// Apply options and use the provider as one operation. Socket I/O stays outside the provider lock.
		synchronized (provider) {
			if (parameterCount > 0)
				applyOptions(parameters, options);
			if (locations instanceof LocationList)
				return provider.getValues((LocationList) locations);
			Location location = (Location) locations;
			return closest ? provider.getClosestDataLocation(location) : provider.getValue(location);
		}
	}

	private static Object decodeLocations(Object value) {
		if (value instanceof List<?> && ((List<?>) value).size() > ServletWorkLimits.SITE_LOCATIONS)
			throw new IllegalArgumentException("Site-data requests are limited to "
					+ ServletWorkLimits.SITE_LOCATIONS + " locations. Split the locations into smaller batches.");
		if (value instanceof LocationList) {
			for (Object location : (LocationList) value) {
				if (!(location instanceof Location))
					throw new IllegalArgumentException("Expected a location in the location list");
				validateLocation((Location) location);
			}
			return value;
		}
		if (value instanceof List<?>) {
			List<?> coordinates = (List<?>) value;
			LocationList locations = new LocationList(coordinates.size());
			for (Object point : coordinates)
				locations.add(decodeCoordinates(point));
			return locations;
		}
		if (value instanceof Location)
			return validateLocation((Location) value);
		return decodeCoordinates(value);
	}

	private static Location decodeCoordinates(Object value) {
		if (!(value instanceof double[]))
			throw new IllegalArgumentException("Expected a location or an array of coordinates");
		double[] coordinates = (double[]) value;
		if (coordinates.length != 2 && coordinates.length != 3)
			throw new IllegalArgumentException("Expected latitude, longitude, and optional depth");
		double depth = coordinates.length == 3 ? coordinates[2] : 0d;
		return validateLocation(new Location(coordinates[0], coordinates[1], depth));
	}

	private static Location validateLocation(Location location) {
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		double depth = location.getDepth();
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || !Double.isFinite(depth)
				|| !Double.isFinite(location.getLatRad()) || !Double.isFinite(location.getLonRad()))
			throw new IllegalArgumentException("Site coordinates must be finite");
		GeoTools.validateLat(latitude);
		GeoTools.validateLon(longitude);
		GeoTools.validateDepth(depth);
		return location;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void applyOptions(ParameterList parameters, List<?> options) {
		Object[] previousValues = new Object[options.size()];
		for (int i = 0; i < options.size(); i++) {
			Parameter parameter = parameters.getByIndex(i);
			Object option = options.get(i);
			if ((parameter instanceof BooleanParameter && !(option instanceof Boolean))
					|| !parameter.isAllowed(option))
				throw new IllegalArgumentException("Invalid value for provider option " + parameter.getName());
			previousValues[i] = parameter.getValue();
		}

		try {
			for (int i = 0; i < options.size(); i++)
				parameters.getByIndex(i).setValue(options.get(i));
		} catch (RuntimeException e) {
			for (int i = 0; i < previousValues.length; i++) {
				try {
					parameters.getByIndex(i).setValue(previousValues[i]);
				} catch (RuntimeException rollbackError) {
					e.addSuppressed(rollbackError);
				}
			}
			throw e;
		}
	}
	
	protected void fail(ObjectOutputStream out, String message) throws IOException {
		ServerPrefUtils.fail(out, debugName, message);
	}
	
	protected void debug(String message) {
		ServerPrefUtils.debug(debugName, message);
	}
}
