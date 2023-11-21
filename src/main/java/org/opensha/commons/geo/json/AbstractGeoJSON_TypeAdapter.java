package org.opensha.commons.geo.json;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class AbstractGeoJSON_TypeAdapter<T> extends TypeAdapter<T> {
	
	/**
	 * Read in assuming the the json object has already been opened. If type is non null, it is assumed that the
	 * type field has already been parsed externally (and will not be encountered again). The json object will not be closed.
	 * 
	 * @param in
	 * @param type declared type if already read
	 * @return
	 * @throws IOException 
	 */
	public abstract T innerReadAsType(JsonReader in, GeoJSON_Type type) throws IOException;

}
