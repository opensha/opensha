package org.opensha.commons.geo.json;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSet.XYAdapter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature.FeatureAdapter;
import org.opensha.commons.geo.json.FeatureCollection.FeatureCollectionAdapter;
import org.opensha.commons.geo.json.Geometry.GeometryAdapter;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * This class represents the properties object of GeoJSON Feature and provides serialization capabilities.
 * <br>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7946">RFC 7946</a> or the
 * <a href="https://geojson.org/">GeoJSON home page</a> for more information.
 * 
 * @author kevin
 *
 */
@JsonAdapter(FeatureProperties.PropertiesAdapter.class)
public class FeatureProperties extends LinkedHashMap<String, Object> {
	// LinkedHashMap for insertion order tracking

	private static final long serialVersionUID = 1L;

	
	public FeatureProperties() {
		
	}
	
	public FeatureProperties(Map<String, Object> other) {
		super(other);
	}
	
	/**
	 * Tries to return the value of the given parameter as a boolean. If the parameter value is a boolean, that boolean will be
	 * returned. If the value is a String, it will return true if the value, ignoring case, equals 'true' or 'yes',
	 * otherwise false. If the value is null or neither a Boolean nor a String, the supplied default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return boolean value
	 */
	public boolean getBoolean(String name, boolean defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof Boolean) {
			return (Boolean)val;
		} else if (val instanceof String) {
			String str = (String)val;
			str = str.trim().toLowerCase();
			if (str.equals("true") || str.equals("yes"))
				return true;
			return false;
		}
		System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+val.getClass().getName());
		return defaultValue;
	}
	
	/**
	 * Tries to return the value of the given parameter as a double. If parameter value is a Number, then Number.doubleValue()
	 * will be returned. If the value is a String, then that String will first be parsed to a Number. Otherwise, the supplied
	 * default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return double value
	 */
	public double getDouble(String name, double defaultValue) {
		return getNumber(name, defaultValue).doubleValue();
	}
	
	/**
	 * Tries to return the value of the given parameter as an int. If parameter value is a Number, then Number.intValue()
	 * will be returned. If the value is a String, then that String will first be parsed to a Number. Otherwise, the supplied
	 * default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return int value
	 */
	public int getInt(String name, int defaultValue) {
		return getNumber(name, defaultValue).intValue();
	}
	
	/**
	 * Tries to return the value of the given parameter as a long. If parameter value is a Number, then Number.longValue()
	 * will be returned. If the value is a String, then that String will first be parsed to a Number. Otherwise, the supplied
	 * default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return long value
	 */
	public long getLong(String name, long defaultValue) {
		return getNumber(name, defaultValue).longValue();
	}
	
	/**
	 * Tries to return the value of the given parameter as a Number. If parameter value is a Number, that will be returned.
	 * If the value is a String, then that String will first be parsed to a Number. Otherwise, null will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return Number value
	 */
	public Number getNumber(String name) {
		return getNumber(name, null);
	}
	
	/**
	 * Tries to return the value of the given parameter as a Number. If parameter value is a Number, that will be returned.
	 * If the value is a String, then that String will first be parsed to a Number. Otherwise, the supplied
	 * default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return Number value
	 */
	public Number getNumber(String name, Number defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		return asNumber(val, name, defaultValue);
	}
	
	private static Number asNumber(Object val, String name, Number defaultValue) {
		if (val == null)
			return defaultValue;
		if (val instanceof String) {
			// try to parse string
			try {
				String str = (String)val;
				if (str.equalsIgnoreCase(Double.NaN+""))
					return Double.NaN;
				if (str.equalsIgnoreCase(Double.POSITIVE_INFINITY+""))
					return Double.POSITIVE_INFINITY;
				if (str.equalsIgnoreCase(Double.NEGATIVE_INFINITY+""))
					return Double.NEGATIVE_INFINITY;
				if (str.contains(".") || str.toLowerCase().contains("e"))
					return Double.parseDouble(str);
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				System.err.println("Feature property with name '"+name
						+"' is of a string that could not be parsed to a number: "+e.getMessage());
				return defaultValue;
			}
		}
		try {
			return (Number)val;
		} catch (ClassCastException e) {
			System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
	}
	
	/**
	 * Tries to return the value of the given parameter as a double array. See {@link #getNumberArray(String)}.
	 * 
	 * @param name
	 * @return
	 */
	public double[] getDoubleArray(String name) {
		Number[] numbers = getNumberArray(name);
		if (numbers == null)
			return null;
		double[] ret = new double[numbers.length];
		for (int i=0; i<numbers.length; i++)
			ret[i] = numbers[i].doubleValue();
		return ret;
	}
	
	/**
	 * Tries to return the value of the given parameter as an int array. See {@link #getNumberArray(String)}.
	 * 
	 * @param name
	 * @return
	 */
	public int[] getIntArray(String name) {
		Number[] numbers = getNumberArray(name);
		if (numbers == null)
			return null;
		int[] ret = new int[numbers.length];
		for (int i=0; i<numbers.length; i++)
			ret[i] = numbers[i].intValue();
		return ret;
	}
	
	/**
	 * Tries to return the value of the given parameter as a long array. See {@link #getNumberArray(String)}.
	 * 
	 * @param name
	 * @return
	 */
	public long[] getLongArray(String name) {
		Number[] numbers = getNumberArray(name);
		if (numbers == null)
			return null;
		long[] ret = new long[numbers.length];
		for (int i=0; i<numbers.length; i++)
			ret[i] = numbers[i].longValue();
		return ret;
	}
	
	/**
	 * Tries to return the value of the given parameter as a Number array. If parameter value is a list/array of Numbers,
	 * that will be returned. If any sub-value is a String, then that String will first be parsed to a Number.
	 * Otherwise, the null will be returned.
	 * 
	 * @param name
	 * @return
	 */
	public Number[] getNumberArray(String name) {
		Object val = get(name);
		if (val == null)
			return null;
		List<Number> numbers = new ArrayList<>();
		if (val instanceof List<?>) {
			for (Object subVal : (List<?>)val) {
				Number number = asNumber(subVal, name, null);
				if (number == null)
					return null;
				numbers.add(number);
			}
		} else if (val.getClass().isArray()) {
			for (Object subVal : (Object[])val) {
				Number number = asNumber(subVal, name, null);
				if (number == null)
					return null;
				numbers.add(number);
			}
		}
		return numbers.toArray(new Number[0]);
	}
	
	/**
	 * Tries to return the value of the given parameter as a Location, either by returning the value directly if it's
	 * already a location (e.g., was set in memory as a location) or parsing a location array of lon, lat, and optinoally
	 * depth (using the default depth serialization, see {@link Geometry#DEPTH_SERIALIZATION_DEFAULT}.
	 * 
	 * If the stored value is not a location or number array, or that array is an unexpected size, null will be returned.
	 * May throw an exception when attempting to build a location with bad lat/lon/depth values.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public Location getLocation(String name) {
		return getLocation(name, null);
	}
	
	/**
	 * Tries to return the value of the given parameter as a Location, either by returning the value directly if it's
	 * already a location (e.g., was set in memory as a location) or parsing a location array of lon, lat, and optinoally
	 * depth (using the default depth serialization, see {@link Geometry#DEPTH_SERIALIZATION_DEFAULT}.
	 * 
	 * If the stored value is not a location or number array, or that array is an unexpected size, the passed in default
	 * value will be returned. May throw an exception when attempting to build a location with bad lat/lon/depth values.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public Location getLocation(String name, Location defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof Location)
			return (Location)val;
		// try loading it as a location
		double[] array = getDoubleArray(name);
		if (array == null || array.length < 2 || array.length > 3)
			return null;
		double lon = array[0];
		double lat = array[1];
		double depth = array.length == 3 ? Geometry.DEPTH_SERIALIZATION_DEFAULT.fromGeoJSON(array[2]) : 0d;
		return new Location(lat, lon, depth);
	}
	
	/**
	 * Tries to retrieve the value with the given name as a FeatureProperties instance, and returns null if no value
	 * exists or it's of an unxepected type.
	 * 
	 * @param name
	 * @return
	 */
	public FeatureProperties getProperties(String name) {
		return getProperties(name, null);
	}
	
	/**
	 * Tries to retrieve the value with the given name as a FeatureProperties instance, and returns the supplied
	 * default value if no value exists or it's of an unxepected type.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public FeatureProperties getProperties(String name, FeatureProperties defaultValue) {
		Object val = get(name);
		FeatureProperties props = getAsFeatureProps(val);
		if (props == null)
			return defaultValue;
		return props;
	}

	@SuppressWarnings("unchecked")
	private FeatureProperties getAsFeatureProps(Object val) {
		if (val == null)
			return null;
		if (val instanceof FeatureProperties)
			return (FeatureProperties)val;
		if (val instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>)val;
			if (map.isEmpty() || map.keySet().iterator().next() instanceof String) {
				try {
					return new FeatureProperties((Map<String, Object>)map);
				} catch (Exception e) {}
			}
		}
		return null;
	}
	
	/**
	 * Tries to retrieve the value with the given name as a list of FeatureProperties instances, and returns null if no
	 * value exists or it's of an unxepected type.
	 * 
	 * @param name
	 * @return
	 */
	public List<FeatureProperties> getPropertiesList(String name) {
		return getPropertiesList(name, null);
	}
	
	/**
	 * Tries to retrieve the value with the given name as a list of  FeatureProperties instances, and returns the supplied
	 * default value if no value exists or it's of an unxepected type.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public List<FeatureProperties> getPropertiesList(String name, List<FeatureProperties> defaultValue) {
		Object val = get(name);
		if (val == null || !(val instanceof Collection<?>))
			return defaultValue;
		Collection<?> inputList = (Collection<?>)val;
		List<FeatureProperties> ret = new ArrayList<>();
		for (Object obj : inputList) {
			if (obj == null) {
				ret.add(null);
			} else {
				FeatureProperties props = getAsFeatureProps(obj);
				if (props == null)
					return defaultValue;
				ret.add(props);
			}
		}
		return ret;
	}
	
	/*
	 * See: https://github.com/mapbox/simplestyle-spec/tree/master/1.1.0
	 */
	public static final String STROKE_COLOR_PROP = "stroke";
	public static final String STROKE_WIDTH_PROP = "stroke-width";
	public static final String STROKE_OPACITY_PROP = "stroke-opacity";
	
	public static final String FILL_COLOR_PROP = "fill";
	public static final String FILL_OPACITY_PROP = "fill-opacity";
	
	public static final String MARKER_COLOR_PROP = "marker-color";
	public static final String MARKER_SIZE_PROP = "marker-size";
	public static final String MARKER_SIZE_SMALL = "small";
	public static final String MARKER_SIZE_MEDIUM = "medium";
	public static final String MARKER_SIZE_LARGE = "large";
	
	/**
	 * Tries to read the property with the given name as a Color and returns null if this fails.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public Color getColor(String name) {
		return getColor(name, null);
	}
	
	/**
	 * Tries to read the property with the given name as a Color and returns the supplied default value if this fails.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public Color getColor(String name, Color defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof Color)
			return (Color)val;
		// try loading it as a Color
		String str = val.toString();
		try {
			return Color.decode(str);
		} catch (NumberFormatException e) {
			System.err.println("Feature property with name '"+name+"' and value '"
					+str+"' could not be parsed as a color, returning default: "+e.getMessage());
			return defaultValue;
		}
	}
	
	/**
	 * Tries to read the property with the given name as a String and returns null if this fails. Non-string objects
	 * will return the default value, not their toString() value (use get(name).toString() if you need that).
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public String getString(String name) {
		return getString(name, null);
	}
	
	/**
	 * Tries to read the property with the given name as a String and returns the supplied default value if this fails.
	 * Non-string objects will return the default value, not their toString() value (use get(name).toString() if you need that).
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public String getString(String name, String defaultValue) {
		Object val = get(name);
		if (val == null || !(val instanceof String))
			return defaultValue;
		return (String)val;
	}
	
	/**
	 * Returns the value of the given parameter, cast to the expected type. Returns the default value if a ClassCastException
	 * is encountered or the value is null.
	 * 
	 * @param <E>
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked") // is caught
	public <E> E get(String name, E defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		try {
//			System.out.println(val+"\t"+val.getClass());
			return (E)val;
		} catch (ClassCastException e) {
			System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
	}
	
	/**
	 * Returns the value of the given parameter, cast to the expected type. Returns null if a ClassCastException
	 * is encountered or the value is null.
	 * 
	 * @param <E>
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked") // is caught
	public <E> E getAsType(String name, TypeToken<E> type) {
		Object val = get(name);
		if (val == null)
			return null;
		try {
//			System.out.println(val+"\t"+val.getClass());
			return (E)val;
		} catch (ClassCastException e) {
			System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return null;
		}
	}
	
	/**
	 * Returns the value of the given parameter, cast to the expected type. Returns null if a ClassCastException
	 * is encountered or the value is null.
	 * 
	 * @param <E>
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked") // is caught
	public <E> E getAsType(String name, Class<E> type) {
		Object val = get(name);
		if (val == null)
			return null;
		try {
//			System.out.println(val+"\t"+val.getClass());
			return (E)val;
		} catch (ClassCastException e) {
			System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return null;
		}
	}
	
	/**
	 * This requires that a property with the given name and type exists, and returns it
	 * 
	 * @param <E>
	 * @param name
	 * @param type
	 * @return property with the given name and of the given type
	 * @throws NullPointerException if no value is present with that name
	 * @throws ClassCastException if a value exists but of a different type
	 */
	public <E> E require(String name, Class<E> type) {
		Object val = get(name);
		Preconditions.checkNotNull(val, "No value present named '%s'", name);
		return type.cast(val);
	}
	
	/**
	 * Similar to {@link FeatureProperties#put(String, Object)}, except that a null value will remove the property
	 * from the map
	 * 
	 * @param name
	 * @param value
	 */
	public void set(String name, Object value) {
		if (value == null)
			remove(name);
		else
			super.put(name, value);
	}
	
	/**
	 * Sets a the property with the given name to the given value if <code>condition == true</code>, otherwise
	 * removes the property with the given name.
	 * 
	 * @param name
	 * @param value
	 * @param condition
	 */
	public void setConditional(String name, Object value, boolean condition) {
		if (condition)
			set(name, value);
		else
			remove(name);
	}
	
	public static class PropertiesAdapter extends TypeAdapter<FeatureProperties> {
		
		/**
		 * Method to serialize a property with the given name to the given JsonWriter, assuming that the name
		 * has already been set in the JsonWriter. Can be overridden to provide custom serialization for specific properties
		 * 
		 * @param out
		 * @param name
		 * @param value
		 * @return
		 * @throws IOException
		 */
		protected void serialize(JsonWriter out, String name, Object value) throws IOException {
			propSerializeDefault(out, value);
		}

		@Override
		public void write(JsonWriter out, FeatureProperties properties) throws IOException {
			if (properties == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			
			for (String name : properties.keySet()) {
				out.name(name);
				Object value = properties.get(name);
				propSerializeDefault(out, value);
			}
			
			out.endObject();
		}
		
		/**
		 * Method to deserialize a property from the given JsonReader with the given name. Can be overridden
		 * to provide custom deserialization for specific properties
		 * 
		 * @param in
		 * @param name
		 * @return
		 * @throws IOException
		 */
		protected Object deserialize(JsonReader in, String name) throws IOException {
			return propDeserializeDefault(in);
		}

		@Override
		public FeatureProperties read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			in.beginObject();
			
			FeatureProperties properties = new FeatureProperties(); // linked for insertion order tracking
			
			while (in.hasNext()) {
				String name = in.nextName();
//				System.out.println("Deserializing "+name);
				Object value = deserialize(in, name);
				
				properties.put(name, value);
			}
			
			in.endObject();
			return properties;
		}

		/**
		 * Utility method to read assuming that we've already inside of the object and have ingested the first name
		 * 
		 * @param in
		 * @param name0
		 * @return
		 * @throws IOException
		 */
		public FeatureProperties readAfterFirstName(JsonReader in, String name0) throws IOException {
			FeatureProperties properties = new FeatureProperties(); // linked for insertion order tracking
			
			Object value0 = deserialize(in, name0);
			properties.put(name0, value0);
			
			while (in.hasNext()) {
				String name = in.nextName();
//				System.out.println("Deserializing "+name);
				Object value = deserialize(in, name);
				
				properties.put(name, value);
			}
			
			in.endObject();
			return properties;
		}

		/**
		 * Utility method to read assuming that we've already inside of the object and have ingested the first name
		 * and the first value
		 * 
		 * @param in
		 * @param name0
		 * @return
		 * @throws IOException
		 */
		public FeatureProperties readAfterFirstNameAndValue(JsonReader in, String name0, Object value0) throws IOException {
			FeatureProperties properties = new FeatureProperties(); // linked for insertion order tracking
			
			properties.put(name0, value0);
			
			while (in.hasNext()) {
				String name = in.nextName();
//				System.out.println("Deserializing "+name);
				Object value = deserialize(in, name);
				
				properties.put(name, value);
			}
			
			in.endObject();
			return properties;
		}
		
	}
	
	private static XYAdapter xyAdapter = new XYAdapter();
	private static PropertiesAdapter propAdapter = new PropertiesAdapter();
	private static GeometryAdapter geomAdapter = new GeometryAdapter();
	private static FeatureAdapter featureAdapter = new FeatureAdapter();
	private static FeatureCollectionAdapter featureCollectionAdapter = new FeatureCollectionAdapter();
	
	/**
	 * Serializes the given value to the given JsonWriter, which should already have the name set. Numbers
	 * are serialized as numbers, nulls as nulls, booleans as booleans, collections as arrays, arrays as arrays,
	 * Locations as Locations, and everything else as toString().
	 * 
	 * @param out
	 * @param value
	 * @throws IOException
	 */
	public static void propSerializeDefault(JsonWriter out, Object value) throws IOException {
		if (value == null) {
			out.nullValue();
		} else if (value instanceof Number) {
			out.value((Number)value);
		} else if (value instanceof Boolean) {
			out.value((Boolean)value);
		} else if (value instanceof Location) {
			Geometry.serializeLoc(out, (Location)value, Geometry.DEPTH_SERIALIZATION_DEFAULT, false);
		} else if (value instanceof Color) {
			out.value("#"+Integer.toHexString(((Color)value).getRGB()).substring(2));
		} else if (value instanceof XY_DataSet) {
			xyAdapter.write(out, (XY_DataSet)value);
		} else if (value instanceof Geometry) {
			geomAdapter.write(out, (Geometry)value);
		} else if (value instanceof Feature) {
			featureAdapter.write(out, (Feature)value);
		} else if (value instanceof FeatureCollection) {
			featureCollectionAdapter.write(out, (FeatureCollection)value);
		} else if (value.getClass().isArray()) {
			Object[] array;
			if (value.getClass().getComponentType().isPrimitive()) {
				if (value instanceof double[])
					array = ArrayUtils.toObject((double[])value);
				else if (value instanceof double[])
					array = ArrayUtils.toObject((double[])value);
				else if (value instanceof float[])
					array = ArrayUtils.toObject((float[])value);
				else if (value instanceof int[])
					array = ArrayUtils.toObject((int[])value);
				else if (value instanceof long[])
					array = ArrayUtils.toObject((long[])value);
				else if (value instanceof short[])
					array = ArrayUtils.toObject((short[])value);
				else if (value instanceof byte[])
					array = ArrayUtils.toObject((byte[])value);
				else if (value instanceof boolean[])
					array = ArrayUtils.toObject((boolean[])value);
				else
					throw new IllegalStateException();
			} else {
				array = (Object[])value;
			}
			out.beginArray();
			for (Object subValue : array)
				propSerializeDefault(out, subValue);
			out.endArray();
		} else if (value instanceof Collection<?>) {
			out.beginArray();
			for (Object subValue : (Collection<?>)value)
				propSerializeDefault(out, subValue);
			out.endArray();
		} else if (value instanceof FeatureProperties) {
			propAdapter.write(out, (FeatureProperties)value);
		} else if (value instanceof Map<?, ?>) {
			// map that's not a FeatureProperties, serialize it directly
			out.beginObject();
			Map<?, ?> map = (Map<?, ?>)value;
			for (Object key : map.keySet()) {
				out.name(key.toString());
				propSerializeDefault(out, map.get(key));
			}
			out.endObject();
		} else {
			// TODO support custom adapters?
			out.value(value.toString());
		}
	}
	
	/**
	 * Attempts to deserialize the given value. Numbers will be returned as Numbers (either Long or Double),
	 * booleans as Boolean's, and Strings as Strings. Other object types will be skipped and null will be returned.
	 * 
	 * @param in
	 * @return
	 * @throws IOException 
	 */
	public static Object propDeserializeDefault(JsonReader in) throws IOException {
		JsonToken token = in.peek();
		if (token == JsonToken.NULL) {
			in.nextNull();
			return null;
		} else if (token == JsonToken.BOOLEAN) {
			return in.nextBoolean();
		} else if (token == JsonToken.NUMBER) {
			return parseNumber(in.nextString());
		} else if (token == JsonToken.STRING) {
			return in.nextString();
		} else if (token == JsonToken.BEGIN_ARRAY) {
			ArrayList<Object> values = new ArrayList<>();
			in.beginArray();
			while (in.hasNext())
				values.add(propDeserializeDefault(in));
			in.endArray();
			return values;
		} else if (token == JsonToken.BEGIN_OBJECT) {
			// see if it specifies a type first
			String startPath = in.getPath();
			
//			System.out.println("OBJECT at path "+startPath);
			
			in.beginObject();
			
//			System.out.println("Now inside: "+in.peek()+", "+in.getPath());
			
			if (in.hasNext()) {
				String name0 = in.nextName();
				
				if (name0.equals("type") && in.peek() == JsonToken.STRING) {
					String type = null;
					// we hit the (possibly" magic 'type' keyword
					try {
						type = in.nextString();
						// first check if it's a GeoJSON type; if so, load it as a GeoJSON object
						GeoJSON_Type geoType = null;
						try {
							geoType = GeoJSON_Type.valueOf(type);
						} catch (Exception e) {
							// not a GeoJSON type
						}
						if (geoType != null) {
							// it's GeoJSON object
							if (GeoJSON_Type.GEOM_TYPES.contains(geoType)) {
								// geometry
								Geometry ret = geomAdapter.innerReadAsType(in, geoType);
								in.endObject();
								return ret;
							} else if (geoType == GeoJSON_Type.Feature) {
								// feature
								Feature ret = featureAdapter.innerReadAsType(in, geoType);
								in.endObject();
								return ret;
							} else if (geoType == GeoJSON_Type.FeatureCollection) {
								// feature collection
								FeatureCollection ret = featureCollectionAdapter.innerReadAsType(in, geoType);
								in.endObject();
								return ret;
							} else {
								System.err.println("WARNING: couldn't deserialize custom FeatureProperties object with type: "
										+type);
							}
						} else {
							// it's not a GeoJSON object, see if it's a class name that we know how to deal with
							try {
								Class<?> typeClass = Class.forName(type);
								if (XY_DataSet.class.isAssignableFrom(typeClass)) {
									XY_DataSet ret = new XY_DataSet.XYAdapter().innerReadAsType(in, (Class<? extends XY_DataSet>)typeClass);
									in.endObject();
									return ret;
								}
								// TODO support generic adapters?
							} catch (Throwable t) {
								// not a class, which is fine
							}
							// ok no special cases found, just load it in as a FeatureProperties map
							try {
								FeatureProperties props = propAdapter.readAfterFirstNameAndValue(in, name0, type);
								if (props != null)
									return props;
							} catch (Throwable t) {
								System.err.println("WARNING: couldn't deserialize custom FeatureProperties object as a map: "
										+t.getMessage());
							}
						}
					} catch (Throwable t) {
						System.err.println("WARNING: couldn't deserialize custom FeatureProperties object with type: "
								+type+", exception: "+t.getMessage());
					}
				} else {
					// try to deserialize as a FeatureProperties
					
					try {
						FeatureProperties props = propAdapter.readAfterFirstName(in, name0);
						if (props != null)
							return props;
					} catch (Throwable t) {
						System.err.println("WARNING: couldn't deserialize custom FeatureProperties object as a map: "
								+t.getMessage());
					}
				}
			}
			skipUntilPastObject(in, startPath);
			return null;
		}
		in.skipValue();
		return null;
	}
	
	/**
	 * This will skip all values in the given reader until it reaches END_OBJECT for the given path.
	 * @param in
	 * @param startPath
	 * @throws IOException
	 */
	private static void skipUntilPastObject(JsonReader in, String startPath) throws IOException {
		// this is where it gets tricky. we have descended into the an object
		// and need to back the reader out
		final boolean D = false;
		if (D) System.out.println("Looking to back out to: "+startPath);
		while (true) {
			String path = in.getPath();
			if (D) System.out.println("Path: "+path+"\tequals? "+isSamePath(path, startPath));
			JsonToken peek = in.peek();
			if (isSamePath(path, startPath)) {
				// do strict equals for this test, otherwise we'll skip every other object in an array
				if (peek == JsonToken.BEGIN_OBJECT && path.equals(startPath)) {
					// phew, we haven't gone in yet. just skip over it
					if (D) System.out.println("DONE: hadn't yet descended into object, can skip");
					in.skipValue();
					break;
				} else {
					// we've fully backed out
					if (D) System.out.println("DONE: exiting with path="+path+" and peek="+peek);
					break;
				}
			}
			if (peek == JsonToken.END_DOCUMENT) {
				// we've gone too far, end with an error
				in.close();
				throw new IllegalStateException("Failed to skipUnilEndObject to "+startPath+", encountered END_DOCUMENT");
			}
//			System.out.println("Still in the thick of it at: "+path);
			if (peek == JsonToken.END_ARRAY) {
				if (D) System.out.println("\tending array");
				in.endArray();
			} else if (peek == JsonToken.END_OBJECT) {
				if (D) System.out.println("\tending object");
				in.endObject();
			} else {
				if (D) System.out.println("\tskipping "+peek);
				in.skipValue();
			}
		}
	}
	
	/**
	 * 
	 * @param testPath
	 * @param destPath
	 * @return true if testPath equals destPath, or testPath is another value in the same array
	 */
	private static boolean isSamePath(String testPath, String destPath) {
		if (testPath.equals(destPath))
			return true;
		if (testPath.endsWith("]") && destPath.endsWith("]")) {
			// special case for arrays, could be at the index in it, which should return true
			int destArrayBegin = destPath.lastIndexOf('[');
			Preconditions.checkState(destArrayBegin > 0);
			int testArrayBegin = destPath.lastIndexOf('[');
			Preconditions.checkState(testArrayBegin > 0);
			if (destArrayBegin == testArrayBegin) {
				// probable match
				String destPrefix = destPath.substring(0, destArrayBegin);
				String testPrefix = testPath.substring(0, testArrayBegin);
				if (destPrefix.equals(testPrefix))
					return true;
			}
		}
		return false;
	}
	
	static Number parseNumber(String numStr) {
		if (numStr.contains(".") || numStr.toLowerCase().contains("e"))
			return Double.parseDouble(numStr);
		else
			return Long.parseLong(numStr);
	}

}
