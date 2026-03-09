package org.opensha.commons.data.comcat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.geo.json.Geometry.DepthSerializationType;
import org.opensha.commons.geo.json.Geometry.MultiPolygon;
import org.opensha.commons.geo.json.Geometry.Polygon;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.JsonEvent;
import gov.usgs.earthquake.event.JsonUtil;
import gov.usgs.earthquake.event.UrlUtil;

public class ComcatInvertedFiniteFaultAccessor {
	
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
	
	public ComcatInvertedFiniteFaultAccessor() {
		this(buildService());
	}
	
	public ComcatInvertedFiniteFaultAccessor(EventWebService service) {
		this.service = service;
	}
	
	/**
	 * @param eventID
	 * @return finite fault representation
	 */
	public ComcatInvertedFiniteFault fetchFiniteFault(String eventID) {
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
		JSONArray finites = (JSONArray) prods.get("finite-fault");
		JSONObject finite;

		if (finites != null) {

			finite = (JSONObject) finites.get(0);

			JSONObject contents = (JSONObject) finite.get("contents");
			JSONObject fault = null;

			if (contents != null){
				Set<?> keys = contents.keySet();
				Iterator<?> i = keys.iterator();
				while (i.hasNext()) {
					String str = i.next().toString();
					if (str.toLowerCase().endsWith("ffm.geojson")){
						if(D) System.out.println(str);
						fault = (JSONObject) contents.get(str);
						if(D) System.out.println(fault.get("url"));
						break;
					}
				}
				
				if (fault != null) {
					String urlStr = fault.get("url").toString().trim();
					URL url;
					try {
						url = new URL(urlStr);
					} catch (MalformedURLException e) {
						System.err.println("Error resolving ComCat finite fault URL");
						throw ExceptionUtils.asRuntimeException(e);
					}
					try {
						return parseFaultJSON(url);
					} catch (IOException e) {
						if(D) System.out.println("No Finite Fault");
						System.err.println("Error loading ComCat finite fault file");
						throw ExceptionUtils.asRuntimeException(e);
					}
				} else {
					if(D) System.out.println("ComCat finite fault exists, but no FFM.geojson");
				}
			}
		}

		if(D) System.out.println("No ComCat finite fault");
		return null;
	}
	
	
//	/**
//	 * Build a RuptureSurface for the given event. For complex surfaces with multiple outlines,
//	 * a CompoundSurface will be returned
//	 * @param eventID
//	 * @return
//	 */
//	public RuptureSurface fetchShakemapSourceSurface(String eventID) {
//		LocationList[] outlines = fetchShakemapSourceOutlines(eventID);
//		if (outlines == null)
//			return null;
//		List<RuptureSurface> surfs = new ArrayList<>();
//		for (LocationList outline : outlines)
//			surfs.add(ShakeMapEdgeRuptureSurface.build(outline, MAX_GRID_SPACING));
//		if (surfs.size() == 1)
//			return surfs.get(0);
//		return new CompoundSurface(surfs);
//	}
	
	private static ComcatInvertedFiniteFault parseFaultJSON(URL url) throws IOException {
		InputStream input = UrlUtil.getInputStream(url);
		if ((input instanceof BufferedInputStream))
			input = new BufferedInputStream(input);
		Gson gson = FeatureCollection.buildGson(DepthSerializationType.DEPTH_M); // finite-fault inversions store depths in m
		// parse feature collection into objects
		FeatureCollection collection = gson.fromJson(new InputStreamReader(input), FeatureCollection.class);
		ComcatInvertedFiniteFault ret = new ComcatInvertedFiniteFault();
		for (Feature feature : collection.features) {
			FeatureProperties props = feature.properties;
//			LocationList[] outline = ShakeMapFiniteFaultAccessor.parsePolygonFeature(feature);
			double slip = props.getDouble("slip", 0d);
			double moment = props.getDouble("sf_moment", 0d);
			Geometry geom = feature.geometry;
			if (geom instanceof Polygon) {
				Polygon poly = (Polygon)geom;
				if (poly.polygon != null)
					ret.addRecord(poly.polygon, slip, moment);
			} else if (geom instanceof MultiPolygon) {
				for (Polygon poly : ((MultiPolygon)geom).polygons)
					if (poly != null)
						ret.addRecord(poly.polygon, slip, moment);
			} else {
				throw new IllegalStateException("Unexpected finite fault geometry type: "+geom.type);
			}
		}
		
		return ret;
	}
	
	public static void main(String[] args) {
		ComcatInvertedFiniteFaultAccessor source = new ComcatInvertedFiniteFaultAccessor();
		ComcatInvertedFiniteFault fault = source.fetchFiniteFault("ci38457511");
		MinMaxAveTracker zTrack = new  MinMaxAveTracker();
		for (LocationList outline : fault.getOutlines(0d))
			for (Location loc : outline)
				zTrack.addValue(loc.getDepth());
		System.out.println("Depth track:\n"+zTrack);
	}

}
