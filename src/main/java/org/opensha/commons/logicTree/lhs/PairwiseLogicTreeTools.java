package org.opensha.commons.logicTree.lhs;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractCombinedSamplingLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;

import com.google.common.base.Preconditions;

/**
 * Scoring machinery for pairwise-balance optimization of a sampled logic tree.
 * <p>
 * <b>Goal.</b> Given a set of sampled logic-tree branches, we want each <i>pair</i> of levels to look as though the two
 * levels were sampled independently of one another. If level A and level B are correlated (e.g. option a1 almost always
 * co-occurs with option b1), the sample covers the joint space poorly. The optimizer (see the {@code *Iteration}
 * classes) hill-climbs toward independence by repeatedly swapping a single level's value between two branches; such a
 * swap never changes any level's marginal distribution, it only changes which options co-occur, which is exactly the
 * correlation structure we want to fix.
 * <p>
 * <b>Score.</b> Every pair of varying levels gets a {@link PairCriterion} measuring "how far from independent is this
 * pair?" Each criterion divides its raw score by the score expected from a completely random pairing, so that every
 * pair has an expected value of exactly {@code 1} under random shuffling, regardless of level cardinality or type
 * (categorical vs. continuous). A normalized score below 1 is better-than-random balance; summing normalized scores
 * across pairs is therefore meaningful. This closed-form random baseline avoids ever simulating random shuffles.
 * <p>
 * <b>Two scoring formulations, one underlying quantity.</b> Categorical/categorical pairs are scored with a contingency
 * table (sum of squared deviations from the independence table). All other pairs (fractile, or combined levels
 * containing fractiles) are scored with a kernel formulation known in the literature as the Hilbert-Schmidt
 * Independence Criterion (HSIC): represent each level by an N x N matrix of pairwise sample similarities, "center" both
 * matrices, and take their entrywise-product sum. These two formulations are not merely analogous: with the equality
 * (same-option-or-not) kernel, HSIC reduces <i>exactly</i> to the contingency residual score. The contingency path is
 * retained purely because its swap update is cheaper, not because it measures anything different.
 *
 * @see PairwiseScorer
 * @see PairCriterion
 */
final class PairwiseLogicTreeTools {
	// Continuous (fractile) levels are quantized to this many equal-width bins on [0,1]. Quantization lets us cache the
	// centered kernel as a small FRACTILE_BINS x FRACTILE_BINS lookup table instead of an N x N matrix, and lets us
	// reduce all sums over samples to sums over bins weighted by bin counts. 100 bins is fine enough that quantization
	// error is negligible relative to sampling noise.
	static final int FRACTILE_BINS = 100;

	private PairwiseLogicTreeTools() {}

	/**
	 * Per-level representation of the sampled assignments, plus the operations the optimizer needs: testing whether two
	 * samples are equivalent on this level, and swapping two samples' values. Concrete subclasses hold the compact
	 * representation (an array of category indices, fractile bins, etc.); the full N x N similarity matrix is never
	 * materialized.
	 */
	static abstract class LevelData {
		final int levelIndex;
		final int numSamples;

		LevelData(int levelIndex, int numSamples) {
			this.levelIndex = levelIndex;
			this.numSamples = numSamples;
		}

		/**
		 * Checks if two samples are identical; for quantized continuous levels, this checks that two samples fall within
		 * the same bin. Used by the optimizer to skip swaps that would change nothing.
		 * @param sample1
		 * @param sample2
		 * @return true if the samples are equal, false otherwise
		 */
		abstract boolean matches(int sample1, int sample2);

		/**
		 * Swaps the values between the two samples (branches). Called only after a swap has been accepted; criterion
		 * state must already have been updated (it is computed from the pre-swap assignments).
		 *
		 * @param sample1
		 * @param sample2
		 */
		abstract void swap(int sample1, int sample2);
	}

	/**
	 * Level data that can be scored via a centered similarity kernel. A "kernel" here is just a similarity function:
	 * given two samples it returns a number saying how alike they are on this level. Collecting those numbers for every
	 * sample pair conceptually forms an N x N matrix K, with K[a][b] = similarity of samples a and b. We never build
	 * that matrix; instead each sample maps to a small integer "kernel state" (its category, or its fractile bin), and
	 * the centered kernel between two states is available by lookup.
	 * <p>
	 * "Centered" means the matrix has been double-centered: from each entry we subtract its row mean and column mean and
	 * add back the grand mean (average of all entries). After centering, every row and column sums to zero. A positive
	 * centered entry means the two samples are <i>more</i> similar than the baseline set by each sample's overall
	 * tendency to be similar to everything; a negative entry means less similar than that baseline. Centering removes
	 * similarity that is an artifact of marginal prevalence (a popular option makes many pairs match by chance), leaving
	 * only genuine above/below-expected similarity. This is the standard "HKH" double-centering used in classical
	 * multidimensional scaling.
	 */
	static abstract class KernelLevelData extends LevelData {

		KernelLevelData(int levelIndex, int numSamples) {
			super(levelIndex, numSamples);
		}

		/** @return number of distinct states used by the cached kernel representation */
		abstract int numKernelStates();

		/** @return cached kernel state for the given sample (category index, fractile bin, or a sentinel) */
		abstract int kernelState(int sample);

		/** @return baseline-adjusted (double-centered) similarity between two cached kernel states */
		abstract double centeredKernelForStates(int state1, int state2);

		/**
		 * Summarizes the amount of variation among samples in this level; used for the random-pairing baseline. The
		 * trace is the sum over samples of each sample's centered self-similarity (the diagonal of the centered kernel).
		 * It behaves like a "total spread" of the level in kernel space: a level whose samples are all identical has
		 * near-zero trace, while a level with many distinct, spread-out values has a large trace. The expected HSIC of
		 * two independent levels is proportional to the product of their traces.
		 *
		 * @return sum of each sample's baseline-adjusted similarity with itself
		 */
		abstract double kernelTrace();
	}

	/**
	 * Level data for categorical data, i.e., samples from a set of fixed choices. The similarity kernel is the equality
	 * kernel: raw similarity is 1 if two samples chose the same option and 0 otherwise. Note the equality kernel is
	 * blind to any ordering among options; it only sees same-or-different. (This is exactly why continuous levels get a
	 * different kernel: with equality, bin 50 and bin 51 would be as "different" as bin 50 and bin 99.)
	 * <p>
	 * The centered equality kernel takes a small set of values that depend on the marginal fractions p_i of each option:
	 * <ul>
	 * <li>Two samples sharing option i: {@code 1 - 2*p_i + grandMean}. Sharing a <i>rare</i> option yields a large
	 * positive value (a surprising agreement); sharing a <i>common</i> option yields a smaller one.</li>
	 * <li>Two samples with different options i, j: {@code -p_i - p_j + grandMean}. Usually negative, exactly zero on the
	 * knife-edge {@code p_i + p_j == grandMean}, and (perhaps surprisingly) strictly positive when both options are rare
	 * while probability mass concentrates on some other, dominant option -- two "loner" samples failing to match is
	 * less surprising-in-the-dissimilar-direction than their very low baseline predicts.</li>
	 * </ul>
	 * where {@code grandMean = sum_i p_i^2} is the probability that two randomly chosen samples match. When all options
	 * are equally likely these collapse to just two values (one for match, one for mismatch); the per-option form here
	 * is the exact general case for unbalanced marginals.
	 */
	static final class CategoricalLevelData extends KernelLevelData {
		final int numCategories;
		final int[] marginalCounts;
		final double[] marginalFractions;
		final double kernelGrandMean;
		final double kernelTrace;
		final int[] values;

