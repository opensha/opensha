package org.opensha.sha.imr.attenRelImpl.ngaw2;


/**
 * Default wrapper for ground motion prediction equation (GMPE) results.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class DefaultGroundMotion implements ScalarGroundMotion {

	private double mean;
	private double sigma;
	private double phi;
	private double tau;
	
	/**
	 * Create a new ground motion container for models that don't support phi and tau.
	 * 
	 * @param mean ground motion (in natural log units)
	 * @param sigma aleatory uncertainty
	 */
	public DefaultGroundMotion(double mean, double sigma) {
		this(mean, sigma, Double.NaN, Double.NaN);
	}
	
	/**
	 * Create a new ground motion container.
	 * 
	 * @param mean ground motion (in natural log units)
	 * @param sigma aleatory uncertainty
	 * @param phi intra-event standard deviation
	 * @param tau inter-event standard deviation
	 */
	public DefaultGroundMotion(double mean, double sigma, double phi, double tau) {
		this.mean = mean;
		this.sigma = sigma;
		this.phi = phi;
		this.tau = tau;
	}

	@Override public double mean() { return mean; }
	@Override public double stdDev() { return sigma; }

	@Override
	public double phi() {
		if (Double.isNaN(phi)) throw new UnsupportedOperationException("Phi not supported");
		return phi;
	}

	@Override
	public double tau() {
		if (Double.isNaN(tau)) throw new UnsupportedOperationException("Tau not supported");
		return tau;
	}

}
