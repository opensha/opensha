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

final class PairwiseLogicTreeTools {
	// for continuous levels, bin quantize the CDF kernels for lookup speed and minimal cache size
	static final int FRACTILE_BINS = 100;

	private PairwiseLogicTreeTools() {}

	static abstract class LevelData {
		final int levelIndex;
		final int numSamples;

		LevelData(int levelIndex, int numSamples) {
			this.levelIndex = levelIndex;
			this.numSamples = numSamples;
		}

		/**
		 * Checks if two samples are identical; for quantized continuous levels, this checks that two samples fall within
		 * the same bin.
		 * @param sample1
		 * @param sample2
		 * @return true if the samples are equal, false otherwise
		 */
		abstract boolean matches(int sample1, int sample2);

		/**
		 * Swaps the values between the two samples (branches)
		 *
		 * @param sample1
		 * @param sample2
		 */
		abstract void swap(int sample1, int sample2);

		/**
		 * Measures the similarity between two samples after removing the similarity expected from the level's overall
		 * distribution.
		 *
		 * @return baseline-adjusted similarity between the samples
		 */
		abstract double centeredKernel(int sample1, int sample2);

		/**
		 * Summarizes the amount of variation among samples in this level. This is used to calculate the pairwise score
		 * expected from random pairings.
		 *
		 * @return sum of each sample's baseline-adjusted similarity with itself
		 */
		abstract double kernelTrace();
	}

	/**
	 * Level data for categorical data, i.e., samples from a set of fixed choices
	 */
	static final class CategoricalLevelData extends LevelData {
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
			// Mean of the full equality-kernel matrix, equivalently the probability that two sampled values match.
			// Double-centering subtracts the row means for both samples, so this global mean is added back once.
			double grandMean = 0d;
			for (int i=0; i<numCategories; i++) {
				marginalFractions[i] = (double)marginalCounts[i] / numSamples;
				grandMean += marginalFractions[i] * marginalFractions[i];
			}
			this.kernelGrandMean = grandMean;
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
		double centeredKernel(int sample1, int sample2) {
			// H*K*H for K(i,j)=1 when samples i and j have the same category.
			int value1 = values[sample1];
			int value2 = values[sample2];
			return (value1 == value2 ? 1d : 0d) - marginalFractions[value1]
					- marginalFractions[value2] + kernelGrandMean;
		}

		@Override
		double kernelTrace() {
			return kernelTrace;
		}
	}

	/**
	 * Level data for samples from continuous distributions, i.e., levels that implement {@link FractileSamplingLevel}.
	 */
	static final class FractileLevelData extends LevelData {
		final double[] fractiles;
		final short[] fractileBins;
		final float[][] centeredKernels;
		final double kernelTrace;

		FractileLevelData(int levelIndex, double[] fractiles) {
			super(levelIndex, fractiles.length);
			this.fractiles = fractiles;
			this.fractileBins = new short[numSamples];
			int[] binCounts = new int[FRACTILE_BINS];
			for (int i=0; i<numSamples; i++) {
				double fractile = fractiles[i];
				Preconditions.checkArgument(Double.isFinite(fractile) && fractile >= 0d && fractile <= 1d,
						"Fractile must be finite and in [0,1], have " + fractile + " for level " + levelIndex);
				fractileBins[i] = (short)fractileBin(fractile);
				binCounts[fractileBins[i]]++;
			}

			// Cache H*K*H by bin; swaps change sample-to-bin assignments, not these values.
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
			double value = fractiles[sample1];
			fractiles[sample1] = fractiles[sample2];
			fractiles[sample2] = value;
			short bin = fractileBins[sample1];
			fractileBins[sample1] = fractileBins[sample2];
			fractileBins[sample2] = bin;
		}

		@Override
		double centeredKernel(int sample1, int sample2) {
			return centeredKernels[fractileBins[sample1]][fractileBins[sample2]];
		}

		@Override
		double kernelTrace() {
			return kernelTrace;
		}
	}

