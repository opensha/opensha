package org.opensha.sha.imr.attenRelImpl.ngaw2;

import org.apache.commons.math3.util.Precision;

/**
 * Add comments here
 * 
 * TODO add method(s) to get sets of periods by freq range
 * 
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum IMT {

	PGA,
	PGV,
	PGD,
	SA0P01,
	SA0P02,
	SA0P03,
	SA0P04,
	SA0P05,
	SA0P075,
	SA0P1,
	SA0P12,
	SA0P15,
	SA0P17,
	SA0P2,
	SA0P25,
	SA0P3,
	SA0P4,
	SA0P5,
	SA0P75,
	SA1P0,
	SA1P5,
	SA2P0,
	SA3P0,
	SA4P0,
	SA5P0,
	SA6P0,
	SA7P5,
	SA10P0;

	/**
	 * Returns the corresponding period or frequency for this {@code IMT} if it
	 * represents a spectral acceleration.
	 * @return the period for this {@code IMT} if it represents a spectral
	 *         acceleration, {@code null} otherwise
	 */
	public Double getPeriod() {
		if (name().startsWith("P")) return null;
		String valStr = name().replace("SA", "").replace("P", ".");
		return Double.parseDouble(valStr);
	}

	/**
	 * Returns the spectral acceleration {@code IMT} associated with the
	 * supplied period. Due to potential floating point precision problems, this
	 * method internally checks values to within a small tolerance.
	 * @param period for {@code IMT}
	 * @return an {@code IMT}, or {@code null} if no IMT exsists for the
	 *         supplied period
	 */
	public static IMT getSA(double period) {
		for (IMT imt : IMT.values()) {
			if (imt.name().startsWith("SA")) {
				double saPeriod = imt.getPeriod();
				if (Precision.equals(saPeriod, period, 0.000001)) return imt;
			}
		}
		return null;
	}

	/**
	 * Returns true if this IMT is some flavor of spectral acceleration.
	 * @return {@code true} if this is a spectral period, {@code false}
	 *         otherwise
	 */
	public boolean isSA() {
		return ordinal() > 2;
	}

	/**
	 * Parses the supplied {@code String} into an {@code IMT}.
	 * @param s {@code String} to parse
	 * @return an {@code IMT}, or {@code null} if supplied {@code String} is
	 *         invalid
	 */
	public static IMT parseIMT(String s) {
		s = s.trim().toUpperCase();
		if (s.equals("PGA") || s.equals("PGV") || s.equals("PGD")) {
			return IMT.valueOf(s);
		}
		try {
			double period = Double.parseDouble(s);
			return getSA(period);
		} catch (NumberFormatException nfe) {
			return null;
		}
	}
	
	public static void main(String[] args) {
		IMT imt = PGA;
		System.out.println(imt);
		IMT currentIMT = imt;
		System.out.println(currentIMT);
		imt = SA1P0;
		System.out.println(currentIMT);
	}

}
