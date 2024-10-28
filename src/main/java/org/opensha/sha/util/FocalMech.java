package org.opensha.sha.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.opensha.sha.earthquake.FocalMechanism;

/**
 * Identifier for different focal mechanism types.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum FocalMech {

	/** A strike-slip focal mechanism. */
	STRIKE_SLIP(1, 90.0, 0.0),

	/** A reverse slip (or thrust) focal mechanism. */
	REVERSE(2, 50.0, 90.0),

	/** A normal slip focal mechanism. */
	NORMAL(3, 50.0, -90.0);

	private int id;
	private double dip;
	private double rake;
	
	public final FocalMechanism.Unmodifiable mechanism;

	private FocalMech(int id, double dip, double rake) {
		this.id = id;
		this.dip = dip;
		this.rake = rake;
		this.mechanism = new FocalMechanism.Unmodifiable(Double.NaN, dip, rake);
	}

	/**
	 * Returns the focal mechanism associated with the supplied NSHMP id value.
	 * @param id to lookup
	 * @return the associated <code>FocalMech</code>
	 */
	public static FocalMech typeForID(int id) {
		for (FocalMech fm : FocalMech.values()) {
			if (fm.id == id) return fm;
		}
		return null;
	}

	/**
	 * Returns a 'standard' dip value for this mechanism.
	 * @return the dip
	 */
	public double dip() {
		return dip;
	}

	/**
	 * Returns a 'standard' rake value for this mechanism.
	 * @return the rake
	 */
	public double rake() {
		return rake;
	}
	
	/**
	 * Returns the {@link FocalMech} enum constant for the given {@link FocalMechanism}. It returns null if the
	 * supplied mechanism does not exactly match both the dip and the rake of one of the constants.
	 * 
	 * @param mechanism
	 * @return
	 */
	public static FocalMech forFocalMechanism(FocalMechanism mechanism) {
		if (mechanism instanceof FocalMechanism.Unmodifiable) {
			// shortcut: see if it's one of our enum constants
			for (FocalMech mech : values())
				if (mechanism == mech.mechanism)
					return mech;
		}
		// slower: check .equals
		for (FocalMech mech : values())
			if (mech.dip == mechanism.getDip() && mech.rake == mechanism.getRake())
				return mech;
		throw null;
	}
	
	@Override
	public String toString() {
		return WordUtils.capitalizeFully(StringUtils.replaceChars(this.name(),
			'_', '-'),'-');
	}

}
