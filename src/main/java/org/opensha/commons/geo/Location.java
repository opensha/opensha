/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.commons.geo;

import java.io.IOException;
import java.io.Serializable;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A <code>Location</code> represents a point with reference to the earth's
 * ellipsoid. It is expressed in terms of latitude, longitude, and depth. As in
 * seismology, the convention adopted in OpenSHA is for depth to be
 * positive-down, always. All utility methods in this package assume this to be
 * the case.<br/>
 * <br/>
 * For computational convenience and speed, latitude and longitude values are converted
 * and stored internally in radians as well as degrees. Special
 * <code>get***Rad()</code> methods are provided to access this native format. <br/>
 * <br/>
 * <code>Location</code> instances are immutable.
 * 
 * @author Peter Powers
 * @author Sid Hellman
 * @author Steven W. Rock
 * @version $Id: Location.java 8034 2011-07-07 15:35:54Z pmpowers $
 */
@JsonAdapter(Location.Adapter.class)
public class Location implements 
		Serializable, XMLSaveable, Cloneable, Comparable<Location> {

	private static final long serialVersionUID = 2L;

	public final static String XML_METADATA_NAME = "Location";
	public final static String XML_METADATA_LONGITUDE = "Longitude";
	public final static String XML_METADATA_LATITUDE = "Latitude";
	public final static String XML_METADATA_DEPTH = "Depth";
	
	private final static boolean FORCE_BACKWARDS_COMPATIBLE = false;

	/**
	 * Latitude of this <code>Location</code> in decimal degrees
	 */
	public final double lat;
	/**
	 * Longitude of this <code>Location</code> in decimal degrees
	 */
	public final double lon;
	final double latRad;
	final double lonRad;
	/**
	 * depth of this <code>Location</code> in km (positive-down)
	 */
	public final double depth;
	
	/**
	 * Constructs a new <code>Location</code> with the supplied latitude and
	 * longitude and sets the depth to 0.
	 * 
	 * @param lat latitude in decimal degrees to set
	 * @param lon longitude in decimal degrees to set
	 * @throws IllegalArgumentException if any supplied values are out of range
	 * @see GeoTools
	 */
	public Location(double lat, double lon) {
		this(lat, lon, 0);
	}

	/**
	 * Constructs a new <code>Location</code> with the supplied latitude,
	 * longitude, and depth values.
	 * 
	 * @param lat latitude in decimal degrees to set
	 * @param lon longitude in decimal degrees to set
	 * @param depth in km to set (positive down)
	 * @throws IllegalArgumentException if any supplied values are out of range
	 * @see GeoTools
	 */
	public Location(double lat, double lon, double depth) {
		GeoTools.validateLat(lat);
		GeoTools.validateLon(lon);
		GeoTools.validateDepth(depth);
		if (FORCE_BACKWARDS_COMPATIBLE) {
			latRad = lat * GeoTools.TO_RAD;
			lonRad = lon * GeoTools.TO_RAD;
			this.lat = latRad * GeoTools.TO_DEG;
			this.lon = lonRad * GeoTools.TO_DEG;
		} else {
			this.lat = lat;
			this.lon = lon;
			this.latRad = Math.toRadians(lat);
			this.lonRad = Math.toRadians(lon);
		}
		this.depth = depth;
	}
	
	/**
	 * Creates a backwards compatible <code>Location</code> where getLatitude/getLongitude will
	 * return the same values as the prior OpenSHA implementation where values were only stored
	 * in radians, and converted back to degrees when requested. Note that returned latitude/longitude
	 * values here will not always exactly match the passed in lat/lon values (they are converted
	 * to radians and then back).
	 * 
	 * @param lat
	 * @param lon
	 * @param depth
	 * @return
	 */
	public static Location backwardsCompatible(double lat, double lon, double depth) {
		double latRad = lat * GeoTools.TO_RAD;
		double lonRad = lon * GeoTools.TO_RAD;
		double latDeg = latRad * GeoTools.TO_DEG;
		double lonDeg = lonRad * GeoTools.TO_DEG;
		return new Location(latDeg, lonDeg, latRad, lonRad, depth);
	}

	// internal for clone use()
	private Location(double latDeg, double lonDeg, double latRad, double lonRad, double depth) {
		super();
		this.lat = latDeg;
		this.lon = lonDeg;
		this.latRad = latRad;
		this.lonRad = lonRad;
		this.depth = depth;
	}

	/**
	 * Returns the depth of this <code>Location</code>.
	 * 
	 * @return the <code>Location</code> depth in km
	 */
	public final double getDepth() {
		return depth;
	}

	/**
	 * Returns the latitude of this <code>Location</code>.
	 * 
	 * @return the <code>Location</code> latitude in decimal degrees
	 */
	public final double getLatitude() {
		return lat;
	}

	/**
	 * Returns the longitude of this <code>Location</code>.
	 * 
	 * @return the <code>Location</code> longitude in decimal degrees
	 */
	public final double getLongitude() {
		return lon;
	}

	/**
	 * Returns the latitude of this <code>Location</code>.
	 * 
	 * @return the <code>Location</code> latitude in radians
	 */
	public double getLatRad() {
		return latRad;
	}

	/**
	 * Returns the longitude of this <code>Location</code>.
	 * 
	 * @return the <code>Location</code> longitude in radians
	 */
	public double getLonRad() {
		return lonRad;
	}

	/**
	 * Returns this <code>Location</code> formatted as a "lon,lat,depth"
	 * <code>String</code> for use in KML documents. This differs from
	 * {@link Location#toString()} in that the output lat-lon order are
	 * reversed.
	 * 
	 * @return the location as a <code>String</code> for use with KML markup
	 */
	public String toKML() {
		// TODO check that reversed lat-lon order would be ok
		// for toString() and retire this method
		StringBuffer b = new StringBuffer();
		b.append(getLongitude());
		b.append(",");
		b.append(getLatitude());
		b.append(",");
		b.append(getDepth());
		return b.toString();
	}

	@Override
	public String toString() {
		return String.format("%.5f, %.5f, %.5f", getLatitude(), getLongitude(),
			getDepth());
	}
	
	@Override
	public Location clone() {
		Location clone = new Location(lat, lon, latRad, lonRad, depth);
		return clone;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Location)) return false;
		Location loc = (Location) obj;
		if (lat != loc.lat) return false;
		if (lon != loc.lon) return false;
		if (depth != loc.depth) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		// edit did same fix as for equals, now uses getters
		long latHash = Double.doubleToLongBits(lat);
		long lonHash = Double.doubleToLongBits(lon + 1000);
		long depHash = Double.doubleToLongBits(depth + 2000);
		long v = latHash + lonHash + depHash;
		return (int) (v^(v>>>32));
	}

	/**
	 * Compares this <code>Location</code> to another and sorts first by 
	 * latitude, then by longitude. When sorting a list of randomized but 
	 * evenly spaced grid of <code>Location</code>s, the resultant ordering 
	 * will be left to right across rows of uniform latitude, ascending to 
	 * the leftmost next higher latitude at the end of each row (left-to-right,
	 * bottom-to-top).
	 * 
	 * @param loc <code>Location</code> to compare <code>this</code> to
	 * @return a negative integer, zero, or a positive integer if this 
	 *         <code>Location</code> is less than, equal to, or greater than 
	 *         the specified <code>Location</code>.
	 */
	@Override
	public int compareTo(Location loc) {
		double d = (lat == loc.lat) ? lon - loc.lon : lat - loc.lat;
		return (d != 0) ? (d < 0) ? -1 : 1 : 0;
	}
	
	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(Location.XML_METADATA_NAME);
		xml.addAttribute(Location.XML_METADATA_LATITUDE, getLatitude() + "");
		xml.addAttribute(Location.XML_METADATA_LONGITUDE, getLongitude() + "");
		xml.addAttribute(Location.XML_METADATA_DEPTH, getDepth() + "");
		return root;
	}

	public static Location fromXMLMetadata(Element root) {
		double lat = Double.parseDouble(
				root.attribute(Location.XML_METADATA_LATITUDE).getValue());
		double lon = Double.parseDouble(
				root.attribute(Location.XML_METADATA_LONGITUDE).getValue());
		double depth = Double.parseDouble(
				root.attribute(Location.XML_METADATA_DEPTH).getValue());
		return new Location(lat, lon, depth);
	}
	
	public static class Adapter extends TypeAdapter<Location> {

		@Override
		public void write(JsonWriter out, Location value) throws IOException {
			out.beginArray();
			
			out.value(value.lat).value(value.lon);
			if (value.depth != 0d)
				out.value(value.depth);
			
			out.endArray();
		}

		@Override
		public Location read(JsonReader in) throws IOException {
			int ind = 0;
			double lat = Double.NaN;
			double lon = Double.NaN;
			double depth = 0d;
			
			in.beginArray();
			
			while (in.hasNext()) {
				double val = in.nextDouble();
				if (ind == 0)
					lat = val;
				else if (ind == 1)
					lon = val;
				else if (ind == 2)
					depth = val;
				else
					throw new IllegalStateException("Location JSON must have 2 or 3 values");
				ind++;
			}
			
			in.endArray();
			return new Location(lat, lon, depth);
		}
		
	}

}
