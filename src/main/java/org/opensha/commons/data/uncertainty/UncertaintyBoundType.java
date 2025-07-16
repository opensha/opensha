package org.opensha.commons.data.uncertainty;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.google.common.base.Preconditions;

/**
 * Types of bounds for a {@link BoundedUncertainty}, with methods for estimating standard deviations from bounds,
 * and bounds from a mean and standard deviation.
 * 
 * @author kevin
 *
 */
public enum UncertaintyBoundType {
	/**
	 * plus/minus 2-sigma bounds: usually (but not necessarily) roughly the 95% confidence interval
	 */
	TWO_SIGMA("± 2σ", 2d),
	/**
	 * 95% confidence bounds: roughly plus/minus 2-sigma if normally distributed
	 */
	CONF_95("95% bounds", new NormalDistribution(0d, 1d).inverseCumulativeProbability((1+0.95)/2d)),
	/**
	 * plus/minus 1-sigma bounds: usually (but not necessarily) roughly the 68% confidence interval
	 */
	ONE_SIGMA("± σ", 1d),
	/**
	 * 68% confidence bounds: roughly plus/minus 1-sigma if normally distributed
	 */
	CONF_68("68% bounds", new NormalDistribution(0d, 1d).inverseCumulativeProbability((1+0.68)/2d)),
	/**
	 * plus/minus 0.5-sigma bounds: this is how we treated average slip uncertainties in UCERF3,
	 * but that was probably a poor assumption and will likely never be used
	 */
	HALF_SIGMA("± σ/2", 0.5);
	
	private String label;
	/**
	 * z-score of a standard normal for the upper bound of this uncertainty
	 */
	public final double z;
	
	private UncertaintyBoundType(String label, double z) {
		this.label = label;
		Preconditions.checkState(z > 0, "z must be positive");
		this.z = z;
	}
	
	/**
	 * Estimate a standard deviation from best estimate, lower and upper bounds of this type
	 * @param lowerBound
	 * @param upperBound
	 * 
	 * @return estimated standard deviation
	 */
	public double estimateStdDev(double lowerBound, double upperBound) {
		Preconditions.checkState(upperBound >= lowerBound);
		return (upperBound - lowerBound)/(2d*z);
	}
	
	/**
	 * Estimates bounds from a best estimate and standard deviation
	 * 
	 * @param bestEstimate
	 * @param stdDev
	 * @return
	 */
	public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
		return new BoundedUncertainty(this, bestEstimate - z*stdDev, bestEstimate + z*stdDev, stdDev);
	}

	@Override
	public String toString() {
		return label;
	}
	
	public static void main(String[] args) {
		for (UncertaintyBoundType type : values())
			System.out.println(type.label+"; z="+(float)type.z);
	}
	
}