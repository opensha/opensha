package org.opensha.commons.logicTree.lhs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.CategoricalLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.CategoricalPairCriterion;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.CombinedLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.ConditionalLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.FractileLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.KernelPairCriterion;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.LevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.PairwiseScorer;

public class PairwiseLogicTreeToolsTest {

	private static final double TOL = 1e-10;

	@Test
	public void testCategoricalRandomExpectation() {
		int[] values1 = { 0, 0, 1, 1, 1 };
		int[] values2 = { 0, 0, 0, 1, 1 };
		CategoricalLevelData level1 = new CategoricalLevelData(0, 2, values1.clone());
		CategoricalLevelData level2 = new CategoricalLevelData(1, 2, values2.clone());
		CategoricalPairCriterion criterion = new CategoricalPairCriterion(level1, level2);

		double permutationMean = meanCategoricalScoreOverPermutations(values1, values2);
		assertEquals(permutationMean, criterion.expectedRandomScore, TOL);

		values1 = new int[] { 0, 0, 0, 1, 1, 1 };
		values2 = new int[] { 0, 1, 2, 0, 1, 2 };
		level1 = new CategoricalLevelData(0, 2, values1.clone());
		level2 = new CategoricalLevelData(1, 3, values2.clone());
		criterion = new CategoricalPairCriterion(level1, level2);
		assertEquals(meanCategoricalScoreOverPermutations(values1, values2),
				criterion.expectedRandomScore, TOL);
	}

	@Test
	public void testKernelRandomExpectations() {
		double[] fractiles1 = { 0.05, 0.22, 0.47, 0.71, 0.93 };
		double[] fractiles2 = { 0.11, 0.31, 0.52, 0.67, 0.86 };
		FractileLevelData level1 = new FractileLevelData(0, fractiles1.clone());
		FractileLevelData level2 = new FractileLevelData(1, fractiles2.clone());
		KernelPairCriterion criterion = new KernelPairCriterion(level1, level2);
		assertEquals(meanKernelScoreOverPermutations(level1, fractiles2),
				criterion.expectedRandomScore, TOL);

		CategoricalLevelData categorical = new CategoricalLevelData(0, 3, new int[] { 0, 0, 1, 1, 2 });
		FractileLevelData fractile = new FractileLevelData(1, fractiles2.clone());
		criterion = new KernelPairCriterion(categorical, fractile);
		assertEquals(meanKernelScoreOverPermutations(categorical, fractiles2),
				criterion.expectedRandomScore, TOL);
	}

	@Test
	public void testIncrementalSwapsMatchRecalculation() {
		List<LevelData> levels = new ArrayList<>();
		levels.add(new CategoricalLevelData(0, 3, new int[] { 0, 0, 1, 1, 2, 2, 0, 1 }));
		levels.add(new FractileLevelData(1,
				new double[] { 0.04, 0.16, 0.28, 0.41, 0.57, 0.69, 0.82, 0.95 }));
		levels.add(new FractileLevelData(2,
				new double[] { 0.91, 0.32, 0.73, 0.08, 0.48, 0.84, 0.19, 0.61 }));
		PairwiseScorer scorer = new PairwiseScorer(levels);

		double initial = scorer.score();
		double delta = scorer.evaluateSwap(1, 6, 1);
		assertEquals(initial, scorer.score(), TOL);
		scorer.applySwap();
		assertEquals(initial+delta, scorer.score(), TOL);
		assertEquals(scorer.score(), scorer.recalculateScore(), TOL);

		double reverseDelta = scorer.evaluateSwap(1, 6, 1);
		scorer.applySwap();
		assertEquals(initial, scorer.score(), TOL);
		assertEquals(-delta, reverseDelta, TOL);

		delta = scorer.evaluateSwap(2, 7, 0);
		scorer.applySwap();
		assertEquals(initial+delta, scorer.score(), TOL);
		assertEquals(scorer.score(), scorer.recalculateScore(), TOL);

		double unchanged = scorer.score();
		scorer.evaluateSwap(0, 5, 2);
		scorer.discardSwap();
		assertEquals(unchanged, scorer.score(), TOL);
	}