		CategoricalLevelData(int levelIndex, int numCategories, int[] values) {
			super(levelIndex, values.length);
			Preconditions.checkArgument(numCategories > 1);
			this.numCategories = numCategories;
			this.values = values;
			this.marginalCounts = new int[numCategories];
			for (int value : values) {
				Preconditions.checkArgument(value >= 0 && value < numCategories,
						"Bad category index %s for level %s with %s categories", value, levelIndex, numCategories);
				marginalCounts[value]++;
			}
			this.marginalFractions = new double[numCategories];
			// Row mean of the equality kernel for a sample in option i is just p_i (the fraction of samples that share
			// its option). The grand mean is the average of the full equality-kernel matrix, equivalently the
			// probability that two sampled values match, sum_i p_i^2. Double-centering subtracts the row means for both
			// samples, so this global mean is added back once.
			double grandMean = 0d;
			for (int i=0; i<numCategories; i++) {
				marginalFractions[i] = (double)marginalCounts[i] / numSamples;
				grandMean += marginalFractions[i] * marginalFractions[i];
			}
			this.kernelGrandMean = grandMean;
			// Trace = sum over samples of the centered self-similarity 1 - 2*p_i + grandMean (self-similarity raw value
			// is 1 for the equality kernel).
			double trace = 0d;
			for (int value : values)
				trace += 1d - 2d*marginalFractions[value] + grandMean;
			this.kernelTrace = trace;
		}

		@Override
		boolean matches(int sample1, int sample2) {
			return values[sample1] == values[sample2];
		}

		@Override
		void swap(int sample1, int sample2) {
			int value = values[sample1];
			values[sample1] = values[sample2];
			values[sample2] = value;
		}

		@Override
		int numKernelStates() {
			return numCategories;
		}

		@Override
		int kernelState(int sample) {
			return values[sample];
		}

		@Override
		double centeredKernelForStates(int state1, int state2) {
			// Equality kernel, double-centered: (1 if same option else 0) - p_i - p_j + grandMean.
			return (state1 == state2 ? 1d : 0d) - marginalFractions[state1]
					- marginalFractions[state2] + kernelGrandMean;
		}

		@Override
		double kernelTrace() {
			return kernelTrace;
		}
	}

	/**
	 * Level data for samples from continuous distributions, i.e., levels that implement {@link FractileSamplingLevel}.
	 * Each sample is a fractile in [0,1]. Unlike the equality kernel, this kernel respects ordering and distance: two
	 * nearby fractiles are highly similar, distant ones are not.
	 * <p>
	 * <b>The kernel.</b> Represent a fractile u by its CDF-indicator "feature" phi_u(t) = 1 if u &lt;= t else 0, for a
	 * sweeping threshold t in [0,1]. The raw similarity of u and v is the overlap (inner product) of their features,
	 * i.e. the length of the range of t on which both features are 1:
	 * <pre>   K(u,v) = integral_0^1 I(u&lt;=t) I(v&lt;=t) dt = 1 - max(u,v).</pre>
	 * Intuitively, two low fractiles have features that are "on" almost everywhere and overlap a lot (high raw
	 * similarity); two high fractiles overlap only near t=1 (low raw similarity). That location bias in the raw scale is
	 * removed by centering, after which "identical" reads the same regardless of where on [0,1] the pair sits.
	 * <p>
	 * Because two samples in the same bin have identical features, the centered kernel depends only on the two bins, so
	 * we cache it as a FRACTILE_BINS x FRACTILE_BINS table. This is the standard CDF/L2 kernel connected to distance
	 * covariance; scoring two such kernels is HSIC with distance-induced kernels.
	 */
	static final class FractileLevelData extends KernelLevelData {
		final short[] fractileBins;
		final float[][] centeredKernels;
		final double kernelTrace;

		FractileLevelData(int levelIndex, double[] fractiles) {
			super(levelIndex, fractiles.length);
			this.fractileBins = new short[numSamples];
			int[] binCounts = new int[FRACTILE_BINS];
			for (int i=0; i<numSamples; i++) {
				double fractile = fractiles[i];
				Preconditions.checkArgument(Double.isFinite(fractile) && fractile >= 0d && fractile <= 1d,
						"Fractile must be finite and in [0,1], have " + fractile + " for level " + levelIndex);
				fractileBins[i] = checkedFractileBin(fractile);
				binCounts[fractileBins[i]]++;
			}

			// Cache the double-centered kernel (H*K*H) by bin. Row/grand means are computed empirically from the actual
			// bin counts rather than assuming a uniform distribution of fractiles: the row mean for bin1 is the average
			// raw similarity of a bin1 sample to all samples, i.e. sum over bin2 of binCounts[bin2]*rawKernel(bin1,bin2)
			// / numSamples. (For reference, in the idealized uniform-and-infinite case these would be the closed forms
			// rowMean(u) = (1 - u^2)/2 and grandMean = 1/3; the code uses the empirical values so it is exact for the
			// actual, non-uniform, finite sample.) Swaps change sample-to-bin assignments, not these cached values.
			double[] rowMeans = new double[FRACTILE_BINS];
			double grandMean = 0d;
			for (int bin1=0; bin1<FRACTILE_BINS; bin1++) {
				for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
					rowMeans[bin1] += binCounts[bin2]*rawFractileKernel(bin1, bin2)/numSamples;
				grandMean += binCounts[bin1]*rowMeans[bin1]/numSamples;
			}
			centeredKernels = new float[FRACTILE_BINS][FRACTILE_BINS];
			for (int bin1=0; bin1<FRACTILE_BINS; bin1++)
				for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
					centeredKernels[bin1][bin2] = (float)(rawFractileKernel(bin1, bin2)
							-rowMeans[bin1]-rowMeans[bin2]+grandMean);

			// Trace = sum of centered self-similarities; the fractile diagonal is not constant (it depends on where each
			// sample sits), unlike the raw equality kernel whose diagonal is all ones.
			double trace = 0d;
			for (int i=0; i<numSamples; i++)
				trace += centeredKernels[fractileBins[i]][fractileBins[i]];
			this.kernelTrace = trace;
		}

		@Override
		boolean matches(int sample1, int sample2) {
			return fractileBins[sample1] == fractileBins[sample2];
		}

		@Override
		void swap(int sample1, int sample2) {
			short bin = fractileBins[sample1];
			fractileBins[sample1] = fractileBins[sample2];
			fractileBins[sample2] = bin;
		}

		@Override
		int numKernelStates() {
			return FRACTILE_BINS;
		}

		@Override
		int kernelState(int sample) {
			return fractileBins[sample];
		}

		@Override
		double centeredKernelForStates(int state1, int state2) {
			return centeredKernels[state1][state2];
		}

		@Override
		double kernelTrace() {
			return kernelTrace;
		}
	}

	/**
	 * Level data for {@link AbstractCombinedSamplingLevel} instances that might contain both categorical and continuous
	 * data. A combined level samples in two stages: a <i>selector</i> chooses which sub-level applies, then that
	 * sub-level supplies a value. Two samples are equivalent on a combined level only if they chose the same sub-level
	 * <i>and</i> the same node within it.
	 * <p>
	 * This class only holds the assignment state (selector values and within-sub-level node indexes) and knows how to
	 * swap it consistently across all its views. The actual scoring is done by {@link CompositePairCriterion}, which
	 * decomposes a combined level into its {@link #selector} and per-sub-level {@link #conditionals}.
	 */
	static final class CombinedLevelData extends LevelData {
		final CategoricalLevelData selector;
		final List<ConditionalLevelData> conditionals;
		final double[] conditionalWeights;
		final int[] selectorValues;
		final int[] nodeValues;

