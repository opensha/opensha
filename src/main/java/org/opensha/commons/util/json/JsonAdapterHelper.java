package org.opensha.commons.util.json;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;


/**
 * Helper class to serialize and deserialize instances of classes that have a TypeAdapter (and are annotated with
 * @JsonAdapter(TypeAdapter), or to fetch default Gson TypeAdapter instances.
 */
@SuppressWarnings("unchecked")
public class JsonAdapterHelper {

	/**
	 * Returns the {@link TypeAdapter} class declared via {@link JsonAdapter} on the supplied object's runtime class.
	 *
	 * @param o object whose class should be inspected
	 * @return adapter class if present and assignable to {@link TypeAdapter}, otherwise {@code null}
	 */
    public static Class getTypeAdapterClass(Object o) {
        JsonAdapter annotation = o.getClass().getAnnotation(JsonAdapter.class);
        if (annotation != null) {
            Class c = annotation.value();
            if (TypeAdapter.class.isAssignableFrom(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Initializes and returns the {@link TypeAdapter} declared via {@link JsonAdapter} on the supplied object.
     *
     * @param o object whose class should be inspected
     * @return initialized adapter instance, or {@code null} if no adapter annotation exists or initialization fails
     */
    public static TypeAdapter getTypeAdapter(Object o) {
       return getTypeAdapter(o, false);
    }
    
    private static Gson defaultGson;
    
    private static void checkInitDefaultGson() {
    	if (defaultGson == null) {
    		synchronized (JsonAdapterHelper.class) {
    			if (defaultGson == null)
    				defaultGson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    		}
    	}
    }

    /**
     * Initializes and returns the {@link TypeAdapter} for the supplied object.
     *
     * @param o object whose class should be inspected
     * @param revertToGsonDefault if {@code true}, return Gson's default adapter for the runtime class when no
     *        {@link JsonAdapter} annotation is present (or initialization fails)
     * @return initialized adapter instance, or {@code null} if none can be determined
     */
    public static TypeAdapter getTypeAdapter(Object o, boolean revertToGsonDefault) {
        Class c = getTypeAdapterClass(o);
        if (c == null && revertToGsonDefault) {
        	checkInitDefaultGson();
        	return defaultGson.getAdapter(o.getClass());
        }
        return initTypeAdapter(c, revertToGsonDefault);
    }

    /**
     * Initializes a {@link TypeAdapter} instance from the supplied adapter class.
     *
     * @param c adapter class
     * @param revertToGsonDefault if {@code true}, return Gson's default adapter for {@code c} when adapter
     *        initialization fails
     * @return initialized adapter instance, or {@code null} if {@code c} is {@code null} or initialization fails
     */
    public static TypeAdapter initTypeAdapter(Class c, boolean revertToGsonDefault) {
        if (c != null) {
            try {
                return (TypeAdapter) c.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
            	if (revertToGsonDefault) {
            		checkInitDefaultGson();
                	return defaultGson.getAdapter(c);
            	}
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Returns {@code true} if the supplied object has an initializable {@link TypeAdapter} declared via
     * {@link JsonAdapter}.
     *
     * @param o object whose class should be inspected
     * @return {@code true} if an adapter can be initialized, otherwise {@code false}
     */
    public static boolean hasTypeAdapter(Object o) {
        return hasTypeAdapter(o, false);
    }

    /**
     * Returns {@code true} if the supplied object has a usable adapter.
     *
     * @param o object whose class should be inspected
     * @param revertToGsonDefault if {@code true}, treat Gson's default adapter as a valid fallback
     * @return {@code true} if an adapter can be initialized, otherwise {@code false}
     */
    public static boolean hasTypeAdapter(Object o, boolean revertToGsonDefault) {
        return getTypeAdapter(o, revertToGsonDefault) != null;
    }

    /**
     * Writes value to out using the TypeAdapter associated with value's class.
     *
     * @param out
     * @param value
     * @throws IOException
     */
    public static void writeAdapterValue(JsonWriter out, Object value) throws IOException {
        TypeAdapter adapter = getTypeAdapter(value);
        writeAdapterValue(out, adapter, value);
    }

    /**
     * Writes value to out using the specified adapter.
     *
     * @param out
     * @param value
     * @throws IOException
     */
    public static void writeAdapterValue(JsonWriter writer, TypeAdapter adapter, Object value) throws IOException {
        writer.beginObject();
        writer.name("adapterClass");
        writer.value(adapter.getClass().getName());
        writer.name("adapterData");
        adapter.write(writer, value);
        writer.endObject();
    }

    /**
     * Reads in an object that was written with writeAdapterValue(). The TypeAdapter class specified
     * in the JSON must be available to the ClassLoader.
     *
     * @param in
     * @return
     * @throws IOException
     */
    public static Object readAdapterValue(JsonReader in) throws IOException {
        in.beginObject();
        Preconditions.checkArgument("adapterClass".equals(in.nextName()));
        String adapterName = in.nextString();
        TypeAdapter adapter = null;
        try {
            Class<? extends TypeAdapter> rawClass = (Class<? extends TypeAdapter>) Class.forName(adapterName);
            adapter = rawClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            System.err.println("WARNING: couldn't locate logic tree branch node adapter class '" + adapterName + "', "
                    + "returning null instead");
        } catch (ClassCastException e) {
            System.err.println("WARNING: logic tree branch node adapter class '" + adapterName + "' is of the wrong type, "
                    + "returning null instead");
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        Preconditions.checkArgument("adapterData".equals(in.nextName()));
        Object result = null;
        if (adapter != null) {
            result = adapter.read(in);
        } else {
            in.skipValue();
        }
        in.endObject();
        return result;
    }
}
