package org.opensha.commons.geo.json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.EnumSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

/**
 * Enum of valid GeoJSON types
 * 
 * @author kevin
 *
 */
public enum GeoJSON_Type {
	
	Feature,
	FeatureCollection,
	GeometryCollection,
	Point,
	MultiPoint,
	LineString,
	MultiLineString,
	Polygon,
	MultiPolygon;
	
	public static final EnumSet<GeoJSON_Type> GEOM_TYPES = EnumSet.range(GeometryCollection, MultiPolygon);
	
	static final Gson gson_default = new GsonBuilder().create();
	
	public static GeoJSON_Type detect(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return detect(reader);
	}
	
	public static GeoJSON_Type detect(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		
		FeatureCollection ret;
		synchronized (gson_default) {
			JsonReader in = gson_default.newJsonReader(reader);
			
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					GeoJSON_Type type = GeoJSON_Type.valueOf(in.nextString());
					in.close();
					reader.close();
					return type;

				default:
					in.skipValue();
					break;
				}
			}
			in.close();
			reader.close();
		}
		throw new IllegalStateException("Type not found, are you sure this is GeoJSON?");
	}
	
	public static void main(String[] args) throws IOException {
		GeoJSON_Type type = detect(new File("/home/kevin/workspace/opensha/src/main/resources/data/erf/nshm23/"
				+ "seismicity/regions/stitched/conus-west.geojson"));
		System.out.println("Detected type: "+type);
	}

}