		CombinedLevelData(int levelIndex, CategoricalLevelData selector,
				List<ConditionalLevelData> conditionals, double[] conditionalWeights, int[] nodeValues) {
			super(levelIndex, selector.numSamples);
			this.selector = selector;
			this.conditionals = conditionals;
			this.conditionalWeights = conditionalWeights;
			this.selectorValues = selector.values;
			this.nodeValues = nodeValues;
		}

		@Override
		boolean matches(int sample1, int sample2) {
			return selectorValues[sample1] == selectorValues[sample2]
					&& nodeValues[sample1] == nodeValues[sample2];
		}

		@Override
		void swap(int sample1, int sample2) {
			// Every component (selector, each conditional, and the node-index array) is another view of the same
			// combined-level assignment, so all must be swapped together to stay consistent.
			selector.swap(sample1, sample2);
			for (ConditionalLevelData conditional : conditionals)
				conditional.swap(sample1, sample2);
			int value = nodeValues[sample1];
			nodeValues[sample1] = nodeValues[sample2];
			nodeValues[sample2] = value;
		}
	}

	/**
	 * Used by {@link CombinedLevelData} to track data for one sub-level (choice) within a combined level. A conditional
	 * asks: <i>among the samples that selected this sub-level</i>, are the within-sub-level values balanced against
	 * other levels? It can wrap either a categorical or a fractile sub-level.
	 * <p>
	 * <b>Local centering + zero-padding.</b> Only a subset of samples selected this sub-level. Its kernel is meaningful
	 * only on that subset, but the scoring machinery expects a global N x N kernel. We therefore center the kernel
	 * <i>among the selecting samples only</i> (all means divide by {@code selectedCount}, not {@code numSamples}), and
	 * embed the result into the global matrix as a block, with every entry touching a non-selecting sample set to zero.
	 * A zero centered entry means "exactly baseline similarity -- no information," which is precisely correct: a sample
	 * that did not select this sub-level tells us nothing about the balance of its values. Centering <i>before</i>
	 * padding keeps the baseline honest (it is relative to the other selectors, not diluted by unrelated samples), and
	 * critically it preserves the property that all global rows and columns sum to zero, which the trace-based random
	 * baseline relies on. Non-selecting samples are given a sentinel kernel state (one past the real states) whose
	 * centered kernel with anything is zero.
	 */
	static final class ConditionalLevelData extends KernelLevelData {
		final boolean fractile;
		final boolean[] selected;
		final int[] categories;
		final short[] fractileBins;
		final double[] kernelStateRowMeans;
		final double kernelGrandMean;
		final float[][] centeredFractileKernels;
		final double kernelTrace;

		ConditionalLevelData(int levelIndex, boolean[] selected, int[] categories, int numCategories) {
			this(levelIndex, selected, categories, null, numCategories);
		}

		ConditionalLevelData(int levelIndex, boolean[] selected, double[] fractiles) {
			this(levelIndex, selected, null, fractiles, -1);
		}

		private ConditionalLevelData(int levelIndex, boolean[] selected, int[] categories, double[] fractiles,
				int numCategories) {
			super(levelIndex, selected.length);
			this.fractile = fractiles != null;
			this.selected = selected;
			this.categories = categories;
			this.fractileBins = this.fractile ? new short[numSamples] : null;
			int selectedCount = 0;
			for (boolean value : selected)
				if (value)
					selectedCount++;
			Preconditions.checkArgument(selectedCount > 1);

			// Center only among samples that selected this sublevel. Unselected samples are represented by zero
			// rows/columns when this conditional kernel is embedded globally. All means below therefore divide by
			// selectedCount.
			int numActiveStates = this.fractile ? FRACTILE_BINS : numCategories;
			kernelStateRowMeans = new double[numActiveStates];
			double grandMean = 0d;
			if (this.fractile) {
				int[] binCounts = new int[FRACTILE_BINS];
				for (int i=0; i<numSamples; i++)
					if (selected[i]) {
						double value = fractiles[i];
						Preconditions.checkArgument(Double.isFinite(value) && value >= 0d && value <= 1d);
						fractileBins[i] = checkedFractileBin(value);
						binCounts[fractileBins[i]]++;
					}
				// Row mean of a bin, averaged over the selecting samples only.
				for (int bin1=0; bin1<FRACTILE_BINS; bin1++)
					for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
						kernelStateRowMeans[bin1] += binCounts[bin2]
								*rawFractileKernel(bin1, bin2)/selectedCount;
				for (int i=0; i<numSamples; i++) {
					if (!selected[i])
						continue;
					grandMean += kernelStateRowMeans[fractileBins[i]];
				}
			} else {
				// For the equality kernel restricted to selectors, the row mean of category c is just its within-subset
				// fraction (count of selectors in c divided by selectedCount).
				int[] counts = new int[numCategories];
				for (int i=0; i<numSamples; i++)
					if (selected[i])
						counts[categories[i]]++;
				for (int category=0; category<numCategories; category++)
					kernelStateRowMeans[category] = (double)counts[category]/selectedCount;
				for (int i=0; i<numSamples; i++)
					if (selected[i])
						grandMean += kernelStateRowMeans[categories[i]];
			}
			this.kernelGrandMean = grandMean/selectedCount;
			if (this.fractile) {
				centeredFractileKernels = new float[FRACTILE_BINS][FRACTILE_BINS];
				for (int bin1=0; bin1<FRACTILE_BINS; bin1++)
					for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
						centeredFractileKernels[bin1][bin2] = (float)(rawFractileKernel(bin1, bin2)
								-kernelStateRowMeans[bin1]-kernelStateRowMeans[bin2]+kernelGrandMean);
			} else {
				centeredFractileKernels = null;
			}

			double trace = 0d;
			for (int i=0; i<numSamples; i++) {
				int state = kernelState(i);
				trace += centeredKernelForStates(state, state);
			}
			this.kernelTrace = trace;
		}

		@Override
		boolean matches(int sample1, int sample2) {
			// Two samples match on this conditional if they agree on selection status, and (when both selected) on the
			// within-sub-level value. Two non-selectors are considered matching (both are "not applicable" here).
			if (selected[sample1] != selected[sample2])
				return false;
			if (!selected[sample1])
				return true;
			return fractile ? fractileBins[sample1] == fractileBins[sample2]
					: categories[sample1] == categories[sample2];
		}

		@Override
		void swap(int sample1, int sample2) {
			boolean selectedValue = selected[sample1];
			selected[sample1] = selected[sample2];
			selected[sample2] = selectedValue;
			if (fractile) {
				short bin = fractileBins[sample1];
				fractileBins[sample1] = fractileBins[sample2];
				fractileBins[sample2] = bin;
			} else {
				int value = categories[sample1];
				categories[sample1] = categories[sample2];
				categories[sample2] = value;
			}
		}

		@Override
		int numKernelStates() {
			// The real states plus one sentinel state for "did not select this sub-level".
			return kernelStateRowMeans.length+1;
		}

		@Override
		int kernelState(int sample) {
			// Sentinel state (== kernelStateRowMeans.length) for non-selecting samples; they contribute zero rows and
			// columns to the embedded kernel.
			if (!selected[sample])
				return kernelStateRowMeans.length;
			return fractile ? fractileBins[sample] : categories[sample];
		}

		@Override
		double centeredKernelForStates(int state1, int state2) {
			// Any pair touching the sentinel (non-selecting) state contributes nothing: this is the zero-padding.
			if (state1 == kernelStateRowMeans.length || state2 == kernelStateRowMeans.length)
				return 0d;
			if (fractile)
				return centeredFractileKernels[state1][state2];
			return (state1 == state2 ? 1d : 0d) - kernelStateRowMeans[state1]
					- kernelStateRowMeans[state2] + kernelGrandMean;
		}

