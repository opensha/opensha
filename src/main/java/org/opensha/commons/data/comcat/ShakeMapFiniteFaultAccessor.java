package org.opensha.commons.data.comcat;

import java.io.BufferedInputStream;
import java.io.File;
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
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.geo.json.Geometry.DepthSerializationType;
import org.opensha.commons.geo.json.Geometry.MultiPolygon;
import org.opensha.commons.geo.json.Geometry.Point;
import org.opensha.commons.geo.json.Geometry.Polygon;
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
		return fetchShakemapSourceOutlines(eventID, -1);
	}
	
	/**
	 * @param eventID
	 * @param version ShakeMap version to use (or -1 for best available)
	 * @return array of all rupture surface outlines. These outlines are direct from ShakeMap, and will follow
	 * their EdgeRupture specification where points are listed on the upper trace along strike, then the lower
	 * trace in reverse, then close the polygon by repeating the first point
	 */
	public LocationList[] fetchShakemapSourceOutlines(String eventID, int version) {
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
			if (shakemaps.size() > 1 || version >= 0) {
				if (version < 0)
					System.out.println("Found "+shakemaps.size()+" ShakeMaps, using most preferred");
				shakemap = null;
				double maxWeight = Double.NEGATIVE_INFINITY;
				String bestVersion = null;
				for (int i=0; i<shakemaps.size(); i++) {
					JSONObject smObj = (JSONObject)shakemaps.get(i);
					String smVersionStr = ((JSONObject)smObj.get("properties")).get("version").toString();
					int smVersion;
					if (smVersionStr.contains("."))
						smVersion = (int)Double.parseDouble(smVersionStr);
					else
						smVersion = Integer.parseInt(smVersionStr);
//					System.out.println("ShakMap Version: "+smVersionStr);
//					for (Object key : smObj.keySet())
//						System.out.println("\t"+key+": "+smObj.get(key));
					if (version >= 0) {
						if (smVersion == version) {
							System.out.println("Found specified ShakeMap, version "+version);
							shakemap = smObj;
							break;
						}
					} else {
						double weight = Double.parseDouble(smObj.get("preferredWeight").toString());
						System.out.println("\tVersion "+smVersionStr+", weight="+(int)weight);
						if (weight > maxWeight) {
							maxWeight = weight;
							shakemap = smObj;
							bestVersion = smVersionStr;
						}
					}
				}
				if (version < 0)
					System.out.println("Using best version ("+bestVersion+") with weight="+(int)maxWeight);
				else if (shakemap == null)
					System.out.println("Didn't find ShakeMap with version="+version);
			} else {
				shakemap = (JSONObject) shakemaps.get(0);
			}
			if (shakemap == null)
				return null;

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
					}	else if (str.toLowerCase().endsWith("rupture.json")){
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
						System.err.println("Error resolving ShakeMap finite fault URL");
						throw ExceptionUtils.asRuntimeException(e);
					}
					try {
						if (urlStr.toLowerCase().endsWith(".txt"))
							return parseFaultTextFile(url);
						else if (urlStr.toLowerCase().endsWith(".json"))
							return parseFaultJSON(url);
					} catch (IOException e) {
						if(D) System.out.println("No Shakemap");
						System.err.println("Error loading ShakeMap finite fault file");
						throw ExceptionUtils.asRuntimeException(e);
					}
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
		return fetchShakemapSourceSurface(eventID, -1);
	}
	
	/**
	 * Build a RuptureSurface for the given event. For complex surfaces with multiple outlines,
	 * a CompoundSurface will be returned
	 * @param eventID
	 * @param version ShakeMap version to use (or -1 for best available)
	 * @return
	 */
	public RuptureSurface fetchShakemapSourceSurface(String eventID, int version) {
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
	
	private static LocationList[] parseFaultJSON(URL url) throws IOException {
		System.out.println("Parsing "+url);
		InputStream input = UrlUtil.getInputStream(url);
		if ((input instanceof BufferedInputStream))
			input = new BufferedInputStream(input);
		Gson gson = FeatureCollection.buildGson(DepthSerializationType.DEPTH_KM); // ShakeMap stores depths in KM
		// parse feature collection into objects
		FeatureCollection collection = gson.fromJson(new InputStreamReader(input), FeatureCollection.class);
		
		List<LocationList> ret = new ArrayList<>();
		for (Feature feature : collection.features) {
			Geometry geom = feature.geometry;
			if (geom instanceof Polygon) {
				Polygon poly = (Polygon)geom;
				if (poly.polygon != null)
					ret.add(poly.polygon);
			} else if (geom instanceof MultiPolygon) {
				for (Polygon poly : ((MultiPolygon)geom).polygons)
					if (poly != null)
						ret.add(poly.polygon);
			} else if (geom instanceof Point) {
				return null;
			} else {
				throw new IllegalStateException("Unexpected ShakeMap geometry type: "+geom.type);
			}
		}
		return ret.toArray(new LocationList[0]);
	}
	
	public static void main(String[] args) throws IOException {
		ShakeMapFiniteFaultAccessor source = new ShakeMapFiniteFaultAccessor();
//		source.fetchShakemapSourceOutlines("ci38443183", 6);
		source.fetchShakemapSourceOutlines("ci38457511");
		FeatureCollection.read(new File("/tmp/rupture.json"));
	}

}
