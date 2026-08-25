package org.opensha.commons.data.sampling;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;

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

	@Override
	public DiscretizedDiscrepancyKernel getDiscretizedKernel(int preferredBins) {
		if (preferredBins < 2)
			throw new IllegalArgumentException("Continuous discretization requires at least 2 bins, have " + preferredBins);
		return new ContinuousDiscretizedKernel(preferredBins);
	}

	private static final class ContinuousDiscretizedKernel implements DiscretizedDiscrepancyKernel {
		private final int bins;
		private final double[] representatives;
		private final double[][] values;
		private final double[] targetMeans;
		private final double targetGrandMean;
		private final double targetDiagonalMean;

		ContinuousDiscretizedKernel(int bins) {
			this.bins = bins;
			this.representatives = new double[bins];
			for (int state=0; state<bins; state++)
				representatives[state] = (state+0.5d)/bins;
			this.values = new double[bins][bins];
			this.targetMeans = new double[bins];
			double diagonalMean = 0d;
			for (int state1=0; state1<bins; state1++) {
				for (int state2=0; state2<bins; state2++) {
					values[state1][state2] = KERNEL.value(representatives[state1], representatives[state2]);
					targetMeans[state1] += values[state1][state2]/bins;
				}
				diagonalMean += values[state1][state1]/bins;
			}
			double grandMean = 0d;
			for (double targetMean : targetMeans)
				grandMean += targetMean/bins;
			this.targetGrandMean = grandMean;
			this.targetDiagonalMean = diagonalMean;
		}

		@Override
		public int stateCount() {
			return bins;
		}

		@Override
		public int state(double value) {
			validateCoordinate(value);
			return Math.min(bins-1, (int)(value*bins));
		}

		@Override
		public double representativeValue(int state) {
			return representatives[state];
		}

		@Override
		public double value(int state1, int state2) {
			return values[state1][state2];
		}

		@Override
		public double targetMean(int state) {
			return targetMeans[state];
		}

		@Override
		public double targetGrandMean() {
			return targetGrandMean;
		}

		@Override
		public double targetDiagonalMean() {
			return targetDiagonalMean;
		}
	}

	private static void validateCoordinate(double value) {
		if (!Double.isFinite(value) || value < 0d || value >= 1d)
			throw new IllegalArgumentException("Coordinate must be finite and in [0,1), have " + value);
	}
}
