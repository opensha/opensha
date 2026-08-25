package org.opensha.commons.data.sampling;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;

/**
 * Standard continuous unit-interval dimension.
 */
public final class ContinuousSamplingDimension implements SamplingDimension {

	public static final ContinuousSamplingDimension INSTANCE = new ContinuousSamplingDimension();

	private static final DiscrepancyKernel KERNEL = new DiscrepancyKernel() {
		@Override
		public double value(double value1, double value2) {
			// Represent u by the CDF-indicator feature I(u <= t) as threshold t sweeps [0,1]. Two values' features are
			// both on from max(u,v) through 1, so their overlap (similarity) has length 1-max(u,v).
			return 1d-Math.max(value1, value2);
		}

		@Override
		public double targetMean(double value) {
			// Average 1-max(value,Y) over uniform Y. Equivalently, at each threshold t >= value the observed feature is
			// on and a uniform target feature is on with probability t, giving integral_value^1 t dt = (1-value^2)/2.
			return 0.5d*(1d-value*value);
		}

		@Override
		public double targetGrandMean() {
			// At threshold t, two independent uniform values are both <= t with probability t^2. Integrating t^2 from
			// 0 to 1 gives 1/3: the mean similarity between two independent ideal values.
			return 1d/3d;
		}

		@Override
		public double targetDiagonalMean() {
			// A value's similarity with itself is 1-U. Its uniform mean is integral_0^1 (1-u) du = 1/2. This exceeds
			// the independent-pair mean of 1/3 and produces the finite-sample random baseline.
			return 0.5d;
		}
	};

	private ContinuousSamplingDimension() {}

	@Override
	public DiscrepancyKernel getDiscrepancyKernel() {
		return KERNEL;
	}
}