	@Test
	public void testCombinedHierarchicalWeightingAndSwaps() {
		int[] selectors = { 0, 0, 0, 0, 1, 1, 1, 1 };
		boolean[] selected = { false, false, false, false, true, true, true, true };
		double[] conditionalValues = { 0d, 0d, 0d, 0d, 0.12, 0.37, 0.63, 0.88 };
		CategoricalLevelData selector = new CategoricalLevelData(0, 2, selectors.clone());
		ConditionalLevelData conditional = new ConditionalLevelData(0, selected.clone(), conditionalValues.clone());
		CombinedLevelData combined = new CombinedLevelData(0, selector, List.of(conditional),
				new double[] { 1d }, new int[] { 0, 0, 0, 0, 0, 1, 2, 3 });
		CategoricalLevelData other = new CategoricalLevelData(1, 2, new int[] { 0, 1, 0, 1, 0, 0, 1, 1 });

		double selectorScore = new CategoricalPairCriterion(selector, other).normalizedScore();
		double conditionalScore = new KernelPairCriterion(conditional, other).normalizedScore();
		PairwiseScorer scorer = new PairwiseScorer(List.of(combined, other));
		assertEquals(0.5d*(selectorScore+conditionalScore), scorer.score(), TOL);

		double initial = scorer.score();
		double delta = scorer.evaluateSwap(1, 6, 0);
		scorer.applySwap();
		assertEquals(initial+delta, scorer.score(), TOL);
		assertEquals(scorer.score(), scorer.recalculateScore(), TOL);
	}

	@Test
	public void testZeroScoreDeltaStillAppliesCategoricalCounts() {
		List<LevelData> levels = new ArrayList<>();
		levels.add(new CategoricalLevelData(0, 2, new int[] { 0, 0, 0, 1, 1, 1 }));
		levels.add(new CategoricalLevelData(1, 2, new int[] { 0, 1, 1, 0, 0, 1 }));
		PairwiseScorer scorer = new PairwiseScorer(levels);
		double initial = scorer.score();

		double delta = scorer.evaluateSwap(1, 3, 1);
		assertEquals(0d, delta, TOL);
		scorer.applySwap();
		assertEquals(initial, scorer.score(), TOL);
		assertEquals(initial, scorer.recalculateScore(), TOL);
	}

	@Test
	public void testAlignedFractilesAreWorseThanRandom() {
		double[] fractiles = new double[20];
		for (int i=0; i<fractiles.length; i++)
			fractiles[i] = (i+0.5)/fractiles.length;
		KernelPairCriterion criterion = new KernelPairCriterion(
				new FractileLevelData(0, fractiles.clone()), new FractileLevelData(1, fractiles.clone()));
		assertTrue(criterion.normalizedScore() > 2d);
	}

	@Test
	public void testKernelMatchesExactEmpiricalCDFIntegral() {
		double[] fractiles1 = { 0.08, 0.24, 0.51, 0.63, 0.88 };
		double[] fractiles2 = { 0.72, 0.13, 0.91, 0.39, 0.57 };
		KernelPairCriterion criterion = new KernelPairCriterion(
				new FractileLevelData(0, fractiles1.clone()),
				new FractileLevelData(1, fractiles2.clone()));
		assertEquals(exactEmpiricalCDFL2(quantize(fractiles1), quantize(fractiles2)), criterion.rawScore, 1e-9);

		int[] categories = { 0, 1, 0, 2, 1 };
		criterion = new KernelPairCriterion(
				new CategoricalLevelData(0, 3, categories.clone()),
				new FractileLevelData(1, fractiles2.clone()));
		assertEquals(exactCategoricalFractileCDFL2(categories, quantize(fractiles2)), criterion.rawScore, 1e-9);
	}

	private static double[] quantize(double[] values) {
		double[] ret = new double[values.length];
		for (int i=0; i<values.length; i++) {
			int bin = Math.min(PairwiseLogicTreeTools.FRACTILE_BINS-1,
					(int)(values[i]*PairwiseLogicTreeTools.FRACTILE_BINS));
			ret[i] = (bin+0.5d)/PairwiseLogicTreeTools.FRACTILE_BINS;
		}
		return ret;
	}

	private static double meanCategoricalScoreOverPermutations(int[] values1, int[] values2) {
		int[] permutation = identity(values2.length);
		double sum = 0d;
		int count = 0;
		do {
			int[] permuted = permute(values2, permutation);
			CategoricalPairCriterion criterion = new CategoricalPairCriterion(
					new CategoricalLevelData(0, numCategories(values1), values1.clone()),
					new CategoricalLevelData(1, numCategories(values2), permuted));
			sum += criterion.rawScore;
			count++;
		} while (nextPermutation(permutation));
		return sum/count;
	}

