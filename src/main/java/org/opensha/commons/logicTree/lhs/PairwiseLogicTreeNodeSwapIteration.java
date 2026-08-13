package org.opensha.commons.logicTree.lhs;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.numbers.core.Precision;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractCombinedSamplingLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.CategoricalLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.FractileLevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.LevelData;
import org.opensha.commons.logicTree.lhs.PairwiseLogicTreeTools.PairwiseScorer;

import com.google.common.base.Preconditions;

/**
 * Pairwise optimizer for logic tree node combinations across a sampled logic tree.
 * @param <E>
 */
public class PairwiseLogicTreeNodeSwapIteration<E extends LogicTreeNode> {

	private final List<LogicTreeLevel<? extends E>> levels;
	private final List<LogicTreeBranch<E>> branches;
	private final List<LevelData> levelData;
	private final List<Integer> movableLevelIndexes;
	private final PairwiseScorer scorer;

	private boolean trackSwaps = false;
	private List<int[]> originalBranchIndexes;
	private double initialScore = Double.NaN;
	private double finalScore = Double.NaN;

	public PairwiseLogicTreeNodeSwapIteration(List<LogicTreeLevel<? extends E>> levels,
			List<LogicTreeBranch<E>> branches, List<double[]> levelFixedWeights) {
		Preconditions.checkState(levelFixedWeights.size() == levels.size());
		Preconditions.checkState(levels.size() > 1);
		Preconditions.checkState(branches.size() > 1);
		this.levels = levels;
		this.branches = branches;
		this.levelData = buildLevelData(levelFixedWeights);
		this.movableLevelIndexes = new ArrayList<>();
		for (int l=0; l<levelData.size(); l++)
			if (levelData.get(l) != null)
				movableLevelIndexes.add(l);
		this.scorer = new PairwiseScorer(levelData);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<LevelData> buildLevelData(List<double[]> levelFixedWeights) {
		int numSamples = branches.size();
		List<LevelData> ret = new ArrayList<>(levels.size());
		for (int l=0; l<levels.size(); l++) {
			LogicTreeLevel<?> level = levels.get(l);
			if (level instanceof AbstractCombinedSamplingLevel<?,?>
					&& PairwiseLogicTreeTools.hasFractileSubLevel((AbstractCombinedSamplingLevel<?,?>)level)) {
				List<LogicTreeNode> sampledNodes = new ArrayList<>(numSamples);
				for (int b=0; b<numSamples; b++)
					sampledNodes.add(branches.get(b).getValue(l));
				ret.add(PairwiseLogicTreeTools.combinedLevelData(l,
						(AbstractCombinedSamplingLevel)level, sampledNodes));
				continue;
			}
			if (level instanceof FractileSamplingLevel<?,?>) {
				double[] fractiles = new double[numSamples];
				for (int b=0; b<numSamples; b++)
					fractiles[b] = PairwiseLogicTreeTools.fractile((FractileSamplingLevel)level,
							branches.get(b).getValue(l));
				ret.add(PairwiseLogicTreeTools.hasMultipleFractiles(fractiles)
						? new FractileLevelData(l, fractiles) : null);
				continue;
			}

			LogicTreeLevel<?> categoricalLevel = level;
			BinnedLevel<?, ? extends LogicTreeNode> binnedLevel = null;
			if (levelFixedWeights.get(l) == null) {
				if (!(level instanceof BinnableLevel<?,?,?>)) {
					ret.add(null);
					continue;
				}
				categoricalLevel = ((BinnableLevel<?,?,?>)level).toBinnedLevel();
				binnedLevel = (BinnedLevel<?, ? extends LogicTreeNode>)categoricalLevel;
			}

			List<? extends LogicTreeNode> nodes = categoricalLevel.getNodes();
			int[] values = new int[numSamples];
			for (int b=0; b<numSamples; b++) {
				LogicTreeNode node = branches.get(b).getValue(l);
				if (binnedLevel != null) {
					node = binnedLevel.getBinUnchecked(node);
					Preconditions.checkNotNull(node);
				}
				values[b] = nodes.indexOf(node);
				Preconditions.checkState(values[b] >= 0, "Node %s not found in level %s", node, level.getName());
			}
			ret.add(PairwiseLogicTreeTools.hasMultipleCategories(values)
					? new CategoricalLevelData(l, nodes.size(), values) : null);
		}
		return ret;
	}

	public void iterate(int numIterations, Random r, boolean verbose) {
		if (movableLevelIndexes.size() < 2 || scorer.size() == 0) {
			System.err.println("WARNING: won't pairwise-iterate LHS sampling because fewer than 2 levels vary");
			return;
		}

		int numSamples = branches.size();
		System.out.println("Pairwise iterating "+numSamples+" LHS samples with "+numIterations+" iterations");

		if (trackSwaps) {
			originalBranchIndexes = new ArrayList<>(numSamples);
			for (int b=0; b<numSamples; b++) {
				int[] indexes = new int[levels.size()];
				for (int l=0; l<indexes.length; l++)
					indexes[l] = b;
				originalBranchIndexes.add(indexes);
			}
		}

		if (verbose) {
			System.out.println("===============================");
			System.out.println("Initial misfits:");
			printStats();
			System.out.println("===============================");
		}
		double score = scorer.score();
		initialScore = score;
		DecimalFormat pDF = new DecimalFormat("0.00%");
		for (int n=0; n<numIterations; n++) {
			if (verbose && n % 1000 == 0)
				System.out.println("Pairwise misfit iteration "+n+"; score="+(float)score
						+"; avgScore="+(float)(score/scorer.size())
						+"; reduction="+formatReduction(pDF, initialScore, score));

			int branchIndex1 = r.nextInt(numSamples);
			int branchIndex2 = r.nextInt(numSamples);
			while (branchIndex1 == branchIndex2)
				branchIndex2 = r.nextInt(numSamples);

			int levelIndex = movableLevelIndexes.get(r.nextInt(movableLevelIndexes.size()));
			if (levelData.get(levelIndex).matches(branchIndex1, branchIndex2))
				continue;

			double deltaScore = scorer.evaluateSwap(branchIndex1, branchIndex2, levelIndex);
			if (deltaScore > 0d || ((float)deltaScore == 0f && r.nextBoolean())) {
				scorer.discardSwap();
			} else {
				scorer.applySwap();
				score += deltaScore;
				E value1 = branches.get(branchIndex1).getValue(levelIndex);
				E value2 = branches.get(branchIndex2).getValue(levelIndex);
				branches.get(branchIndex1).setValue(levelIndex, value2);
				branches.get(branchIndex2).setValue(levelIndex, value1);
				if (trackSwaps) {
					int previous = originalBranchIndexes.get(branchIndex1)[levelIndex];
					originalBranchIndexes.get(branchIndex1)[levelIndex] =
							originalBranchIndexes.get(branchIndex2)[levelIndex];
					originalBranchIndexes.get(branchIndex2)[levelIndex] = previous;
				}
			}
		}

		finalScore = scorer.recalculateScore();
		Preconditions.checkState(Precision.equals(score, finalScore, 1e-3),
				"Score drift! Calculated final=%s, iterated=%s, diff=%s", finalScore, score, score-finalScore);
		if (verbose)
			System.out.println("===============================");
		System.out.println("Final normalized score after "+numIterations+" iterations: "+(float)finalScore
				+"; avg="+(float)(finalScore/scorer.size())+"; reduction="+formatReduction(pDF, initialScore, finalScore));
		if (verbose) {
			printStats();
			System.out.println("===============================");
		}
	}

	private void printStats() {
		scorer.printStats(levels);
	}

	private static String formatReduction(DecimalFormat pDF, double initialScore, double score) {
		if (initialScore == 0d)
			return pDF.format(0d);
		return pDF.format((initialScore-score)/initialScore);
	}

	public void setTrackSwaps(boolean trackSwaps) {
		this.trackSwaps = trackSwaps;
		this.originalBranchIndexes = null;
	}

	public List<int[]> getOriginalBranchIndexes() {
		Preconditions.checkNotNull(originalBranchIndexes, "trackSwaps must be true and set before iterate");
		return originalBranchIndexes;
	}

	public double getInitialScore() {
		Preconditions.checkState(Double.isFinite(initialScore), "iterate must be called first");
		return initialScore;
	}

	public double getFinalScore() {
		Preconditions.checkState(Double.isFinite(finalScore), "iterate must be called first");
		return finalScore;
	}
}
