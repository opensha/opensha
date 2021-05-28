package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public interface JSON_BackedModule extends StatefulModule {
	
	/**
	 * File name to use for the JSON that represents this module
	 * 
	 * @return
	 */
	public String getJSON_FileName();

	@Override
	public default void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		String entryName = StatefulModule.getEntryName(entryPrefix, getJSON_FileName());
		
		ZipEntry entry = new ZipEntry(entryName);
		zout.putNextEntry(entry);
		
		Gson gson = buildGson();
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
		JsonWriter out = gson.newJsonWriter(writer);
		writeToJSON(out, gson);
		out.flush();
		writer.write("\n");
		writer.flush();
		zout.closeEntry();
	}

	@Override
	public default void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		String entryName = StatefulModule.getEntryName(entryPrefix, getJSON_FileName());
		ZipEntry entry = zip.getEntry(entryName);
		Preconditions.checkNotNull(entry, "Entry not found in zip archive: %s", entryName);
		
		Gson gson = buildGson();
		
		InputStream zin = zip.getInputStream(entry);
		BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
		JsonReader in = gson.newJsonReader(reader);
		initFromJSON(in, gson);
		in.close();
		
		zin.close();
	}
	
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

}