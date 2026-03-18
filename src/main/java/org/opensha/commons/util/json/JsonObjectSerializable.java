package org.opensha.commons.util.json;

import java.io.IOException;
import java.lang.reflect.Constructor;

import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

/**
 * Interface for an object that is serializable to an {@link JsonObject} and can be deserialized via a default
 * constructor and that same object. This can be used in lieu of a TypeAdapter if that pattern does not fit well
 */
public interface JsonObjectSerializable {
	
	public JsonObject toJsonObject();
	
	public void initFromJsonObject(JsonObject jsonObj);
	
	@SuppressWarnings("unchecked")
	public static <E extends JsonObjectSerializable> E initialize(String className, JsonObject jsonObj) throws Exception {
		Class<?> rawClass = Class.forName(className);
		Class<? extends E> clazz = (Class<? extends E>)rawClass;
		return initialize(clazz, jsonObj);
	}
	
	public static <E extends JsonObjectSerializable> E initialize(Class<E> clazz, JsonObject jsonObj) throws Exception {
		Constructor<? extends E> constructor = clazz.getDeclaredConstructor();
		constructor.setAccessible(true);
		
		E ret = constructor.newInstance();
		
		ret.initFromJsonObject(jsonObj);
		
		return ret;
	}
	
	public static void writeJsonObjectToWriter(JsonObject jsonObj, JsonWriter out) throws IOException {
		Streams.write(jsonObj, out);
	}

}
