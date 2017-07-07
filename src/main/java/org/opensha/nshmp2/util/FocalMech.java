package org.opensha.nshmp2.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/**
 * Identifier for different focal mechanism types.
 * 
 * TODO this could move to commons
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

	private FocalMech(int id, double dip, double rake) {
		this.id = id;
		this.dip = dip;
		this.rake = rake;
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
	
	@Override
	public String toString() {
		return WordUtils.capitalizeFully(StringUtils.replaceChars(this.name(),
			'_', '-'),'-');
	}

}
