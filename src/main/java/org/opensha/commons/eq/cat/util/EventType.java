package org.opensha.commons.eq.cat.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Values for different earthquake event types.
 * 
 * @author Peter Powers
 * @version $Id: EventType.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public enum EventType {

	/** Unknown event type value. */
	UNKNOWN(0, "uk"),
	/** Local event value. */
	LOCAL(10, "le"),
	/** Regional event value. */
	REGIONAL(20, "re"),
	/** Teleseismic event value. */
	TELESEISM(30, "ts"),
	/** Quarry blast event value. */
	QUARRY(40, "qb"),
	/** Sonic boom event value. */
	SONIC(50, "sn"),
	/** Nuclear blast event value. */
	NUCLEAR(60, "nt");

	private static Map<Integer, EventType> idMap;
	private static Map<String, EventType> abbrMap;
	private int id;
	private String abbr;

	private EventType(int id, String abbr) {
		this.id = id;
		this.abbr = abbr;
	}

	static {
		idMap = new HashMap<Integer, EventType>();
		abbrMap = new HashMap<String, EventType>();
		for (EventType et : EventType.values()) {
			idMap.put(et.id(), et);
			abbrMap.put(et.abbr(), et);
		}
	}

	/**
	 * Returns the <code>EventType</code> corresponding to the supplied
	 * <code>id</code> value, or <code>null</code> if no <code>EventType</code>
	 * with the supplied <code>id</code> exists.
	 * 
	 * @param id to look up
	 * @return the <code>EventType</code>
	 */
	public EventType typeForID(int id) {
		return idMap.get(id);
	}

	/**
	 * Returns the <code>id</code> value for this <code>EventType</code>. The
	 * <code>id</code> is used when storing (persisting) this value.
	 * 
	 * @return the <code>EventType</code> id
	 */
	public int id() {
		return id;
	}

	/**
	 * Returns the abbreviation commonly used to identify this
	 * <code>EventType</code> (in earthquake catalogs).
	 * 
	 * @return the abbreviation for this <code>EventType</code>
	 */
	public String abbr() {
		return abbr;
	}

	/**
	 * Convert a string to an <code>EventType</code>. If the string does not
	 * match any value, <code>EventType.UNKNOWN</code> is returned. This method
	 * is case insensitive.
	 * 
	 * @param s string to convert
	 * @return the <code>EventType</code> value
	 */
	public static EventType parse(String s) {
		try {
			EventType type = abbrMap.get(s.trim().toLowerCase());
			return (type == null) ? UNKNOWN : type;
		} catch (Exception e) {
			// NPE; do nothing
		}
		return UNKNOWN;
	}

}