		@Override
		double kernelTrace() {
			return kernelTrace;
		}
	}

	/**
	 * Scores the pairwise dependence between two levels and supports evaluating swaps incrementally. Each implementation
	 * maintains its current raw score and calculates the exact score change from swapping two samples in one of its
	 * levels. Scores are divided by their expected value under a random permutation so that different level types and
	 * numbers of choices have the same random baseline of {@code 1}.
	 */
	static abstract class PairCriterion {
		final LevelData[] levels;
		final LevelData level1;
		final LevelData level2;
		final double expectedRandomScore;
		double rawScore;

		PairCriterion(LevelData level1, LevelData level2, double expectedRandomScore) {
			this.levels = new LevelData[] { level1, level2 };
			this.level1 = level1;
			this.level2 = level2;
			this.expectedRandomScore = expectedRandomScore;
			Preconditions.checkState(Double.isFinite(expectedRandomScore) && expectedRandomScore > 0d,
					"Expected random score must be positive, have %s for levels %s and %s",
					expectedRandomScore, level1.levelIndex, level2.levelIndex);
		}

		/**
		 * @return current score divided by its expected score under random pairing
		 */
		final double normalizedScore() {
			// A randomly permuted pairing has expectation 1, independent of level cardinality/type.
			return rawScore/expectedRandomScore;
		}

		/**
		 * Calculates, but does not apply, the raw score change for a proposed swap. Must be computed from the pre-swap
		 * assignments (the caller applies the level swap only after this and {@link #applySwap(double)}).
		 * @param sample1 first sample index
		 * @param sample2 second sample index
		 * @param swappedLevels flags identifying which level is being swapped
		 * @return proposed raw-score change, where a negative value is an improvement
		 */
		abstract double calculateSwapDelta(int sample1, int sample2, boolean[] swappedLevels);

		/**
		 * Commits criterion-specific state for the most recently calculated swap.
		 * @param rawDelta raw-score change returned by {@link #calculateSwapDelta(int, int, boolean[])}
		 */
		abstract void applySwap(double rawDelta);

		/**
		 * Rebuilds the raw score from the current level assignments, also correcting any accumulated floating-point drift.
		 * @return recalculated raw score
		 */
		abstract double recalculateRawScore();

		/**
		 * @return short criterion description for diagnostic output
		 */
		abstract String typeName();
	}

	/**
	 * {@link PairCriterion} implementation for simple pairings between categorical levels. The raw score is the sum of
	 * squared differences between observed and independent-expected contingency-table counts,
	 * {@code sum_ij (N_ij - E_ij)^2} with {@code E_ij = r_i * c_j / n}. Its normalization is the exact expected raw
	 * score over random permutations with both sampled margins fixed, calculated by summing the hypergeometric variance
	 * of every table cell. (This is the equality-kernel special case of the HSIC score computed by
	 * {@link KernelPairCriterion}; it is kept separate only because the four-cell swap update below is cheaper.)
	 */
	static final class CategoricalPairCriterion extends PairCriterion {
		final CategoricalLevelData categorical1;
		final CategoricalLevelData categorical2;
		final double[][] expectations;
		final int[][] counts;
		final int[] swapRows = new int[4];
		final int[] swapColumns = new int[4];
		final int[] swapDeltas = new int[4];
		int numSwapChanges;

		CategoricalPairCriterion(CategoricalLevelData level1, CategoricalLevelData level2) {
			super(level1, level2, expectedRandomL2(level1, level2));
			this.categorical1 = level1;
			this.categorical2 = level2;
			this.expectations = new double[level1.numCategories][level2.numCategories];
			this.counts = new int[level1.numCategories][level2.numCategories];
			// Independence-expected count for cell (i,j): row marginal times column marginal over n. This is also the
			// mean of the cell count over random pairings, so residuals below are deviations from the random-pairing
			// mean.
			for (int i=0; i<level1.numCategories; i++)
				for (int j=0; j<level2.numCategories; j++)
					expectations[i][j] = (double)level1.marginalCounts[i]*level2.marginalCounts[j]/level1.numSamples;
			rawScore = rebuildCountsAndScore();
		}

		private static double expectedRandomL2(CategoricalLevelData level1, CategoricalLevelData level2) {
			// The expected raw score under random pairing equals the sum over all cells of the variance of that cell's
			// count. With both margins fixed, each cell count is hypergeometric, so its variance is
			// row*col*(n-row)*(n-col) / (n^2 (n-1)). The (n-1) is the finite-population correction (drawing without
			// replacement). Summing per-cell variances is valid because expectation is linear even though the cells are
			// not independent.
			int n = level1.numSamples;
			double sum = 0d;
			for (int row : level1.marginalCounts) {
				for (int column : level2.marginalCounts) {
					double numerator = (double)row*column*(n-row)*(n-column);
					sum += numerator/((double)n*n*(n-1d));
				}
			}
			return sum;
		}

		@Override
		double calculateSwapDelta(int sample1, int sample2, boolean[] swappedLevels) {
			// Swapping one level's value between two samples moves exactly two samples between contingency cells, so at
			// most four cells change (fewer if some coincide). We record the integer change per affected cell, then use
			// (r + d)^2 - r^2 = 2*r*d + d^2 to get the exact score delta from the pre-swap residuals r.
			boolean swapLevel1 = swappedLevels[level1.levelIndex];
			CategoricalLevelData swapped = swapLevel1 ? categorical1 : categorical2;
			CategoricalLevelData fixed = swapLevel1 ? categorical2 : categorical1;
			int swapped1 = swapped.values[sample1];
			int swapped2 = swapped.values[sample2];
			int fixed1 = fixed.values[sample1];
			int fixed2 = fixed.values[sample2];
			numSwapChanges = 0;
			if (swapLevel1) {
				addSwapChange(swapped1, fixed1, -1);
				addSwapChange(swapped2, fixed2, -1);
				addSwapChange(swapped2, fixed1, 1);
				addSwapChange(swapped1, fixed2, 1);
			} else {
				addSwapChange(fixed1, swapped1, -1);
				addSwapChange(fixed2, swapped2, -1);
				addSwapChange(fixed1, swapped2, 1);
				addSwapChange(fixed2, swapped1, 1);
			}
			// Only four cells can change. For residual r and integer change d, (r+d)^2 - r^2 = 2*r*d + d^2.
			double delta = 0d;
			for (int i=0; i<numSwapChanges; i++) {
				double residual = counts[swapRows[i]][swapColumns[i]]
						- expectations[swapRows[i]][swapColumns[i]];
				delta += 2d*residual*swapDeltas[i] + swapDeltas[i]*swapDeltas[i];
			}
			return delta;
		}

		private void addSwapChange(int row, int column, int delta) {
			// Accumulate into an existing (row,column) entry if present, so a cell touched twice (its net change may be
			// zero, e.g. when the two samples share the fixed-level value) is handled correctly rather than double
			// counted.
			for (int i=0; i<numSwapChanges; i++) {
				if (swapRows[i] == row && swapColumns[i] == column) {
					swapDeltas[i] += delta;
					return;
				}
			}
			swapRows[numSwapChanges] = row;
			swapColumns[numSwapChanges] = column;
			swapDeltas[numSwapChanges] = delta;
			numSwapChanges++;
		}

		@Override
		void applySwap(double rawDelta) {
			for (int i=0; i<numSwapChanges; i++)
				counts[swapRows[i]][swapColumns[i]] += swapDeltas[i];
			rawScore += rawDelta;
		}

