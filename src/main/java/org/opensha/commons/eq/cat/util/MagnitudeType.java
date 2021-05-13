package org.opensha.commons.eq.cat.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Values for different earthquake magnitude types.
 *
 * @author Peter Powers
 * @version $Id: MagnitudeType.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public enum MagnitudeType {

	/** No magnitude type. */
	NONE(0),
	/** Local (Wood-Anderson) magnitude type. */
	LOCAL(10),
	/** Surface-wave magnitude type. */
	SURFACE(20),
	/** Moment magnitude type. */
	MOMENT(30),
	/** Energy magnitude type. */
	ENERGY(40),
	/** Body-wave magnitude type. */
	BODY(50),
	/** Coda amplitude magnitude type. */
	CODA_AMPLITUDE(60),
	/** Coda duration magnitude type. */
	CODA_DURATION(70),
	/** Helicorder (short-period Benioff) magnitude type. */
	HELICORDER(80),
	/** Maximum amplitude magnitude type. */
	MAX_AMPLITUDE(90),
	/** Average of coda duration and maximum amplitude magnitude types. */
	AVERAGE_MD_MX(100),
	/** Lg phase magnitude type. */
	LG_PHASE(110);

	private static Map<Integer, MagnitudeType> idMap;
	private int id;

	private MagnitudeType(int id) {
		this.id = id;
	}

	static {
		idMap = new HashMap<Integer, MagnitudeType>();
		for (MagnitudeType mt : MagnitudeType.values()) {
			idMap.put(mt.id(), mt);
		}
	}

	/**
	 * Returns the <code>MagnitudeType</code> corresponding to the supplied
	 * <code>id</code> value, or <code>null</code> if no
	 * <code>MagnitudeType</code> with the supplied <code>id</code> exists.
	 *
	 * @param id to look up
	 * @return the <code>MagnitudeType</code>
	 */
	public MagnitudeType typeForID(int id) {
		return idMap.get(id);
	}

	/**
	 * Returns the <code>id</code> value for this <code>MagnitudeType</code>.
	 * The <code>id</code> is used when storing (persisting) this value.
	 *
	 * @return the <code>MagnitudeType</code> id
	 */
	public int id() {
		return id;
	}

	/**
	 * Parses string values found in NCEDC catalogs to internal values for
	 * <code>MagnitudeType</code>. Method expects ['Md' 'Mx' 'ML*' 'Mw' 'Mavg'
	 * 'Ms' 'Mb' 'Me' 'Mc' 'Mlg']; otherwise returns
	 * <code>MagnitudeType.NONE</code>.
	 *
	 * @param s string to convert
	 * @return the corresponding magnitude type value
	 */
	public static MagnitudeType parseNCEDC(String s) {
		if (s.equals("Md")) return CODA_DURATION;
		if (s.equals("Mx")) return MAX_AMPLITUDE;
		if (s.startsWith("ML")) return LOCAL;
		if (s.equals("Mw")) return MOMENT;
		if (s.equals("Mavg")) return AVERAGE_MD_MX;
		if (s.equals("Ms")) return SURFACE;
		if (s.equals("Mb")) return BODY;
		if (s.equals("Me")) return ENERGY;
		if (s.equals("Mc")) return CODA_AMPLITUDE;
		if (s.equals("Mlg")) return LG_PHASE;
		return NONE;
	}

	/**
	 * Parses string values found in NCEDC catalogs to internal values for
	 * <code>MagnitudeType</code>. Method expects ['l' 'h' 's' 'b' 'w' 'e' 'c'
	 * 'd']; otherwise returns <code>MagnitudeType.NONE</code>.
	 *
	 * @param s string to convert
	 * @return the corresponding magnitude type value
	 */
	public static MagnitudeType parseSCEDC(String s) {
		if (s.equals("l")) return LOCAL;
		if (s.equals("h")) return HELICORDER;
		if (s.equals("s")) return SURFACE;
		if (s.equals("b")) return BODY;
		if (s.equals("w")) return MOMENT;
		if (s.equals("e")) return ENERGY;
		if (s.equals("c")) return CODA_AMPLITUDE;
		if (s.equals("d")) return CODA_DURATION;
		return NONE;
	}

}
