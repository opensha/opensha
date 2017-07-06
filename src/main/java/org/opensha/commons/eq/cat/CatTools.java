package org.opensha.commons.eq.cat;

import static org.opensha.commons.eq.cat.util.DataType.*;

import java.lang.reflect.Array;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.commons.eq.cat.util.DataType;
import org.opensha.commons.util.DataUtils;

/**
 * Catalog utilities.
 * 
 * @author Peter Powers
 * @version $Id: CatTools.java 8912 2012-04-20 17:29:53Z pmpowers $
 */
public class CatTools {

	/** Minimum earthquake magnitude value (-2) used for range checking. */
	public static final double MAG_MIN = -2.0;

	/** Maximum earthquake magnitude value (10) used for range checking. */
	public static final double MAG_MAX = 10.0;

	/**
	 * Verifies that a set of magnitude values fall within range of
	 * <code>MAG_MIN</code> and <code>MAG_MAX</code> (inclusive).
	 * 
	 * @param mags magnitudes to validate
	 * @throws IllegalArgumentException if a data value is out of range
	 */
	public final static void validateMags(double[] mags) {
		DataUtils.validate(MAG_MIN, MAG_MAX, mags);
	}

	/**
	 * Verifies that a magnitude value falls within range of
	 * <code>MAG_MIN</code> and <code>MAG_MAX</code> (inclusive).
	 * 
	 * @param mag magnitude to validate
	 * @throws IllegalArgumentException if data value is out of range
	 */
	public final static void validateMag(double mag) {
		DataUtils.validate(MAG_MIN, MAG_MAX, mag);
	}

	/**
	 * Returns a JSON string containing data arrays for the following data
	 * [TIME, LONGITUDE, LATITUDE, DEPTH, MAGNITUDE]
	 * 
	 * @param catalog to process
	 * @return a JSON formatted <code>String</code>
	 * @throws IllegalArgumentException if <code>catalog</code> does not contain
	 *         the required output data types
	 */
	public final static String toJSON(Catalog catalog) {
		Set<DataType> types = EnumSet.of(TIME, LONGITUDE, LATITUDE, DEPTH, MAGNITUDE);
		StringBuilder json = new StringBuilder("{");
		for (DataType type : types) {
			json.append("\"" + type.toString().toLowerCase() + "s\":");
			json.append(buildArray(catalog.getData(type)));
			json.append((type==MAGNITUDE) ? "}" : ",");
		}
		
		return json.toString();
	}
	
	// builds comma delimited (no trailing space) array string
	private static String buildArray(Object array) {
		String delim = ",";
		StringBuilder sb = new StringBuilder("[");
		int len = Array.getLength(array);
		for (int i=0; i<len; i++) {
			sb.append(Array.get(array, i));
			if (i != len-1) sb.append(delim);
		}
		sb.append("]");
		return sb.toString();
	}
	
}