		private double rebuildCountsAndScore() {
			for (int[] row : counts)
				Arrays.fill(row, 0);
			for (int sample=0; sample<level1.numSamples; sample++)
				counts[categorical1.values[sample]][categorical2.values[sample]]++;
			double score = 0d;
			for (int i=0; i<counts.length; i++) {
				for (int j=0; j<counts[i].length; j++) {
					double residual = counts[i][j] - expectations[i][j];
					score += residual*residual;
				}
			}
			return score;
		}

		@Override
		double recalculateRawScore() {
			rawScore = rebuildCountsAndScore();
			return rawScore;
		}

		@Override
		String typeName() {
			return "categorical";
		}
	}

	/**
	 * {@link PairCriterion} for pairings represented by centered kernels, including fractile and conditional pairings.
	 * The raw score is the (scaled) Frobenius inner product of the two centered kernel matrices,
	 * {@code (1/N^2) * sum_ab centeredK[a][b] * centeredL[a][b]} -- the empirical HSIC. Each product term is positive
	 * when both levels agree a sample pair is above- (or below-) baseline similar, and negative when they disagree, so
	 * the sum is large when the levels are dependent and near zero when independent.
	 * <p>
	 * Its random-pairing expectation is {@code trace(K)*trace(L) / (N^2*(N-1))}; dividing by that gives a random
	 * baseline of {@code 1}, consistent with {@link CategoricalPairCriterion}. (The (N-1) again arises because, under a
	 * random permutation, the two positions of an off-diagonal entry map to two <i>distinct</i> samples; the derivation
	 * relies on centered rows/columns summing to zero.)
	 */
	static final class KernelPairCriterion extends PairCriterion {
		final int numSamples;
		final KernelLevelData kernelLevel1;
		final KernelLevelData kernelLevel2;
		final KernelLevelData leftKernelLevel;
		final KernelLevelData rightKernelLevel;
		// [left kernel state][right state] = sum over samples in that right state of the left centered-kernel column.
		// This "left-kernel times joint-state counts" cache lets both the score and each swap delta be evaluated by
		// looping over right states only, rather than over all sample pairs.
		final double[][] leftKernelTimesJointCounts;
		int pendingLeftState1;
		int pendingLeftState2;
		int pendingRightState1;
		int pendingRightState2;

		KernelPairCriterion(KernelLevelData level1, KernelLevelData level2) {
			super(level1, level2, expectedRandomL2(level1, level2));
			this.numSamples = level1.numSamples;
			this.kernelLevel1 = level1;
			this.kernelLevel2 = level2;
			// Delta evaluation loops over right states, so put the smaller state space there.
			if (level1.numKernelStates() >= level2.numKernelStates()) {
				this.leftKernelLevel = level1;
				this.rightKernelLevel = level2;
			} else {
				this.leftKernelLevel = level2;
				this.rightKernelLevel = level1;
			}
			this.leftKernelTimesJointCounts =
					new double[leftKernelLevel.numKernelStates()][rightKernelLevel.numKernelStates()];
			rebuildLeftKernelTimesJointCounts();
			rawScore = calculateRawScore();
		}

		private static double expectedRandomL2(KernelLevelData level1, KernelLevelData level2) {
			double n = level1.numSamples;
			// For independently permuted centered Gram matrices K and L, E[sum(K .* L)] = trace(K)*trace(L)/(N-1); the
			// extra 1/N^2 matches the scaling applied to the raw score in calculateRawScore().
			return level1.kernelTrace()*level2.kernelTrace()/(n*n*(n-1d));
		}

		@Override
		double calculateSwapDelta(int sample1, int sample2, boolean[] swappedLevels) {
			// Exact HSIC delta for swapping one level's value between two samples. Split into a diagonal term (the two
			// swapped samples' self-similarity contributions) and an off-diagonal term computed from the cached
			// left-kernel-times-counts table, with a correction removing the two swapped samples from that grouped sum
			// (they are handled by the diagonal term).
			KernelLevelData swapped = swappedLevels[level1.levelIndex] ? kernelLevel1 : kernelLevel2;
			KernelLevelData fixed = swapped == kernelLevel1 ? kernelLevel2 : kernelLevel1;
			pendingLeftState1 = leftKernelLevel.kernelState(sample1);
			pendingLeftState2 = leftKernelLevel.kernelState(sample2);
			pendingRightState1 = rightKernelLevel.kernelState(sample1);
			pendingRightState2 = rightKernelLevel.kernelState(sample2);

			int swappedState1 = swapped.kernelState(sample1);
			int swappedState2 = swapped.kernelState(sample2);
			int fixedState1 = fixed.kernelState(sample1);
			int fixedState2 = fixed.kernelState(sample2);
			double delta = (swapped.centeredKernelForStates(swappedState2, swappedState2)
					-swapped.centeredKernelForStates(swappedState1, swappedState1))
					*(fixed.centeredKernelForStates(fixedState1, fixedState1)
							-fixed.centeredKernelForStates(fixedState2, fixedState2));

			int leftPlus, leftMinus, rightPlus, rightMinus;
			if (swapped == leftKernelLevel) {
				leftPlus = pendingLeftState2;
				leftMinus = pendingLeftState1;
				rightPlus = pendingRightState1;
				rightMinus = pendingRightState2;
			} else {
				leftPlus = pendingLeftState1;
				leftMinus = pendingLeftState2;
				rightPlus = pendingRightState2;
				rightMinus = pendingRightState1;
			}

			// This is the old per-sample sum grouped and partially multiplied by kernel state.
			double offDiagonal = 0d;
			for (int rightState=0; rightState<rightKernelLevel.numKernelStates(); rightState++)
				offDiagonal += (leftKernelTimesJointCounts[leftPlus][rightState]
						-leftKernelTimesJointCounts[leftMinus][rightState])
						*(rightKernelLevel.centeredKernelForStates(rightPlus, rightState)
								-rightKernelLevel.centeredKernelForStates(rightMinus, rightState));

			// The grouped sum includes the two swapped samples; the separate diagonal term above handles them.
			offDiagonal -= kernelDifference(leftKernelLevel, leftPlus, leftMinus, pendingLeftState1)
					*kernelDifference(rightKernelLevel, rightPlus, rightMinus, pendingRightState1);
			offDiagonal -= kernelDifference(leftKernelLevel, leftPlus, leftMinus, pendingLeftState2)
					*kernelDifference(rightKernelLevel, rightPlus, rightMinus, pendingRightState2);
			delta += 2d*offDiagonal;
			return delta/((double)numSamples*numSamples);
		}

		private static double kernelDifference(KernelLevelData level, int plusState, int minusState, int otherState) {
			return level.centeredKernelForStates(plusState, otherState)
					-level.centeredKernelForStates(minusState, otherState);
		}

		@Override
		void applySwap(double rawDelta) {
			// Swapping either level changes two columns of the joint state table. Update its kernel transform directly
			// while LevelData still contains the pre-swap states.
			for (int leftKernelState=0; leftKernelState<leftKernelTimesJointCounts.length; leftKernelState++) {
				double difference = leftKernelLevel.centeredKernelForStates(leftKernelState, pendingLeftState2)
						-leftKernelLevel.centeredKernelForStates(leftKernelState, pendingLeftState1);
				leftKernelTimesJointCounts[leftKernelState][pendingRightState1] += difference;
				leftKernelTimesJointCounts[leftKernelState][pendingRightState2] -= difference;
			}
			rawScore += rawDelta;
		}

