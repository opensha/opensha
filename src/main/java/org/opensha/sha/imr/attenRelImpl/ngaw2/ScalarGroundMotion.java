package org.opensha.sha.imr.attenRelImpl.ngaw2;

/**
 * Wrapper class for ground motion prediction equation (GMPE) results.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface ScalarGroundMotion {

	/**
	 * Returns the median peak ground motion.
	 * @return the mean
	 */
	public double mean();
	
	/**
	 * Returns the standard deviation for a hazard calculation.
	 * @return the standard deviation
	 */
	public double stdDev();
	
	/**
	 * Intra-event standard deviation if model supported.
	 * @return Intra-event standard deviation
	 * @throws UnsupportedOperationException if not supported
	 */
	public double phi();
	
	/**
	 * Inter-event standard deviation if model supported.
	 * @return Inter-event standard deviation
	 * @throws UnsupportedOperationException if not supported
	 */
	public double tau();
	
}
