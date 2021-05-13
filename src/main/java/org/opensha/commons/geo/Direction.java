package org.opensha.commons.geo;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/**
 * Enum identifies basic geographic directions.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum Direction {

	/** North */
	NORTH,
	/** Northeast */
	NORTHEAST,
	/** East */
	EAST,
	/** Southeast */
	SOUTHEAST,
	/** South */
	SOUTH,
	/** Southwest */
	SOUTHWEST,
	/** West */
	WEST,
	/** Northwest */
	NORTHWEST;

	@Override
	public String toString() {
		return WordUtils.capitalizeFully(this.name());
	}

	/**
	 * Returns the numeric bearing (0&deg;-360&deg;) associated with this
	 * <code>Direction</code> (e.g. NORTHEAST = 45&deg;).
	 * @return the bearing
	 */
	public double bearing() {
		return ordinal() * 45;
	}

	/**
	 * Returns the <code>Direction</code> opposite this <code>Direction</code>
	 * @return the opposite <code>Direction</code>
	 */
	public Direction opposite() {
		return valueOf((ordinal() + 4) % 8);
	}

	/**
	 * Returns the <code>Direction</code> encountered moving clockwise from this
	 * <code>Direction</code>.
	 * @return the next (moving clockwise) <code>Direction</code>
	 */
	public Direction next() {
		return valueOf((ordinal() + 1) % 8);
	}

	/**
	 * Returns the <code>Direction</code> encountered moving anti-clockwise from
	 * this <code>Direction</code>.
	 * @return the previous (moving anti-clockwise) <code>Direction</code>
	 */
	public Direction prev() {
		return valueOf((ordinal() + 7) % 8);
	}

	/**
	 * Returns whether a move in this <code>Direction</code> will result in a
	 * positive, negative or no change to the latitude of some geographic
	 * location.
	 * @return the sign of a latitude change corresponding to a move in this
	 *         <code>Direction</code>
	 */
	public int signLatMove() {
		return dLat[ordinal()];
	}

	/**
	 * Returns whether a move in this <code>Direction</code> will result in a
	 * positive, negative or no change to the longitude of some geographic
	 * location.
	 * @return the sign of a longitude change corresponding to a move in this
	 *         <code>Direction</code>
	 */
	public int signLonMove() {
		return dLon[ordinal()];
	}

	private static int[] dLat = { 1, 1, 0, -1, -1, -1, 0, 1 };
	private static int[] dLon = { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static Direction[] values = values();

	// TODO determine whether each call to values() returns the same array,
	// I doubt it as it would be mutable

	private Direction valueOf(int ordinal) {
		return values[ordinal];
	}

	public static void main(String[] args) {
		for (Direction d : values()) {
			Object[] dat = new Object[] { d.name(), d, d.ordinal(), d
				.opposite(), d.prev(), d.next() };
			System.out.println(StringUtils.join(dat, " "));
		}
	}

}