		private void rebuildLeftKernelTimesJointCounts() {
			// For each (leftKernelState, rightState), accumulate the left centered-kernel value between that left state
			// and each sample whose right state matches. This is the left kernel matrix multiplied by the joint
			// state-count structure, grouped so downstream sums cost O(states) rather than O(samples).
			for (double[] row : leftKernelTimesJointCounts)
				Arrays.fill(row, 0d);
			for (int sample=0; sample<numSamples; sample++) {
				int leftState = leftKernelLevel.kernelState(sample);
				int rightState = rightKernelLevel.kernelState(sample);
				for (int leftKernelState=0; leftKernelState<leftKernelTimesJointCounts.length; leftKernelState++)
					leftKernelTimesJointCounts[leftKernelState][rightState] +=
							leftKernelLevel.centeredKernelForStates(leftKernelState, leftState);
			}
		}

		private double calculateRawScore() {
			// Sum the Frobenius inner product (empirical HSIC numerator) from the same transformed joint-state cache.
			double numerator = 0d;
			for (int sample=0; sample<numSamples; sample++) {
				int leftState = leftKernelLevel.kernelState(sample);
				int rightState = rightKernelLevel.kernelState(sample);
				for (int otherRightState=0; otherRightState<rightKernelLevel.numKernelStates(); otherRightState++)
					numerator += leftKernelTimesJointCounts[leftState][otherRightState]
							*rightKernelLevel.centeredKernelForStates(rightState, otherRightState);
			}
			return numerator/((double)numSamples*numSamples);
		}

		@Override
		double recalculateRawScore() {
			rebuildLeftKernelTimesJointCounts();
			rawScore = calculateRawScore();
			return rawScore;
		}

		@Override
		String typeName() {
			if (level1 instanceof FractileLevelData && level2 instanceof FractileLevelData)
				return "fractile-fractile";
			return "categorical-fractile";
		}
	}

	/**
	 * {@link PairCriterion} instance for scoring pairs where at least one level is a combined level. A combined level is
	 * decomposed into scoring components -- its selector (a categorical level: which sub-level was chosen) and its
	 * conditionals (the within-sub-level value distributions) -- and this criterion scores the Cartesian product of the
	 * two levels' components, combining their <i>already-normalized</i> scores with fixed weights that sum to one.
	 * Because the components are pre-normalized, this criterion's own {@code expectedRandomScore} is 1 and its
	 * {@code rawScore} is already a normalized quantity (unlike the simple criteria, whose rawScore is un-normalized).
	 * <p>
	 * <b>Weighting.</b> A combined level splits its weight 50/50 between selector balance (which sub-level is chosen)
	 * and within-selector balance (the values given the choice); conditionals share their half in proportion to their
	 * sampled prevalence. The 50/50 split is a modeling choice, not a derived constant: the selector is given parity
	 * with the entire conditional structure because it acts as a gatekeeper -- selector imbalance distorts every
	 * conditional at once, whereas conditional imbalance is local to the selecting samples. See the LaTeX write-up for
	 * discussion and alternatives.
	 */
	static final class CompositePairCriterion extends PairCriterion {
		final PairCriterion[] components;
		final double[] weights;
		final double[] componentDeltas;

		CompositePairCriterion(LevelData level1, LevelData level2) {
			super(level1, level2, 1d);
			List<LevelData> components1 = scoringComponents(level1);
			List<LevelData> components2 = scoringComponents(level2);
			double[] weights1 = scoringWeights(level1);
			double[] weights2 = scoringWeights(level2);
			components = new PairCriterion[components1.size()*components2.size()];
			weights = new double[components.length];
			componentDeltas = new double[components.length];
			// Taking the Cartesian product also defines combined-versus-combined scoring; component weights multiply and
			// still sum to one.
			int index = 0;
			for (int i=0; i<components1.size(); i++) {
				for (int j=0; j<components2.size(); j++) {
					components[index] = buildSimpleCriterion(components1.get(i), components2.get(j));
					weights[index] = weights1[i]*weights2[j];
					index++;
				}
			}
			rawScore = calculateCompositeScore();
		}

		private static List<LevelData> scoringComponents(LevelData level) {
			if (!(level instanceof CombinedLevelData))
				return List.of(level);
			CombinedLevelData combined = (CombinedLevelData)level;
			List<LevelData> ret = new ArrayList<>(1+combined.conditionals.size());
			// Only include the selector if it actually varies (nonzero trace); a constant selector has no pairwise
			// dependence to optimize.
			if (combined.selector.kernelTrace() > 0d)
				ret.add(combined.selector);
			ret.addAll(combined.conditionals);
			return ret;
		}

		private static double[] scoringWeights(LevelData level) {
			if (!(level instanceof CombinedLevelData))
				return new double[] { 1d };
			CombinedLevelData combined = (CombinedLevelData)level;
			boolean includeSelector = combined.selector.kernelTrace() > 0d;
			double[] ret = new double[(includeSelector ? 1 : 0)+combined.conditionals.size()];
			// Split a combined level 50/50 between selector balance and within-selector balance. Conditional sublevels
			// share their half according to sampled prevalence. If the selector does not vary, conditionals take the
			// whole weight.
			double selectorWeight = combined.conditionals.isEmpty() ? 1d : 0.5d;
			double conditionalScale = includeSelector ? 0.5d : 1d;
			int offset = 0;
			if (includeSelector)
				ret[offset++] = selectorWeight;
			for (int i=0; i<combined.conditionalWeights.length; i++)
				ret[offset+i] = conditionalScale*combined.conditionalWeights[i];
			return ret;
		}

		@Override
		double calculateSwapDelta(int sample1, int sample2, boolean[] swappedLevels) {
			// The composite raw score is a weighted sum of component normalized scores, so its delta is the same
			// weighted sum of component raw deltas each divided by that component's own random baseline.
			double delta = 0d;
			for (int i=0; i<components.length; i++) {
				componentDeltas[i] = components[i].calculateSwapDelta(sample1, sample2, swappedLevels);
				delta += weights[i]*componentDeltas[i]/components[i].expectedRandomScore;
			}
			return delta;
		}

		@Override
		void applySwap(double rawDelta) {
			for (int i=0; i<components.length; i++)
				components[i].applySwap(componentDeltas[i]);
			rawScore += rawDelta;
		}

		private double calculateCompositeScore() {
			double score = 0d;
			for (int i=0; i<components.length; i++)
				score += weights[i]*components[i].normalizedScore();
			return score;
		}

		@Override
		double recalculateRawScore() {
			for (PairCriterion component : components)
				component.recalculateRawScore();
			rawScore = calculateCompositeScore();
			return rawScore;
		}

		@Override
		String typeName() {
			return "combined-hierarchical";
		}
	}

	private static PairCriterion buildSimpleCriterion(LevelData level1, LevelData level2) {
		// Categorical/categorical uses the cheaper contingency criterion; anything involving a kernel level uses the
		// HSIC criterion. (The two agree exactly on categorical/categorical pairs.)
		if (level1 instanceof CategoricalLevelData && level2 instanceof CategoricalLevelData)
			return new CategoricalPairCriterion((CategoricalLevelData)level1, (CategoricalLevelData)level2);
		Preconditions.checkState(level1 instanceof KernelLevelData && level2 instanceof KernelLevelData);
		return new KernelPairCriterion((KernelLevelData)level1, (KernelLevelData)level2);
	}

