package org.opensha.commons.eq;

/**
 * Utility class for working with magnitudes.
 * 
 * @author Peter Powers
 * @version $Id:$
 * 
 */
public class MagUtils {

	/**
	 * Convert moment magnitude, <em>M</em><sub>W</sub>, to seismic moment,
	 * <em>M</em><sub>0</sub>.
	 * @param magnitude to convert
	 * @return the equivalent seismic moment in Newton-meters
	 */
	public static double magToMoment(double magnitude) {
		return (Math.pow(10, 1.5 * magnitude + 9.05));
	}

	/**
	 * Convert seismic moment, <em>M</em><sub>0</sub>, to moment magnitude,
	 * <em>M</em><sub>w</sub>.
	 * @param moment to convert (in Newton-meters)
	 * @return the equivalent moment magnitude
	 */
	public static double momentToMag(double moment) {
		return (Math.log10(moment) - 9.05) / 1.5;
	}

	/**
	 * Returns the Gutenberg Richter event rate for the supplied a- and b-values
	 * and magnitude.
	 * @param a value (log10 rate of M=0 events)
	 * @param b value
	 * @param M magnitude of interest
	 * @return the rate of magnitude <code>M</code> events
	 */
	public static double gr_rate(double a, double b, double M) {
		return Math.pow(10, a - b * M);
	}
	
	
}
