package org.opensha.commons.logicTree;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;


/**
 * Helper class to serialize and deserialize instances of classes that have a TypeAdapter.
 * Classes must be annotated with @JsonAdapter(TypeAdapter)
 */
@SuppressWarnings("unchecked")
public class JsonAdapterHelper {

    protected static Class getTypeAdapterClass(Object o) {
        JsonAdapter annotation = o.getClass().getAnnotation(JsonAdapter.class);
        if (annotation != null) {
            Class c = annotation.value();
            if (TypeAdapter.class.isAssignableFrom(c)) {
                return c;
            }
        }
        return null;
    }

    protected static TypeAdapter getTypeAdapter(Object o) {
        Class c = getTypeAdapterClass(o);
        if (c != null) {
            try {
                return (TypeAdapter) c.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean hasTypeAdapter(Object o) {
        return getTypeAdapter(o) != null;
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
