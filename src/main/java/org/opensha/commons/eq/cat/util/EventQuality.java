package org.opensha.commons.eq.cat.util;

import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.eq.cat.Catalog;

/**
 * Values for different earthquake event location qualities.
 * 
 * @author Peter Powers
 * @version $Id: EventQuality.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public enum EventQuality {

	/** No event quality value. */
	NONE(0),
	/** Event quality value A (xy &#177; 1km, z &#177; 2km) */
	A(10),
	/** Event quality value B (xy &#177; 2km, z &#177; 5km) */
	B(20),
	/** Event quality value C (xy &#177; 5km, z not restricted) */
	C(30),
	/** Event quality value D (xy &#62;&#177; 5km, z not restricted) */
	D(40);

	private static Map<Integer, EventQuality> idMap;
	private int id;

	private EventQuality(int id) {
		this.id = id;
	}

	static {
		idMap = new HashMap<Integer, EventQuality>();
		for (EventQuality mt : EventQuality.values()) {
			idMap.put(mt.id(), mt);
		}
	}

	/**
	 * Returns the <code>EventQuality</code> corresponding to the supplied
	 * <code>id</code> value, or <code>null</code> if no
	 * <code>EventQuality</code> with the supplied <code>id</code> exists.
	 * 
	 * @param id to look up
	 * @return the <code>EventQuality</code>
	 */
	public EventQuality typeForID(int id) {
		return idMap.get(id);
	}

	/**
	 * Returns the <code>id</code> value for this <code>EventQuality</code>. The
	 * <code>id</code> is used when storing (persisting) this value.
	 * 
	 * @return the <code>EventQuality</code> id
	 */
	public int id() {
		return id;
	}

	/**
	 * Convert a string to an <code>EventQuality</code>. If the string does not
	 * match any value, <code>EventQuality.NONE</code> is returned. This method
	 * is case insensitive.
	 * 
	 * @param s string to convert
	 * @return the event location quality value
	 */
	public static EventQuality parse(String s) {
		try {
			return valueOf(EventQuality.class, s.trim().toUpperCase());
		} catch (Exception e) {
			// NPE and IAE; do nothing
		}
		return NONE;
	}

}
