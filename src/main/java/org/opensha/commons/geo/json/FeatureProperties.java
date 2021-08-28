package org.opensha.commons.geo.json;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.geo.Location;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
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
	 * Tries to return the value of the given parameter as a boolean. If the parameter value is a Boolean, that boolean will be
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
		if (val instanceof String) {
			// try to parse string
			try {
				String str = (String)val;
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
	 * Tries to return the value of the given parameter as a double array. See {@link #getNumberArray(String, Number[])}.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public double[] getDoubleArray(String name, double[] defaultValue) {
		Number[] numbers = getNumberArray(name, null);
		if (numbers == null)
			return null;
		double[] ret = new double[numbers.length];
		for (int i=0; i<numbers.length; i++)
			ret[i] = numbers[i].doubleValue();
		return ret;
	}
	
	/**
	 * Tries to return the value of the given parameter as an int array. See {@link #getNumberArray(String, Number[])}.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public int[] getIntArray(String name, int[] defaultValue) {
		Number[] numbers = getNumberArray(name, null);
		if (numbers == null)
			return null;
		int[] ret = new int[numbers.length];
		for (int i=0; i<numbers.length; i++)
			ret[i] = numbers[i].intValue();
		return ret;
	}
	
	/**
	 * Tries to return the value of the given parameter as a long array. See {@link #getNumberArray(String, Number[])}.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public long[] getLongArray(String name, int[] defaultValue) {
		Number[] numbers = getNumberArray(name, null);
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
	 * Otherwise, the supplied default value will be returned.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public Number[] getNumberArray(String name, Number[] defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		List<Number> numbers = new ArrayList<>();
		if (val instanceof List<?>) {
			for (Object subVal : (List<?>)val) {
				Number number = asNumber(subVal, name, null);
				if (number == null)
					return defaultValue;
				numbers.add(number);
			}
		} else if (val.getClass().isArray()) {
			for (Object subVal : (Object[])val) {
				Number number = asNumber(subVal, name, null);
				if (number == null)
					return defaultValue;
				numbers.add(number);
			}
		}
		return numbers.toArray(new Number[0]);
	}
	
	public Location getLocation(String name, Location defaultValue) {
		Object val = get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof Location)
			return (Location)val;
		// try loading it as a location
		double[] array = getDoubleArray(name, null);
		if (array == null || array.length < 2 || array.length > 3)
			return null;
		double lon = array[0];
		double lat = array[1];
		double depth = array.length == 3 ? Geometry.DEPTH_SERIALIZATION_DEFAULT.fromGeoJSON(array[2]) : 0d;
		return new Location(lat, lon, depth);
	}
	
	public static final String STROKE_COLOR_PROP = "stroke";
	public static final String STROKE_WIDTH_PROP = "stroke-width";
	public static final String STROKE_OPACITY_PROP = "stroke-opacity";
	public static final String FILL_COLOR_PROP = "fill";
	public static final String FILL_OPACITY_PROP = "fill-opacity";
	
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
			return (E)val;
		} catch (ClassCastException e) {
			System.err.println("Feature property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
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
				Object value = deserialize(in, name);
				
				properties.put(name, value);
			}
			
			in.endObject();
			return properties;
		}
		
	}
	
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
		} else {
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
		}
		in.skipValue();
		return null;
	}
	
	static Number parseNumber(String numStr) {
		if (numStr.contains(".") || numStr.toLowerCase().contains("e"))
			return Double.parseDouble(numStr);
		else
			return Long.parseLong(numStr);
	}

}