	/**
	 * Level data for {@link AbstractCombinedSamplingLevel} instances that might contain both categorical and continuous
	 * data
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
			// Every component is another view of the same combined-level assignment.
			selector.swap(sample1, sample2);
			for (ConditionalLevelData conditional : conditionals)
				conditional.swap(sample1, sample2);
			int value = nodeValues[sample1];
			nodeValues[sample1] = nodeValues[sample2];
			nodeValues[sample2] = value;
		}

		// A combined level has no single kernel: CompositePairCriterion scores its selector
		// and conditional kernels separately, then combines their normalized scores.
		@Override double centeredKernel(int sample1, int sample2) { throw new UnsupportedOperationException(); }
		@Override double kernelTrace() { throw new UnsupportedOperationException(); }
	}

	/**
	 * Used by {@link CombinedLevelData} to track data for choices (categorical or fractile) within combined levels
	 */
	static final class ConditionalLevelData extends LevelData {
		final boolean fractile;
		final boolean[] selected;
		final int[] categories;
		final double[] fractiles;
		final short[] fractileBins;
		final double[] kernelRowMeans;
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
			this.fractiles = fractiles;
			this.fractileBins = this.fractile ? new short[numSamples] : null;
			int selectedCount = 0;
			for (boolean value : selected)
				if (value)
					selectedCount++;
			Preconditions.checkArgument(selectedCount > 1);

			// Center only among samples that selected this sublevel. Unselected samples are
			// represented by zero rows/columns when this conditional kernel is embedded globally.
			kernelRowMeans = new double[numSamples];
			double grandMean = 0d;
			if (this.fractile) {
				int[] binCounts = new int[FRACTILE_BINS];
				for (int i=0; i<numSamples; i++)
					if (selected[i]) {
						double value = fractiles[i];
						Preconditions.checkArgument(Double.isFinite(value) && value >= 0d && value <= 1d);
						fractileBins[i] = (short)fractileBin(value);
						binCounts[fractileBins[i]]++;
					}
				double[] meansByBin = new double[FRACTILE_BINS];
				for (int bin1=0; bin1<FRACTILE_BINS; bin1++)
					for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
						meansByBin[bin1] += binCounts[bin2]*rawFractileKernel(bin1, bin2)/selectedCount;
				for (int i=0; i<numSamples; i++) {
					if (!selected[i])
						continue;
					kernelRowMeans[i] = meansByBin[fractileBins[i]];
					grandMean += kernelRowMeans[i];
				}
			} else {
				int[] counts = new int[numCategories];
				for (int i=0; i<numSamples; i++)
					if (selected[i])
						counts[categories[i]]++;
				for (int i=0; i<numSamples; i++)
					if (selected[i]) {
						kernelRowMeans[i] = (double)counts[categories[i]]/selectedCount;
						grandMean += kernelRowMeans[i];
					}
			}
			this.kernelGrandMean = grandMean/selectedCount;
			if (this.fractile) {
				centeredFractileKernels = new float[FRACTILE_BINS][FRACTILE_BINS];
				for (int bin1=0; bin1<FRACTILE_BINS; bin1++)
					for (int bin2=0; bin2<FRACTILE_BINS; bin2++)
						centeredFractileKernels[bin1][bin2] = (float)(rawFractileKernel(bin1, bin2)
								-kernelMeanForBin(bin1)-kernelMeanForBin(bin2)+kernelGrandMean);
			} else {
				centeredFractileKernels = null;
			}

			double trace = 0d;
			for (int i=0; i<numSamples; i++)
				if (selected[i])
					trace += centeredKernel(i, i);
			this.kernelTrace = trace;
		}

		private double kernelMeanForBin(int bin) {
			for (int i=0; i<numSamples; i++)
				if (selected[i] && fractileBins[i] == bin)
					return kernelRowMeans[i];
			return 0d;
		}

		private double rawKernel(int sample1, int sample2) {
			if (fractile)
				return rawFractileKernel(fractileBins[sample1], fractileBins[sample2]);
			return categories[sample1] == categories[sample2] ? 1d : 0d;
		}

