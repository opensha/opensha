package org.opensha.commons.util.modules.helpers;

import java.io.IOException;
import java.lang.reflect.Type;

import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Helper interface for {@link ArchivableModule}'s that are backed by a single JSON file and serialized via a
 * {@link TypeAdapter}. Implementations need only implement {@link #getFileName()},
 * {@link #registerTypeAdapters(GsonBuilder)}, {@link #get()}, and {@link #set(Object)}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface JSON_TypeAdapterBackedModule<E> extends JSON_BackedModule {
	
	public Type getType();
	
	public E get();
	
	public void set(E value);

	@Override
	public default void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		E value = get();
		Preconditions.checkNotNull("Cannot serialize null object");
		gson.toJson(value, getType(), out);
	}

	@Override
	public default void initFromJSON(JsonReader in, Gson gson) throws IOException {
		E value = gson.fromJson(in, getType());
		set(value);
	}
	
	public void registerTypeAdapters(GsonBuilder builder);

	@Override
	public default Gson buildGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		
		registerTypeAdapters(builder);
		
		return builder.create();
	}

}