	/**
	 * Owns all pair criteria for a set of levels and coordinates transactional swap evaluation. A proposed swap is first
	 * evaluated across every affected criterion; the caller must then either {@link #applySwap()} or
	 * {@link #discardSwap()}. This avoids mutating level or criterion state for rejected proposals.
	 */
	static final class PairwiseScorer {
		final List<LevelData> levels;
		final List<PairCriterion> criteria;
		final boolean[] swappedLevels;
		final double[] rawSwapDeltas;
		final boolean[] affectedCriteria;
		boolean swapPending;
		int pendingSample1;
		int pendingSample2;
		
		final double initialScore;

		/**
		 * Builds criteria for every pair of non-null levels.
		 * @param levels level representations, with null entries for levels that do not vary
		 */
		PairwiseScorer(List<LevelData> levels) {
			this(levels, null);
		}

		/**
		 * Builds criteria for selected pairs of non-null levels.
		 * @param levels level representations, with null entries for levels that do not vary
		 * @param includePairs upper-triangular mask of level pairs to score, or null to include every pair
		 */
		PairwiseScorer(List<LevelData> levels, boolean[][] includePairs) {
			this.levels = levels;
			this.criteria = new ArrayList<>();
			for (int l1=0; l1<levels.size(); l1++) {
				LevelData level1 = levels.get(l1);
				if (level1 == null)
					continue;
				for (int l2=l1+1; l2<levels.size(); l2++) {
					if (includePairs != null && !includePairs[l1][l2])
						continue;
					LevelData level2 = levels.get(l2);
					if (level2 == null)
						continue;
					// Any pair touching a combined level needs the composite (component-decomposing) criterion.
					PairCriterion criterion = level1 instanceof CombinedLevelData || level2 instanceof CombinedLevelData
							? new CompositePairCriterion(level1, level2) : buildSimpleCriterion(level1, level2);
					criteria.add(criterion);
				}
			}
			this.swappedLevels = new boolean[levels.size()];
			this.rawSwapDeltas = new double[criteria.size()];
			this.affectedCriteria = new boolean[criteria.size()];
			
			initialScore = score();
		}

		/**
		 * @return number of level-pair criteria; composite internals still count as one level pair
		 */
		int size() {
			return criteria.size();
		}

		/**
		 * @return sum of normalized scores across all level pairs
		 */
		double score() {
			double score = 0d;
			for (PairCriterion criterion : criteria)
				score += criterion.normalizedScore();
			return score;
		}
		
		/**
		 * @return the worst normalized level pair score
		 */
		double worstScore() {
			double worst = 0d;
			for (PairCriterion criterion : criteria)
				worst = Math.max(worst, criterion.normalizedScore());
			return worst;
		}
		
		/**
		 * @return mean normalized score per level pair
		 */
		double avgScore() {
			return score()/size();
		}

		/**
		 * Evaluates swapping one level between two samples without committing score or assignment changes.
		 * @param sample1 first sample index
		 * @param sample2 second sample index
		 * @param levelIndex level to swap
		 * @return change in total normalized score
		 */
		double evaluateSwap(int sample1, int sample2, int levelIndex) {
			return evaluateSwap(sample1, sample2, new int[] { levelIndex });
		}

		/**
		 * Evaluates swapping one or more levels between two samples without committing score or assignment changes. Swapping
		 * multiple levels is used when an entire branch moves; criteria for which both levels move are unchanged and skipped.
		 * @param sample1 first sample index
		 * @param sample2 second sample index
		 * @param levelIndexes levels to swap
		 * @return change in total normalized score
		 */
		double evaluateSwap(int sample1, int sample2, int[] levelIndexes) {
			// Evaluation is transactional: criteria save their prospective deltas, but no counts, scores, or level
			// assignments change until applySwap(). A criterion is affected only if exactly one of its two levels is
			// being swapped -- if both move together (whole-branch swap) their joint relationship is unchanged.
			Preconditions.checkState(!swapPending, "Previous swap evaluation must be applied or discarded");
			Arrays.fill(swappedLevels, false);
			Arrays.fill(rawSwapDeltas, 0d);
			Arrays.fill(affectedCriteria, false);
			for (int levelIndex : levelIndexes)
				if (levelIndex >= 0 && levels.get(levelIndex) != null)
					swappedLevels[levelIndex] = true;

			double delta = 0d;
			for (int i=0; i<criteria.size(); i++) {
				PairCriterion criterion = criteria.get(i);
				int numSwapped = 0;
				for (LevelData level : criterion.levels)
					if (swappedLevels[level.levelIndex])
						numSwapped++;
				if (numSwapped > 0 && numSwapped < criterion.levels.length) {
					double rawDelta = criterion.calculateSwapDelta(sample1, sample2, swappedLevels);
					affectedCriteria[i] = true;
					rawSwapDeltas[i] = rawDelta;
					delta += rawDelta/criterion.expectedRandomScore;
				}
			}
			pendingSample1 = sample1;
			pendingSample2 = sample2;
			swapPending = true;
			return delta;
		}

		/** Commits the pending criterion updates and level assignments. */
		void applySwap() {
			Preconditions.checkState(swapPending, "No pending swap evaluation");
			for (int i=0; i<criteria.size(); i++)
				if (affectedCriteria[i])
					criteria.get(i).applySwap(rawSwapDeltas[i]);
			// Apply criterion state first because its pending changes were calculated from the pre-swap level data; then
			// move the shared level representations.
			for (int levelIndex=0; levelIndex<swappedLevels.length; levelIndex++)
				if (swappedLevels[levelIndex])
					levels.get(levelIndex).swap(pendingSample1, pendingSample2);
			swapPending = false;
		}

		/** Discards the pending swap without changing scores or level assignments. */
		void discardSwap() {
			Preconditions.checkState(swapPending, "No pending swap evaluation");
			swapPending = false;
		}

		/**
		 * Recalculates all criteria from the current assignments (also correcting floating-point drift accumulated over
		 * many incremental swaps).
		 * @return recalculated total normalized score
		 */
		double recalculateScore() {
			double score = 0d;
			for (PairCriterion criterion : criteria) {
				criterion.recalculateRawScore();
				score += criterion.normalizedScore();
			}
			return score;
		}
		
		private static final DecimalFormat pDF = new DecimalFormat("0.00%");
		private static final DecimalFormat scoreDF = new DecimalFormat("0.000000");

		/**
		 * Prints raw, random-expected, and normalized scores for each level pair. Note: for composite (combined-level)
		 * criteria the printed "raw" value is already a normalized, weighted average of component scores, not an
		 * un-normalized raw score as for the simple criteria.
		 * @param namedLevels original levels used to label each pair
		 */
		void printStats(List<? extends LogicTreeLevel<?>> namedLevels) {
			System.out.println("Pairwise normalized scores (random expectation = 1 per pair):");
			List<String> pairNames = new ArrayList<>(criteria.size());
			List<String> pairStats = new ArrayList<>(criteria.size());
			int longestPairName = 0;
			for (PairCriterion criterion : criteria) {
				String name1 = namedLevels.get(criterion.level1.levelIndex).getShortName();
				String name2 = namedLevels.get(criterion.level2.levelIndex).getShortName();
				String pairName = name1+" / "+name2+" ["+criterion.typeName()+"]:";
				longestPairName = Integer.max(longestPairName, pairName.length());
				pairNames.add(pairName);
//				pairStats.add("raw="+scoreDF.format(criterion.rawScore)+";\trandom="
//						+scoreDF.format(criterion.expectedRandomScore)+";\tnormalized="
//						+scoreDF.format(criterion.normalizedScore()));
				pairStats.add(scoreDF.format(criterion.normalizedScore())
						+"\t(raw="+scoreDF.format(criterion.rawScore)
						+";\trand="+scoreDF.format(criterion.expectedRandomScore)+")");
			}
			
			for (int p=0; p<pairNames.size(); p++) {
				String name = pairNames.get(p);
				String stats = pairStats.get(p);
				while (name.length() < longestPairName)
					name += " ";
				System.out.println(name+"\t"+stats);
			}
			System.out.println("Total normalized score: "+scoreDF.format(score()));
		}