		@Override
		boolean matches(int sample1, int sample2) {
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
				double value = fractiles[sample1];
				fractiles[sample1] = fractiles[sample2];
				fractiles[sample2] = value;
				short bin = fractileBins[sample1];
				fractileBins[sample1] = fractileBins[sample2];
				fractileBins[sample2] = bin;
			} else {
				int value = categories[sample1];
				categories[sample1] = categories[sample2];
				categories[sample2] = value;
			}
			double value = kernelRowMeans[sample1];
			kernelRowMeans[sample1] = kernelRowMeans[sample2];
			kernelRowMeans[sample2] = value;
		}

		@Override
		double centeredKernel(int sample1, int sample2) {
			if (!selected[sample1] || !selected[sample2])
				return 0d;
			if (fractile)
				return centeredFractileKernels[fractileBins[sample1]][fractileBins[sample2]];
			return rawKernel(sample1, sample2) - kernelRowMeans[sample1]
					- kernelRowMeans[sample2] + kernelGrandMean;
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
		 * Calculates, but does not apply, the raw score change for a proposed swap.
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
	 * squared differences between observed and independent-expected contingency-table counts. Its normalization is the
	 * exact expected raw score over random permutations with both sampled margins fixed, calculated by summing the
	 * hypergeometric variance of every table cell.
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
			for (int i=0; i<level1.numCategories; i++)
				for (int j=0; j<level2.numCategories; j++)
					expectations[i][j] = (double)level1.marginalCounts[i]*level2.marginalCounts[j]/level1.numSamples;
			rawScore = rebuildCountsAndScore();
		}

		private static double expectedRandomL2(CategoricalLevelData level1, CategoricalLevelData level2) {
			// Sum the exact hypergeometric variance of every contingency-table cell while
			// holding both sampled marginals fixed.
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
			// Only four cells can change. For residual r and integer change d,
			// (r+d)^2-r^2 = 2*r*d+d^2.
			double delta = 0d;
			for (int i=0; i<numSwapChanges; i++) {
				double residual = counts[swapRows[i]][swapColumns[i]]
						- expectations[swapRows[i]][swapColumns[i]];
				delta += 2d*residual*swapDeltas[i] + swapDeltas[i]*swapDeltas[i];
			}
			return delta;
		}

		private void addSwapChange(int row, int column, int delta) {
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
	 * The raw score is the scaled Frobenius inner product of the two centered kernel matrices. Its random-pairing
	 * expectation is {@code trace(K)*trace(L)/(N^2*(N-1))}; dividing by that value gives a random baseline of {@code 1},
	 * consistent with {@link CategoricalPairCriterion}.
	 */
	static final class KernelPairCriterion extends PairCriterion {
		final int numSamples;

		KernelPairCriterion(LevelData level1, LevelData level2) {
			super(level1, level2, expectedRandomL2(level1, level2));
			this.numSamples = level1.numSamples;
			rawScore = calculateRawScore();
		}

		private static double expectedRandomL2(LevelData level1, LevelData level2) {
			double n = level1.numSamples;
			// For independently permuted centered Gram matrices K and L,
			// E[sum(K .* L)] = trace(K)*trace(L)/(N-1).
			return level1.kernelTrace()*level2.kernelTrace()/(n*n*(n-1d));
		}

		@Override
		double calculateSwapDelta(int sample1, int sample2, boolean[] swappedLevels) {
			boolean swapLevel1 = swappedLevels[level1.levelIndex];
			LevelData swapped = swapLevel1 ? level1 : level2;
			LevelData fixed = swapLevel1 ? level2 : level1;
			return kernelDelta(swapped, fixed, sample1, sample2)/((double)numSamples*numSamples);
		}

		private static double kernelDelta(LevelData swapped, LevelData fixed, int sample1, int sample2) {
			// Swapping two rows/columns of one Gram matrix changes only terms touching
			// those samples. Symmetry combines the off-diagonal terms into this O(N) sum.
			double delta = (swapped.centeredKernel(sample2, sample2)-swapped.centeredKernel(sample1, sample1))
					*(fixed.centeredKernel(sample1, sample1)-fixed.centeredKernel(sample2, sample2));
			for (int sample=0; sample<swapped.numSamples; sample++) {
				if (sample == sample1 || sample == sample2)
					continue;
				double swappedDifference = swapped.centeredKernel(sample2, sample)
						- swapped.centeredKernel(sample1, sample);
				double fixedDifference = fixed.centeredKernel(sample1, sample)
						- fixed.centeredKernel(sample2, sample);
				delta += 2d*swappedDifference*fixedDifference;
			}
			return delta;
		}

		@Override
		void applySwap(double rawDelta) {
			rawScore += rawDelta;
		}

		private double calculateRawScore() {
			// Frobenius inner product of the centered kernels, scaled by N^2. For the CDF
			// kernel this is the exact L2 integral after fractile quantization.
			double numerator = 0d;
			for (int i=0; i<numSamples; i++) {
				numerator += level1.centeredKernel(i, i)*level2.centeredKernel(i, i);
				for (int j=i+1; j<numSamples; j++)
					numerator += 2d*level1.centeredKernel(i, j)*level2.centeredKernel(i, j);
			}
			return numerator/((double)numSamples*numSamples);
		}

		@Override
		double recalculateRawScore() {
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
	 * {@link PairCriterion} instance for weighted scoring of fractile data within a combined level. In that case,
	 * we must score both the number of pairwise samples that landed in the fractile bin, as well as the distribution
	 * of those pairwise fractile samples. We average scores for both criteria.
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
			// Taking the Cartesian product also defines combined-versus-combined scoring;
			// component weights multiply and still sum to one.
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
			// Split a combined level 50/50 between selector balance and within-selector
			// balance. Conditional sublevels share their half according to sampled prevalence.
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
		if (level1 instanceof CategoricalLevelData && level2 instanceof CategoricalLevelData)
			return new CategoricalPairCriterion((CategoricalLevelData)level1, (CategoricalLevelData)level2);
		return new KernelPairCriterion(level1, level2);
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
					PairCriterion criterion = level1 instanceof CombinedLevelData || level2 instanceof CombinedLevelData
							? new CompositePairCriterion(level1, level2) : buildSimpleCriterion(level1, level2);
					criteria.add(criterion);
				}
			}
			this.swappedLevels = new boolean[levels.size()];
			this.rawSwapDeltas = new double[criteria.size()];
			this.affectedCriteria = new boolean[criteria.size()];
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
			// Evaluation is transactional: criteria save their prospective deltas, but no
			// counts, scores, or level assignments change until applySwap().
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
			// Apply criterion state first because its pending changes were calculated from
			// the pre-swap level data; then move the shared level representations.
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
		 * Recalculates all criteria from the current assignments.
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

		/**
		 * Prints raw, random-expected, and normalized scores for each level pair.
		 * @param namedLevels original levels used to label each pair
		 */
		void printStats(List<? extends LogicTreeLevel<?>> namedLevels) {
			DecimalFormat scoreDF = new DecimalFormat("0.000000");
			System.out.println("Pairwise normalized scores (random expectation = 1 per pair):");
			for (PairCriterion criterion : criteria) {
				String name1 = namedLevels.get(criterion.level1.levelIndex).getName();
				String name2 = namedLevels.get(criterion.level2.levelIndex).getName();
				System.out.println("\t"+name1+" / "+name2+" ["+criterion.typeName()+"]: raw="
						+scoreDF.format(criterion.rawScore)+", random="
						+scoreDF.format(criterion.expectedRandomScore)+", normalized="
						+scoreDF.format(criterion.normalizedScore()));
			}
			System.out.println("Total normalized score: "+scoreDF.format(score()));
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
		// The selector answers which sublevel was chosen; each conditional below answers
		// how values are distributed given that particular choice.
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
		// Renormalize over varying conditionals only: constant or unsampled sublevels have
		// no pairwise dependence to optimize and should not consume conditional weight.
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

	private static double rawFractileKernel(int bin1, int bin2) {
		// Integral from 0 to 1 of I(u<=t)*I(v<=t) dt, using bin midpoints for u and v.
		return 1d-(Math.max(bin1, bin2)+0.5d)/FRACTILE_BINS;
	}
}
