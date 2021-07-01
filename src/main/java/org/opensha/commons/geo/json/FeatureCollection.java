package org.opensha.commons.geo.json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.json.Feature.FeatureAdapter;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * This class represents a GeoJSON FeatureCollection and provides serialization capabilities.
 * <br>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7946">RFC 7946</a> or the
 * <a href="https://geojson.org/">GeoJSON home page</a> for more information.
 * 
 * @author kevin
 *
 */
@JsonAdapter(FeatureCollection.FeatureCollectionAdapter.class)
public class FeatureCollection {
	
	public final GeoJSON_Type type = GeoJSON_Type.FeatureCollection;
	
	public final ImmutableList<Feature> features;
	
	public FeatureCollection(List<Feature> features) {
		this.features = features == null ? null : ImmutableList.copyOf(features);
	}
	
	private static FeatureAdapter featureAdapter = new FeatureAdapter();
	
	public static class FeatureCollectionAdapter extends TypeAdapter<FeatureCollection> {

		@Override
		public void write(JsonWriter out, FeatureCollection value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			
			out.name("type").value(value.type.name());
			
			out.name("features");
			if (value.features == null) {
				out.nullValue();
			} else {
				out.beginArray();
				
				for (Feature feature : value.features)
					featureAdapter.write(out, feature);
				
				out.endArray();
			}
			
			out.endObject();
		}

		@Override
		public FeatureCollection read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			
			in.beginObject();
			
			GeoJSON_Type type = null;
			List<Feature> features = null;
			
			while (in.hasNext()) {
				String name = in.nextName();
				
				switch (name) {
				case "type":
					type = GeoJSON_Type.valueOf(in.nextString());
					Preconditions.checkState(type == GeoJSON_Type.FeatureCollection, "Expected Feature type, have %s", type);
					break;
				case "features":
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
					} else {
						features = new ArrayList<>();
						in.beginArray();
						
						while (in.hasNext())
							features.add(featureAdapter.read(in));
						
						in.endArray();
					}
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			Preconditions.checkState(type == GeoJSON_Type.FeatureCollection, "Expected Feature type, have %s", type);
			
			return new FeatureCollection(features);
		}
		
	}
	
	/**
	 * Reads a FeatureCollection from the given GeoJSON file
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static FeatureCollection read(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return read(reader);
	}
	
	/**
	 * Reads a FeatureCollection from the given reader
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static FeatureCollection read(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		Gson gson = new GsonBuilder().create();
		FeatureCollection ret = gson.fromJson(reader, FeatureCollection.class);
		reader.close();
		return ret;
	}
	
	/**
	 * Writes a FeatureCollection to the given GeoJSON file
	 * 
	 * @param features
	 * @param jsonFile
	 * @throws IOException
	 */
	public static void write(FeatureCollection features, File jsonFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		write(features, writer);
		writer.close();
	}
	
	/**
	 * Writes a FeatureCollection to the given writer
	 * 
	 * @param features
	 * @param writer
	 * @throws IOException
	 */
	public static void write(FeatureCollection features, Writer writer) throws IOException {
		if (!(writer instanceof BufferedWriter))
			writer = new BufferedWriter(writer);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(features, FeatureCollection.class, writer);
		
		writer.flush();
	}

}
