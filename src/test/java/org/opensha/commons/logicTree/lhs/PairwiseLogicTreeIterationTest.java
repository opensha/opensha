package org.opensha.commons.logicTree.lhs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FileBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.SamplingMethod;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_InterfaceHingedBValue.CombinedSamplingLevel;

public class PairwiseLogicTreeIterationTest {

	@Test(timeout=30000)
	public void testNodeSwapWithFractileLevels() {
		int numSamples = 80;
		ContinuousDistributionSampledLevel continuous1 = continuousLevel("Continuous 1", "C1");
		ContinuousDistributionSampledLevel continuous2 = continuousLevel("Continuous 2", "C2");
		FileBackedNode option1 = new FileBackedNode("Option 1", "O1", 0.5, "o1");
		FileBackedNode option2 = new FileBackedNode("Option 2", "O2", 0.5, "o2");
		FileBackedLevel categorical = new FileBackedLevel("Categorical", "Cat", List.of(option1, option2));

		List<LogicTreeLevel<? extends LogicTreeNode>> levels = List.of(continuous1, continuous2, categorical);
		LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(levels, numSamples, 1234L,
				SamplingMethod.LATIN_HYPERCUBE);
		List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(tree.getBranches());
		double[][] beforeFractiles = { sortedValues(branches, 0), sortedValues(branches, 1) };
		List<double[]> fixedWeights = Arrays.asList(null, null, new double[] { 0.5, 0.5 });

		PairwiseLogicTreeNodeSwapIteration<LogicTreeNode> iteration =
				new PairwiseLogicTreeNodeSwapIteration<>(levels, branches, fixedWeights);
		iteration.iterate(2000, new Random(5678L), false);

		assertTrue(iteration.getFinalScore() <= iteration.getInitialScore());
		assertArrayEquals(beforeFractiles[0], sortedValues(branches, 0), 0d);
		assertArrayEquals(beforeFractiles[1], sortedValues(branches, 1), 0d);
	}

	@Test(timeout=30000)
	public void testBranchOrderWithFractileLevels() {
		int numSamples = 60;
		LogicTree<LogicTreeNode> tree1 = LogicTree.buildSampled(List.of(continuousLevel("Continuous 1", "C1")),
				numSamples, 12L, SamplingMethod.LATIN_HYPERCUBE);
		LogicTree<LogicTreeNode> tree2 = LogicTree.buildSampled(List.of(continuousLevel("Continuous 2", "C2")),
				numSamples, 34L, SamplingMethod.LATIN_HYPERCUBE);

		PairwiseLogicTreeBranchOrderIteration<LogicTreeNode> iteration =
				new PairwiseLogicTreeBranchOrderIteration<>(List.of(tree1, tree2));
		iteration.iterate(1500, new Random(56L), false);

		assertTrue(iteration.getFinalMisfit() <= iteration.getInitialMisfit());
		assertEquals(numSamples, iteration.getReorderedTrees().get(1).size());
	}

	@Test(timeout=30000)
	public void testNodeSwapWithCombinedFractileLevel() {
		int numSamples = 100;
		CombinedSamplingLevel combined = new CombinedSamplingLevel("Combined", "Comb", 0.35,
				UniformContinuousDistribution.of(0.4, 1.2), 0.65);
		ContinuousDistributionSampledLevel continuous = continuousLevel("Continuous", "C");
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = List.of(combined, continuous);
		LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(levels, numSamples, 2345L,
				SamplingMethod.LATIN_HYPERCUBE);
		List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(tree.getBranches());
		int[] selectorCounts = combinedSelectorCounts(branches, 0, combined.getSubLevels().size());

		PairwiseLogicTreeNodeSwapIteration<LogicTreeNode> iteration =
				new PairwiseLogicTreeNodeSwapIteration<>(levels, branches, Arrays.asList(null, null));
		iteration.iterate(2000, new Random(6789L), false);

		assertTrue(iteration.getFinalScore() <= iteration.getInitialScore());
		assertArrayEquals(selectorCounts, combinedSelectorCounts(branches, 0, combined.getSubLevels().size()));
	}

	private static ContinuousDistributionSampledLevel continuousLevel(String name, String shortName) {
		return new ContinuousDistributionSampledLevel(name, shortName,
				UniformContinuousDistribution.of(0d, 1d), name+" sample", shortName, shortName.toLowerCase());
	}

	private static double[] sortedValues(List<? extends LogicTreeBranch<?>> branches, int levelIndex) {
		double[] values = new double[branches.size()];
		for (int i=0; i<values.length; i++)
			values[i] = (Double)((ValuedLogicTreeNode<?>)branches.get(i).getValue(levelIndex)).getValue();
		Arrays.sort(values);
		return values;
	}

	private static int[] combinedSelectorCounts(List<? extends LogicTreeBranch<?>> branches, int levelIndex,
			int numSelectors) {
		int[] counts = new int[numSelectors];
		for (LogicTreeBranch<?> branch : branches) {
			int[] indexes = (int[])((ValuedLogicTreeNode<?>)branch.getValue(levelIndex)).getValue();
			counts[indexes[0]]++;
		}
		return counts;
	}
}
