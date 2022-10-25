package org.opensha.commons.data.uncertainty;

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
	TWO_SIGMA("± 2σ") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return (upperBound - lowerBound)/4d;
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-2*stdDev, bestEstimate+2*stdDev, stdDev);
		}
	},
	/**
	 * 95% confidence bounds: roughly plus/minus 2-sigma if normally distributed
	 */
	CONF_95("95% bounds") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return (upperBound - lowerBound)/4d;
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-2*stdDev, bestEstimate+2*stdDev, stdDev);
		}
	},
	/**
	 * plus/minus 1-sigma bounds: usually (but not necessarily) roughly the 68% confidence interval
	 */
	ONE_SIGMA("± σ") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return (upperBound - lowerBound)/2d;
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-stdDev, bestEstimate+stdDev, stdDev);
		}
	},
	/**
	 * 68% confidence bounds: roughly plus/minus 1-sigma if normally distributed
	 */
	CONF_68("68% bounds") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return (upperBound - lowerBound)/2d;
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-stdDev, bestEstimate+stdDev, stdDev);
		}
	},
	/**
	 * plus/minus 0.5-sigma bounds: this is how we treated average slip uncertainties in UCERF3,
	 * but that was probably a poor assumption and will likely never be used
	 */
	HALF_SIGMA("± σ/2") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return upperBound - lowerBound;
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-0.5*stdDev, bestEstimate+0.5*stdDev, stdDev);
		}
	},
	/**
	 * sigma-squared bounds
	 */
	SIGMA_SQUARED("± σ²") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return Math.sqrt(ONE_SIGMA.estimateStdDev(bestEstimate, lowerBound, upperBound));
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-stdDev*stdDev, bestEstimate+stdDev*stdDev, stdDev);
		}
	},
	/**
	 * sigma-squared bounds
	 */
	SQRT_SIGMA("± √σ") {
		@Override
		public double estimateStdDev(double bestEstimate, double lowerBound, double upperBound) {
			return Math.pow(ONE_SIGMA.estimateStdDev(bestEstimate, lowerBound, upperBound), 2);
		}

		@Override
		public BoundedUncertainty estimate(double bestEstimate, double stdDev) {
			return new BoundedUncertainty(this, bestEstimate-Math.sqrt(stdDev), bestEstimate+Math.sqrt(stdDev), stdDev);
		}
	};
	
	private String label;

	private UncertaintyBoundType(String label) {
		this.label = label;
	}
	
	/**
	 * Estimate a standard deviation from best estimate, lower and upper bounds of this type
	 * 
	 * @param bestEstimate
	 * @param lowerBound
	 * @param upperBound
	 * @return estimated standard deviation
	 */
	public abstract double estimateStdDev(double bestEstimate, double lowerBound, double upperBound);
	
	/**
	 * Estimates bounds from a best estimate and standard deviation
	 * 
	 * @param bestEstimate
	 * @param stdDev
	 * @return
	 */
	public abstract BoundedUncertainty estimate(double bestEstimate, double stdDev);

	@Override
	public String toString() {
		return label;
	}
}