	private static int numCategories(int[] values) {
		int max = -1;
		for (int value : values)
			max = Math.max(max, value);
		return max+1;
	}

	private static double exactEmpiricalCDFL2(double[] values1, double[] values2) {
		double[] edges1 = cdfEdges(values1);
		double[] edges2 = cdfEdges(values2);
		double integral = 0d;
		for (int i=0; i<edges1.length-1; i++) {
			double width1 = edges1[i+1]-edges1[i];
			if (width1 == 0d)
				continue;
			double threshold1 = 0.5*(edges1[i]+edges1[i+1]);
			for (int j=0; j<edges2.length-1; j++) {
				double width2 = edges2[j+1]-edges2[j];
				if (width2 == 0d)
					continue;
				double threshold2 = 0.5*(edges2[j]+edges2[j+1]);
				int count1 = 0;
				int count2 = 0;
				int jointCount = 0;
				for (int sample=0; sample<values1.length; sample++) {
					boolean below1 = values1[sample] <= threshold1;
					boolean below2 = values2[sample] <= threshold2;
					if (below1)
						count1++;
					if (below2)
						count2++;
					if (below1 && below2)
						jointCount++;
				}
				double n = values1.length;
				double residual = jointCount/n - (count1/n)*(count2/n);
				integral += width1*width2*residual*residual;
			}
		}
		return integral;
	}

	private static double exactCategoricalFractileCDFL2(int[] categories, double[] fractiles) {
		double[] edges = cdfEdges(fractiles);
		int numCategories = numCategories(categories);
		int[] categoryCounts = new int[numCategories];
		for (int category : categories)
			categoryCounts[category]++;
		double integral = 0d;
		for (int i=0; i<edges.length-1; i++) {
			double width = edges[i+1]-edges[i];
			if (width == 0d)
				continue;
			double threshold = 0.5*(edges[i]+edges[i+1]);
			int marginalCount = 0;
			int[] jointCounts = new int[numCategories];
			for (int sample=0; sample<fractiles.length; sample++) {
				if (fractiles[sample] <= threshold) {
					marginalCount++;
					jointCounts[categories[sample]]++;
				}
			}
			double n = fractiles.length;
			for (int category=0; category<numCategories; category++) {
				double residual = jointCounts[category]/n
						- (categoryCounts[category]/n)*(marginalCount/n);
				integral += width*residual*residual;
			}
		}
		return integral;
	}

	private static double[] cdfEdges(double[] values) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		double[] ret = new double[sorted.length+2];
		ret[0] = 0d;
		System.arraycopy(sorted, 0, ret, 1, sorted.length);
		ret[ret.length-1] = 1d;
		return ret;
	}

	private static double meanKernelScoreOverPermutations(LevelData fixed, double[] values) {
		int[] permutation = identity(values.length);
		double sum = 0d;
		int count = 0;
		do {
			KernelPairCriterion criterion = new KernelPairCriterion(copy(fixed),
					new FractileLevelData(1, permute(values, permutation)));
			sum += criterion.rawScore;
			count++;
		} while (nextPermutation(permutation));
		return sum/count;
	}

	private static LevelData copy(LevelData level) {
		if (level instanceof CategoricalLevelData) {
			CategoricalLevelData categorical = (CategoricalLevelData)level;
			return new CategoricalLevelData(0, categorical.numCategories, categorical.values.clone());
		}
		FractileLevelData fractile = (FractileLevelData)level;
		return new FractileLevelData(0, fractile.fractiles.clone());
	}

	private static int[] identity(int size) {
		int[] permutation = new int[size];
		for (int i=0; i<size; i++)
			permutation[i] = i;
		return permutation;
	}

	private static int[] permute(int[] values, int[] permutation) {
		int[] ret = new int[values.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = values[permutation[i]];
		return ret;
	}

	private static double[] permute(double[] values, int[] permutation) {
		double[] ret = new double[values.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = values[permutation[i]];
		return ret;
	}

	private static boolean nextPermutation(int[] values) {
		int i = values.length-2;
		while (i >= 0 && values[i] >= values[i+1])
			i--;
		if (i < 0)
			return false;
		int j = values.length-1;
		while (values[j] <= values[i])
			j--;
		int value = values[i];
		values[i] = values[j];
		values[j] = value;
		for (int left=i+1, right=values.length-1; left<right; left++, right--) {
			value = values[left];
			values[left] = values[right];
			values[right] = value;
		}
		return true;
	}
}
