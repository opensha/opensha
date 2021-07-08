package org.opensha.commons.geo.json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * This class represents a GeoJSON Feature and provides serialization capabilities.
 * <br>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7946">RFC 7946</a> or the
 * <a href="https://geojson.org/">GeoJSON home page</a> for more information.
 * 
 * @author kevin
 *
 */
@JsonAdapter(Feature.FeatureAdapter.class)
public class Feature {
	
	public final Object id;
	
	public final GeoJSON_Type type = GeoJSON_Type.Feature;
	
	public final Geometry geometry;
	
	public final FeatureProperties properties;
	
	public Feature(Geometry geometry, FeatureProperties properties) {
		this.id = null;
		this.geometry = geometry;
		this.properties = properties;
	}
	
	private Feature(Object id, Geometry geometry, FeatureProperties properties) {
		this.id = id;
		this.geometry = geometry;
		this.properties = properties;
	}
	
	public Feature(Number id, Geometry geometry, FeatureProperties properties) {
		this.id = id;
		this.geometry = geometry;
		this.properties = properties;
	}
	
	public Feature(String id, Geometry geometry, FeatureProperties properties) {
		this.id = id;
		this.geometry = geometry;
		this.properties = properties;
	}
	
	public static class FeatureAdapter extends TypeAdapter<Feature> {
		
		private TypeAdapter<FeatureProperties> propsAdapter;
		private Geometry.GeometryAdapter geomAdapter = new Geometry.GeometryAdapter();
		
		public FeatureAdapter() {
			this.propsAdapter = new FeatureProperties.PropertiesAdapter();
			this.geomAdapter = new Geometry.GeometryAdapter();
		}
		
		public FeatureAdapter(TypeAdapter<FeatureProperties> propsAdapter, Geometry.GeometryAdapter geomAdapter) {
			this.propsAdapter = propsAdapter;
			this.geomAdapter = geomAdapter;
		}

		@Override
		public void write(JsonWriter out, Feature value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			
			out.beginObject();
			
			out.name("type").value(value.type.name());
			
			if (value.id != null) {
				out.name("id");
				if (value.id instanceof Number)
					out.value((Number)value.id);
				else
					out.value(value.id.toString());
			}
			
			out.name("properties");
			if (value.properties == null)
				// some parsers fail on null properties, even though the spec says null is file
				// just give an empty properties object instead
				out.beginObject().endObject();
			else
				propsAdapter.write(out, value.properties);
			
			out.name("geometry");
			if (value.geometry == null)
				out.nullValue();
			else
				geomAdapter.write(out, value.geometry);
			
			out.endObject();
		}

		@Override
		public Feature read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			
//			System.out.print("Deserializing feature at "+in.getPath());
			
			in.beginObject();
			GeoJSON_Type type = null;
			Object id = null;
			Geometry geometry = null;
			FeatureProperties properties = null;
			
			while (in.hasNext()) {
				String name = in.nextName();
				JsonToken peek = in.peek();
				
//				System.out.println("name: "+name+" is "+peek);
				
				switch (name) {
				case "type":
					type = GeoJSON_Type.valueOf(in.nextString());
					Preconditions.checkState(type == GeoJSON_Type.Feature, "Expected Feature type, have %s", type);
					break;
				case "id":
					if (peek == JsonToken.NUMBER)
						id = FeatureProperties.parseNumber(in.nextString());
					else
						id = in.nextString();
					break;
				case "geometry":
					if (peek == JsonToken.NULL)
						in.nextNull();
					else
						geometry = geomAdapter.read(in);
					break;
				case "properties":
					if (peek == JsonToken.NULL)
						in.nextNull();
					else
						properties = propsAdapter.read(in);
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			Preconditions.checkState(type == GeoJSON_Type.Feature, "Expected Feature type, have %s", type);
			// everything else can be null
			
			in.endObject();
			
			return new Feature(id, geometry, properties);
		}
		
	}
	
	/**
	 * @return GeoJSON representation of this Feature
	 * @throws IOException
	 */
	public String toJSON() throws IOException {
		StringWriter writer = new StringWriter();
		write(this, writer);
		return writer.toString();
	}
	
	/**
	 * Parses a Feature from GeoJSON
	 * 
	 * @param json
	 * @return
	 * @throws IOException
	 */
	public static Feature fromJSON(String json) throws IOException {
		StringReader reader = new StringReader(json);
		return read(reader);
	}
	
	/**
	 * Reads a Feature from the given GeoJSON file
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static Feature read(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return read(reader);
	}
	
	/**
	 * Reads a Feature from the given reader
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static Feature read(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		Feature ret;
		synchronized (FeatureCollection.gson_default) {
			ret = FeatureCollection.gson_default.fromJson(reader, Feature.class);
			reader.close();
		}
		return ret;
	}
	
	/**
	 * Writes a Feature to the given GeoJSON file
	 * 
	 * @param features
	 * @param jsonFile
	 * @throws IOException
	 */
	public static void write(Feature feature, File jsonFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		write(feature, writer);
		writer.close();
	}
	
	/**
	 * Writes a Feature to the given writer
	 * 
	 * @param features
	 * @param writer
	 * @throws IOException
	 */
	public static void write(Feature feature, Writer writer) throws IOException {
		if (!(writer instanceof BufferedWriter))
			writer = new BufferedWriter(writer);

		synchronized (FeatureCollection.gson_default) {
			FeatureCollection.gson_default.toJson(feature, Feature.class, writer);
			writer.flush();
		}
	}
	
	public static Feature getWithID(Feature feature, Number id) {
		return new Feature(id, feature.geometry, feature.properties);
	}
	
	public static Feature getWithID(Feature feature, String id) {
		return new Feature(id, feature.geometry, feature.properties);
	}

}
