package org.opensha.commons.data.comcat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.JsonEvent;
import gov.usgs.earthquake.event.JsonUtil;

public class ShakeMapFiniteFaultAccessor {
	
	private static final boolean D = true;
	
	private static final double MAX_GRID_SPACING = 1d;
	
	private EventWebService service;
	
	private static EventWebService buildService() {
		try {
			URL serviceURL = new URL ("https://earthquake.usgs.gov/fdsnws/event/1/");
			URL feedURL = new URL ("https://earthquake.usgs.gov/earthquakes/feed/v1.0/detail/");
			return new ComcatEventWebService(serviceURL, feedURL);
		} catch (MalformedURLException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public ShakeMapFiniteFaultAccessor() {
		this(buildService());
	}
	
	public ShakeMapFiniteFaultAccessor(EventWebService service) {
		this.service = service;
	}
	
	/**
	 * @param eventID
	 * @return array of all rupture surface outlines. These outlines are direct from ShakeMap, and will follow
	 * their EdgeRupture specification where points are listed on the upper trace along strike, then the lower
	 * trace in reverse, then close the polygon by repeating the first point
	 */
	public LocationList[] fetchShakemapSourceOutlines(String eventID) {
		EventQuery query = new EventQuery();
		query.setEventId(eventID);
		List<JsonEvent> events;
		try {
			events = service.getEvents(query);
			Preconditions.checkState(!events.isEmpty(), "Event not found");
		} catch (Exception e) {
			System.err.println("Could not retrieve event '"+ eventID +"' from Comcat");
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Preconditions.checkState(events.size() == 1, "More that 1 match? "+events.size());
		
//		JsonEvent event = events.get(0);
		JSONObject obj = events.get(0);
//		JSONParser jsonParser = new JSONParser();s
		
//		printJSON(obj);
		
		
		JSONObject prop = (JSONObject) obj.get("properties");
		JSONObject prods = (JSONObject) prop.get("products");
		JSONArray shakemaps = (JSONArray) prods.get("shakemap");
		JSONObject shakemap;

		if (shakemaps != null) {

			shakemap = (JSONObject) shakemaps.get(0);

			JSONObject contents = (JSONObject) shakemap.get("contents");
			JSONObject fault = null;

			if (contents != null){
				Set<?> keys = contents.keySet();
				Iterator<?> i = keys.iterator();
				while (i.hasNext()) {
					String str = i.next().toString();
					if (str.toLowerCase().endsWith("fault.txt")){
						if(D) System.out.println(str);
						fault = (JSONObject) contents.get(str);
						if(D) System.out.println(fault.get("url"));
						break;
					}
					// TODO fault.json
				}
				
				if (fault != null) {
					String urlStr = fault.get("url").toString().trim();
					URL url;
					try {
						url = new URL(urlStr);
					} catch (MalformedURLException e) {
						System.err.println("Error resolving ShakeMap finite fault URL");
						throw ExceptionUtils.asRuntimeException(e);
					}
					try {
						if (urlStr.toLowerCase().endsWith(".txt"))
							return parseFaultTextFile(url);
					} catch (IOException e) {
						if(D) System.out.println("No Shakemap");
						System.err.println("Error loading ShakeMap finite fault file");
						throw ExceptionUtils.asRuntimeException(e);
					}
					// TODO fault.json
				} else {
					if(D) System.out.println("Shakemap exists, but no finite fault");
				}
			}
		}

		if(D) System.out.println("No Shakemap");
		return null;
	}
	
	
	/**
	 * Build a RuptureSurface for the given event. For complex surfaces with multiple outlines,
	 * a CompoundSurface will be returned
	 * @param eventID
	 * @return
	 */
	public RuptureSurface fetchShakemapSourceSurface(String eventID) {
		LocationList[] outlines = fetchShakemapSourceOutlines(eventID);
		if (outlines == null)
			return null;
		List<RuptureSurface> surfs = new ArrayList<>();
		for (LocationList outline : outlines)
			surfs.add(EdgeRuptureSurface.build(outline, MAX_GRID_SPACING));
		if (surfs.size() == 1)
			return surfs.get(0);
		return new CompoundSurface(surfs);
	}
	
	private static LocationList[] parseFaultTextFile(URL url) throws IOException {
		String text = IOUtils.toString(url, "UTF-8");
		String[] lines = text.split("\n");
		
		List<LocationList> outlines = new ArrayList<>();
		LocationList curLocs = new LocationList();
		for (String line : lines) {
			line = line.trim();
			if (line.startsWith(">") || line.startsWith("#") || line.isEmpty()) {
				if (!curLocs.isEmpty()) {
					outlines.add(curLocs);
					curLocs = new LocationList();
				}
			} else {
				String[] split = line.split(" ");
				Preconditions.checkState(split.length == 3, "Unexpected line in ShakeMap finite fault file: %s", line);
				Location loc = new Location(Double.parseDouble(split[0]), Double.parseDouble(split[1]),
						Double.parseDouble(split[2]));
				curLocs.add(loc);
			}
		}
		if (!curLocs.isEmpty())
			outlines.add(curLocs);
		if (D) System.out.println("Found surface from "+outlines.size()+" outline polygons");
		Preconditions.checkState(!outlines.isEmpty(), "No surfaces found");
		return outlines.toArray(new LocationList[0]);
	}
	
	static LocationList parsePolygonFeature(JSONObject feature) {
		JSONObject geom = JsonUtil.getJsonObject(feature.get("geometry"));
		String type = JsonUtil.getString(geom.get("type"));
		Preconditions.checkState(type.equals("Polygon"), "Only 'Polygon' geometry type supported. Type is: %s", type);
		JSONArray coordsOuter = JsonUtil.getJsonArray(geom.get("coordinates"));
		JSONArray coordsInner = JsonUtil.getJsonArray(coordsOuter.get(0));
		LocationList ret = new LocationList();
		for (Object coordsObj : coordsInner) {
			JSONArray coords = JsonUtil.getJsonArray(coordsObj);
			Preconditions.checkState(coords.size() == 3, "Expected 3 values (lon, lat, depth)");
			double lon = ((Number)coords.get(0)).doubleValue();
			double lat = ((Number)coords.get(1)).doubleValue();
			double depth = ((Number)coords.get(2)).doubleValue()/1000d; // m to km
			ret.add(new Location(lat, lon, depth));
		}
		return ret;
	}
	
	public static void main(String[] args) {
		ShakeMapFiniteFaultAccessor source = new ShakeMapFiniteFaultAccessor();
		source.fetchShakemapSourceOutlines("ci38457511");
	}

}
