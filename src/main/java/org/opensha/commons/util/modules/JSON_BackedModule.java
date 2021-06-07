package org.opensha.commons.util.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Helper interface for {@link ArchivableModule}'s that are backed by a single CSV file. Implementations need only
 * implement {@link #getFileName()}, {@link #writeToJSON(JsonWriter, Gson)}, and {@link #initFromJSON(JsonReader, Gson)}.
 * <p>
 * Implementations can also attach any custom TypeAdapters, or otherwise control Gson settings by overriding
 * {@link #buildGson()}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface JSON_BackedModule extends FileBackedModule {
	
	/**
	 * Writes this module to the given JsonWriter instance
	 * 
	 * @param out
	 * @param gson
	 * @throws IOException
	 */
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException;
	
	/**
	 * Initializes this writer from the given JsonReader instance
	 * 
	 * @param in
	 * @param gson
	 * @throws IOException
	 */
	public void initFromJSON(JsonReader in, Gson gson) throws IOException;
	
	/**
	 * Initializes a Gson instance for [de]serialization of this module. Default implementation just enables
	 * pretty printing. Intercept this method if you need to add any custom TypeAdapters for use during
	 * [de]serialization.
	 * 
	 * @return
	 */
	public default Gson buildGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		
		return builder.create();
	}

	@Override
	default void writeToStream(BufferedOutputStream out) throws IOException {
		Gson gson = buildGson();
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
		JsonWriter jout = gson.newJsonWriter(writer);
		writeToJSON(jout, gson);
	}

	@Override
	default void initFromStream(BufferedInputStream in) throws IOException {
		Gson gson = buildGson();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		JsonReader jin = gson.newJsonReader(reader);
		initFromJSON(jin, gson);
	}
}
