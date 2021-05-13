package org.opensha.commons.eq.cat.util;

import static org.opensha.commons.eq.cat.CatTools.MAG_MAX;
import static org.opensha.commons.eq.cat.CatTools.MAG_MIN;
import static org.opensha.commons.geo.GeoTools.DEPTH_MAX;
import static org.opensha.commons.geo.GeoTools.DEPTH_MIN;
import static org.opensha.commons.geo.GeoTools.LAT_MAX;
import static org.opensha.commons.geo.GeoTools.LAT_MIN;
import static org.opensha.commons.geo.GeoTools.LON_MAX;
import static org.opensha.commons.geo.GeoTools.LON_MIN;

import org.opensha.commons.eq.cat.CatTools;
import org.opensha.commons.geo.GeoTools;

/**
 * Values for different catalog data types.
 * 
 * @author Peter Powers
 * @version $Id: DataType.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public enum DataType {

	/** Event ID data identifier. */
	EVENT_ID("Event ID", int[].class),

	/** Event type data identifier. */
	EVENT_TYPE("Event Type", int[].class),

	/** Event time data identifier. */
	TIME("Time", long[].class),

	/** Event longitude data identifier. */
	LONGITUDE("Longitude", double[].class),

	/** Event latitude data identifier. */
	LATITUDE("Latitude", double[].class),

	/** Event depth data identifier. */
	DEPTH("Depth", double[].class),

	/** Event location quality data identifier. */
	QUALITY("Event Quality", int[].class),

	/** Event horizontal error data identifier. */
	XY_ERROR("Horizontal Error", double[].class),

	/** Event vertical error data identifier. */
	Z_ERROR("Vertical Error", double[].class),

	/** Event magnitude data identifier. */
	MAGNITUDE("Magnitude", double[].class),

	/** Event magnitude type data identifier. */
	MAGNITUDE_TYPE("Magnitude Type", int[].class),

	/** Event fault plane strike data identifier. */
	STRIKE("Strike", double[].class),

	/** Event fault plane dip data identifier. */
	DIP("Dip", double[].class),

	/** Event fault plane rake data identifier. */
	RAKE("Rake", double[].class),

	/** Distance of event from assiociated fault data identifier. */
	FAULT_DISTANCE("Distance to Fault", double[].class);

	private String name;
	private Class<?> clazz;

	private DataType(String name, Class<?> clazz) {
		this.name = name;
		this.clazz = clazz;
	}

	/**
	 * Returns the class
	 * 
	 * @return the array class that will hold data for this type.
	 */
	public Class<?> clazz() {
		return clazz;
	}

	/**
	 * Overriden to return a label friendly <code>String</code>.
	 * 
	 * @return a <code>String</code> value for labels.
	 */
	@Override
	public String toString() {
		return name;
	}

	/**
	 * Returns the minimum possible value for this <code>DataType</code>.
	 * Method is only applicable for the following double-valued
	 * <code>DataTypes</code>: [LONGITUDE, LATITUDE, DEPTH, MAGNITUDE]. Requests
	 * on any other <code>DataType</code> return <code>null</code>.
	 * 
	 * @return the lower limit value
	 * @see GeoTools for values
	 * @see CatTools for values
	 */
	public Double minLimit() {
		switch (this) {
			case LATITUDE:
				return LAT_MIN;
			case LONGITUDE:
				return LON_MIN;
			case DEPTH:
				return DEPTH_MIN;
			case MAGNITUDE:
				return MAG_MIN;
			default:
				return null;
		}
	}

	/**
	 * Returns the maximum possible value for this <code>DataType</code>.
	 * Method is only applicable for the following double-valued
	 * <code>DataTypes</code>: [LONGITUDE, LATITUDE, DEPTH, MAGNITUDE]. Requests
	 * on any other <code>DataType</code> return <code>null</code>.
	 * 
	 * @return the upper limit value
	 * @see GeoTools for values
	 * @see CatTools for values
	 */
	public Double maxLimit() {
		switch (this) {
			case LATITUDE:
				return LAT_MAX;
			case LONGITUDE:
				return LON_MAX;
			case DEPTH:
				return DEPTH_MAX;
			case MAGNITUDE:
				return MAG_MAX;
			default:
				return null;
		}
	}

}