		String summaryStats() {
			double score = score();
			return "avg="+scoreDF.format(avgScore())+";\tworst="+scoreDF.format(worstScore())+";\tsum="+scoreDF.format(score)
					+";\treduction="+formatReduction(initialScore, score);
		}

		private static String formatReduction(double initialScore, double score) {
			if (initialScore == 0d)
				return pDF.format(0d);
			return pDF.format((initialScore-score)/initialScore);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static double fractile(FractileSamplingLevel fractileLevel, LogicTreeNode node) {
		Preconditions.checkState(node instanceof ValuedLogicTreeNode<?>,
				"Fractile sampling node must be value-backed, have %s", node.getClass().getName());
		return fractileLevel.getFractile(((ValuedLogicTreeNode<?>)node).getValue());
	}

	static boolean hasFractileSubLevel(AbstractCombinedSamplingLevel<?,?> level) {
		for (int i=0; i<level.getSubLevels().size(); i++)
			if (level.getSubLevels().getValue(i) instanceof FractileSamplingLevel<?,?>)
				return true;
		return false;
	}

	/**
	 * Builds the {@link CombinedLevelData} for a combined sampling level: a categorical selector (which sub-level each
	 * sample chose) plus one {@link ConditionalLevelData} per sub-level that was chosen by at least two samples and
	 * whose selected values vary. Conditional weights are the sampled prevalence of each varying sub-level,
	 * renormalized over the varying conditionals only. Returns null if nothing about the level varies.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static LevelData combinedLevelData(int levelIndex, AbstractCombinedSamplingLevel combinedLevel,
			List<? extends LogicTreeNode> sampledNodes) {
		int numSamples = sampledNodes.size();
		int numSubLevels = combinedLevel.getSubLevels().size();
		int[] selectors = new int[numSamples];
		int[] nodeIndexes = new int[numSamples];
		int[] selectorCounts = new int[numSubLevels];
		for (int i=0; i<numSamples; i++) {
			int[] indexes = (int[])((ValuedLogicTreeNode<?>)sampledNodes.get(i)).getValue();
			Preconditions.checkState(indexes.length == 2 && indexes[0] >= 0 && indexes[0] < numSubLevels);
			selectors[i] = indexes[0];
			nodeIndexes[i] = indexes[1];
			selectorCounts[indexes[0]]++;
		}
		// The selector answers which sublevel was chosen; each conditional below answers how values are distributed
		// given that particular choice.
		CategoricalLevelData selector = new CategoricalLevelData(levelIndex, numSubLevels, selectors);
		List<ConditionalLevelData> conditionals = new ArrayList<>();
		List<Double> prevalences = new ArrayList<>();
		for (int s=0; s<numSubLevels; s++) {
			if (selectorCounts[s] < 2)
				continue;
			LogicTreeLevel subLevel = (LogicTreeLevel)combinedLevel.getSubLevels().getValue(s);
			boolean[] selected = new boolean[numSamples];
			if (subLevel instanceof FractileSamplingLevel<?,?>) {
				double[] values = new double[numSamples];
				for (int i=0; i<numSamples; i++) {
					selected[i] = selectors[i] == s;
					if (selected[i])
						values[i] = fractile((FractileSamplingLevel)subLevel,
								(LogicTreeNode)subLevel.getNodes().get(nodeIndexes[i]));
				}
				if (hasMultipleSelectedFractiles(selected, values)) {
					conditionals.add(new ConditionalLevelData(levelIndex, selected, values));
					prevalences.add((double)selectorCounts[s]/numSamples);
				}
			} else {
				LogicTreeLevel categoricalLevel = subLevel;
				BinnedLevel binnedLevel = null;
				if (subLevel instanceof BinnableLevel<?,?,?>) {
					categoricalLevel = (LogicTreeLevel)((BinnableLevel)subLevel).toBinnedLevel();
					binnedLevel = (BinnedLevel)categoricalLevel;
				}
				List nodes = categoricalLevel.getNodes();
				int[] values = new int[numSamples];
				for (int i=0; i<numSamples; i++) {
					selected[i] = selectors[i] == s;
					if (selected[i]) {
						LogicTreeNode node = (LogicTreeNode)subLevel.getNodes().get(nodeIndexes[i]);
						if (binnedLevel != null)
							node = (LogicTreeNode)binnedLevel.getBinUnchecked(node);
						values[i] = nodes.indexOf(node);
						Preconditions.checkState(values[i] >= 0);
					}
				}
				if (hasMultipleSelectedCategories(selected, values)) {
					conditionals.add(new ConditionalLevelData(levelIndex, selected, values, nodes.size()));
					prevalences.add((double)selectorCounts[s]/numSamples);
				}
			}
		}
		// Renormalize over varying conditionals only: constant or unsampled sublevels have no pairwise dependence to
		// optimize and should not consume conditional weight.
		double prevalenceSum = 0d;
		for (double prevalence : prevalences)
			prevalenceSum += prevalence;
		double[] conditionalWeights = new double[prevalences.size()];
		for (int i=0; i<conditionalWeights.length; i++)
			conditionalWeights[i] = prevalences.get(i)/prevalenceSum;
		if (selector.kernelTrace() == 0d && conditionals.isEmpty())
			return null;
		return new CombinedLevelData(levelIndex, selector, conditionals, conditionalWeights, nodeIndexes);
	}

	private static boolean hasMultipleSelectedCategories(boolean[] selected, int[] values) {
		int first = -1;
		for (int i=0; i<values.length; i++)
			if (selected[i]) {
				if (first < 0)
					first = values[i];
				else if (values[i] != first)
					return true;
			}
		return false;
	}

	private static boolean hasMultipleSelectedFractiles(boolean[] selected, double[] values) {
		int first = -1;
		for (int i=0; i<values.length; i++)
			if (selected[i]) {
				int bin = fractileBin(values[i]);
				if (first < 0)
					first = bin;
				else if (bin != first)
					return true;
			}
		return false;
	}

	static boolean hasMultipleCategories(int[] values) {
		int first = values[0];
		for (int i=1; i<values.length; i++)
			if (values[i] != first)
				return true;
		return false;
	}

	static boolean hasMultipleFractiles(double[] values) {
		int first = fractileBin(values[0]);
		for (int i=1; i<values.length; i++)
			if (fractileBin(values[i]) != first)
				return true;
		return false;
	}

	private static int fractileBin(double fractile) {
		return Math.min(FRACTILE_BINS-1, (int)(fractile*FRACTILE_BINS));
	}

	private static short checkedFractileBin(double fractile) {
		int bin = fractileBin(fractile);
		Preconditions.checkState(bin >= 0 && bin <= Short.MAX_VALUE,
				"Fractile bin index %s overflows short storage; FRACTILE_BINS=%s", bin, FRACTILE_BINS);
		return (short)bin;
	}

	private static double rawFractileKernel(int bin1, int bin2) {
		// Raw CDF-kernel similarity of two fractiles: integral_0^1 I(u<=t) I(v<=t) dt = 1 - max(u,v), evaluated at bin
		// midpoints. Two low fractiles (features "on" almost everywhere) overlap a lot -> high raw similarity; two high
		// fractiles overlap only near t=1 -> low raw similarity. This location bias is removed by centering.
		return 1d-(Math.max(bin1, bin2)+0.5d)/FRACTILE_BINS;
	}
}