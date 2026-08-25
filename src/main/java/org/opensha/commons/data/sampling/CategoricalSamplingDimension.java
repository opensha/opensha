package org.opensha.commons.data.sampling;

import java.util.Arrays;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;

/**
 * A categorical interpretation of the unit interval. Categories occupy contiguous intervals in index order and are
 * scored with an equality kernel, so the score imposes no distance or ordering between categories.
 */
public final class CategoricalSamplingDimension implements SamplingDimension {

	private static final double BOUNDARY_TOLERANCE = 1e-12;

	private final double[] probabilities;
	private final double[] upperBounds;
	private final double targetGrandMean;
	private final DiscrepancyKernel kernel = new DiscrepancyKernel() {
		@Override
		public double value(double value1, double value2) {
			// Categorical similarity deliberately ignores interval positions and distances: two values are similar only
			// when they map to the same category.
			return categoryIndex(value1) == categoryIndex(value2) ? 1d : 0d;
		}

		@Override
		public double targetMean(double value) {
			// If the observed value is in category c, a random ideal value matches it with probability p_c.
			return probabilities[categoryIndex(value)];
		}

		@Override
		public double targetGrandMean() {
			// Two independent ideal values match when both choose category c. Summing p_c^2 over categories gives the
			// target's overall chance agreement; it was cached while the category probabilities were constructed.
			return targetGrandMean;
		}

		@Override
		public double targetDiagonalMean() {
			// Every value necessarily shares its own category, so all diagonal equality-kernel entries are 1.
			return 1d;
		}
	};
	private final DiscretizedDiscrepancyKernel discretizedKernel = new DiscretizedDiscrepancyKernel() {
		@Override
		public int stateCount() {
			return categoryCount();
		}

		@Override
		public int state(double value) {
			return categoryIndex(value);
		}

		@Override
		public double representativeValue(int state) {
			return 0.5d*(categoryLowerBound(state)+categoryUpperBound(state));
		}

		@Override
		public double value(int state1, int state2) {
			return state1 == state2 ? 1d : 0d;
		}

		@Override
		public double targetMean(int state) {
			return probabilities[state];
		}

		@Override
		public double targetGrandMean() {
			return targetGrandMean;
		}

		@Override
		public double targetDiagonalMean() {
			return 1d;
		}
	};

	private CategoricalSamplingDimension(double[] probabilities) {
		this.probabilities = probabilities;
		this.upperBounds = new double[probabilities.length];
		double sum = 0d;
		double grandMean = 0d;
		for (int i=0; i<probabilities.length; i++) {
			sum += probabilities[i];
			upperBounds[i] = sum;
			grandMean += probabilities[i]*probabilities[i];
		}
		// Avoid a floating-point gap immediately below 1 after normalized weights are accumulated.
		upperBounds[upperBounds.length-1] = 1d;
		this.targetGrandMean = grandMean;
	}

	/**
	 * Builds a categorical dimension from relative category weights. Weights are normalized to sum to one.
	 *
	 * @param weights finite, positive category weights
	 * @return categorical dimension
	 */
	public static CategoricalSamplingDimension forWeights(double... weights) {
		if (weights == null)
			throw new NullPointerException("Weights cannot be null");
		if (weights.length < 2)
			throw new IllegalArgumentException("At least two categories are required");
		double sum = 0d;
		for (int i=0; i<weights.length; i++) {
			double weight = weights[i];
			if (!Double.isFinite(weight) || weight <= 0d)
				throw new IllegalArgumentException("Weight " + i + " must be finite and positive, have " + weight);
			sum += weight;
		}
		if (!Double.isFinite(sum) || sum <= 0d)
			throw new IllegalArgumentException("Weight sum must be finite and positive, have " + sum);
		double[] probabilities = weights.clone();
		for (int i=0; i<probabilities.length; i++)
			probabilities[i] /= sum;
		return new CategoricalSamplingDimension(probabilities);
	}

	/**
	 * Builds a categorical dimension from cumulative upper interval boundaries. The final boundary must equal 1 within
	 * floating-point tolerance. For example, {@code [0.2, 0.5, 1]} defines probabilities {@code [0.2, 0.3, 0.5]}.
	 *
	 * @param upperBounds strictly increasing category upper bounds
	 * @return categorical dimension
	 */
	public static CategoricalSamplingDimension forUpperBounds(double... upperBounds) {
		if (upperBounds == null)
			throw new NullPointerException("Upper bounds cannot be null");
		if (upperBounds.length < 2)
			throw new IllegalArgumentException("At least two categories are required");
		double[] probabilities = new double[upperBounds.length];
		double previous = 0d;
		for (int i=0; i<upperBounds.length; i++) {
			double upper = upperBounds[i];
			if (!Double.isFinite(upper) || upper <= previous || upper > 1d+BOUNDARY_TOLERANCE)
				throw new IllegalArgumentException("Upper bound " + i + " must be finite, strictly increasing, and <= 1; have "
						+ upper);
			probabilities[i] = upper-previous;
			previous = upper;
		}
		if (Math.abs(previous-1d) > BOUNDARY_TOLERANCE)
			throw new IllegalArgumentException("Final upper bound must equal 1, have " + previous);
		// Force an exact total of one when the supplied final boundary differed only by roundoff.
		probabilities[probabilities.length-1] += 1d-previous;
		return new CategoricalSamplingDimension(probabilities);
	}

	/** @return number of categories */
	public int categoryCount() {
		return probabilities.length;
	}

	/**
	 * Maps a coordinate to its category using {@code [lower, upper)} intervals.
	 *
	 * @param value coordinate in {@code [0,1)}
	 * @return category index
	 */
	public int categoryIndex(double value) {
		validateCoordinate(value);
		int low = 0;
		int high = upperBounds.length-1;
		while (low < high) {
			int middle = (low+high) >>> 1;
			if (value < upperBounds[middle])
				high = middle;
			else
				low = middle+1;
		}
		return low;
	}

	/** @return normalized probability of the category */
	public double categoryProbability(int category) {
		return probabilities[category];
	}

	/** @return inclusive lower interval boundary */
	public double categoryLowerBound(int category) {
		if (category < 0 || category >= categoryCount())
			throw new IndexOutOfBoundsException("Category index out of range: " + category);
		return category == 0 ? 0d : upperBounds[category-1];
	}

	/** @return exclusive upper interval boundary */
	public double categoryUpperBound(int category) {
		return upperBounds[category];
	}

	/** @return copy of normalized category probabilities */
	public double[] getProbabilities() {
		return probabilities.clone();
	}

	/** @return copy of cumulative category upper bounds */
	public double[] getUpperBounds() {
		return upperBounds.clone();
	}

	@Override
	public DiscrepancyKernel getDiscrepancyKernel() {
		return kernel;
	}

	@Override
	public DiscretizedDiscrepancyKernel getDiscretizedKernel(int preferredBins) {
		if (preferredBins < 1)
			throw new IllegalArgumentException("Preferred bin count must be positive, have " + preferredBins);
		return discretizedKernel;
	}

	private static void validateCoordinate(double value) {
		if (!Double.isFinite(value) || value < 0d || value >= 1d)
			throw new IllegalArgumentException("Coordinate must be finite and in [0,1), have " + value);
	}

	@Override
	public String toString() {
		return "CategoricalSamplingDimension" + Arrays.toString(probabilities);
	}
}
