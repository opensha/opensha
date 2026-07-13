package org.opensha.commons.logicTree.lhs;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.numbers.core.Precision;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

/**
 * Pairwise optimizer for index-by-index combinations of multiple already-sampled logic trees. It leaves the first
 * tree's branch order fixed and swaps whole branches in later trees to improve pairwise balance between levels in
 * different trees.
 */
public class PairwiseLogicTreeBranchOrderIteration<E extends LogicTreeNode> {
	
	private static final double WEIGHT_TOL = 1e-8;
	
	private final List<LogicTree<E>> trees;
	private final int numSamples;
	private final List<LogicTreeLevel<? extends LogicTreeNode>> pairwiseLevels;
	private final List<double[]> levelWeights;
	private final List<int[]> treeLevelIndexes;
	private final List<int[]> branchIndexes;
	private final int[] levelTreeIndexes;
	private final int[] levelTreeLevelIndexes;
	private final boolean[][] includePairs;
	private final List<int[]> branchNodeIndexes;
	private final List<Integer> movableTreeIndexes;
	private final int numPairsToIterate;
	
	private double initialMisfit = Double.NaN;
	private double finalMisfit = Double.NaN;
	
	@SuppressWarnings("unchecked")
	public PairwiseLogicTreeBranchOrderIteration(List<LogicTree<E>> trees) {
		Preconditions.checkState(trees.size() > 1, "Must supply at least 2 logic trees");
		this.trees = trees;
		this.numSamples = validateTrees(trees);
		
		pairwiseLevels = new ArrayList<>();
		levelWeights = new ArrayList<>();
		treeLevelIndexes = new ArrayList<>(trees.size());
		branchIndexes = new ArrayList<>(trees.size());
		List<Integer> levelTreeIndexesList = new ArrayList<>();
		List<Integer> levelTreeLevelIndexesList = new ArrayList<>();
		
		for (int t=0; t<trees.size(); t++) {
			LogicTree<?> tree = trees.get(t);
			int[] myBranchIndexes = new int[numSamples];
			for (int i=0; i<numSamples; i++)
				myBranchIndexes[i] = i;
			branchIndexes.add(myBranchIndexes);
			
			int[] myLevelIndexes = new int[tree.getLevels().size()];
			treeLevelIndexes.add(myLevelIndexes);
			for (int l=0; l<tree.getLevels().size(); l++) {
				LogicTreeLevel<?> level = tree.getLevels().get(l);
				if (level instanceof BinnableLevel<?,?,?>) {
					level = ((BinnableLevel<?,?,?>)level).toBinnedLevel();
				} else if (level instanceof RandomLevel<?,?>) {
					myLevelIndexes[l] = -1;
					continue;
				}
				myLevelIndexes[l] = pairwiseLevels.size();
				pairwiseLevels.add((LogicTreeLevel<? extends LogicTreeNode>)level);
				levelTreeIndexesList.add(t);
				levelTreeLevelIndexesList.add(l);
			}
		}
		
		levelTreeIndexes = toArray(levelTreeIndexesList);
		levelTreeLevelIndexes = toArray(levelTreeLevelIndexesList);
		includePairs = buildIncludePairs(levelTreeIndexes);
		branchNodeIndexes = buildBranchNodeIndexesAndWeights();
		movableTreeIndexes = buildMovableTreeIndexes();
		numPairsToIterate = countPairsToIterate();
	}
	
	private static int validateTrees(List<? extends LogicTree<?>> trees) {
		int numSamples = trees.get(0).size();
		Preconditions.checkState(numSamples > 1, "Must have >1 branches");
		double weight = trees.get(0).getBranchWeight(0);
		Preconditions.checkState(Double.isFinite(weight), "Branch weight must be finite");
		for (int t=0; t<trees.size(); t++) {
			LogicTree<?> tree = trees.get(t);
			Preconditions.checkState(tree.size() == numSamples,
					"Tree %s has %s branches but tree 0 has %s", t, tree.size(), numSamples);
			for (int b=0; b<numSamples; b++) {
				double myWeight = tree.getBranchWeight(b);
				Preconditions.checkState(Precision.equals(weight, myWeight, WEIGHT_TOL),
						"Branch weights must be identical; tree %s branch %s has %s but expected %s",
						t, b, myWeight, weight);
			}
		}
		return numSamples;
	}
	
	private static int[] toArray(List<Integer> list) {
		int[] ret = new int[list.size()];
		for (int i=0; i<ret.length; i++)
			ret[i] = list.get(i);
		return ret;
	}
	
	private static boolean[][] buildIncludePairs(int[] levelTreeIndexes) {
		boolean[][] ret = new boolean[levelTreeIndexes.length][levelTreeIndexes.length];
		for (int i=0; i<ret.length; i++)
			for (int j=i+1; j<ret.length; j++)
				ret[i][j] = levelTreeIndexes[i] != levelTreeIndexes[j];
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private List<int[]> buildBranchNodeIndexesAndWeights() {
		List<int[]> ret = new ArrayList<>(numSamples);
		for (int b=0; b<numSamples; b++) {
			int[] indexes = new int[pairwiseLevels.size()];
			for (int i=0; i<indexes.length; i++)
				indexes[i] = -1;
			ret.add(indexes);
		}
		
		for (int levelIndex=0; levelIndex<pairwiseLevels.size(); levelIndex++) {
			int treeIndex = levelTreeIndexes[levelIndex];
			int treeLevelIndex = levelTreeLevelIndexes[levelIndex];
			LogicTree<?> tree = trees.get(treeIndex);
			LogicTreeLevel<?> pairwiseLevel = pairwiseLevels.get(levelIndex);
			BinnedLevel<?, ? extends LogicTreeNode> binnedLevel = null;
			if (tree.getLevels().get(treeLevelIndex) instanceof BinnableLevel<?,?,?>)
				binnedLevel = (BinnedLevel<?, ? extends LogicTreeNode>)pairwiseLevel;
			List<? extends LogicTreeNode> nodes = pairwiseLevel.getNodes();
			double[] weights = new double[nodes.size()];
			
			for (int b=0; b<numSamples; b++) {
				LogicTreeNode node = tree.getBranch(b).getValue(treeLevelIndex);
				if (binnedLevel != null) {
					node = binnedLevel.getBinUnchecked(node);
					Preconditions.checkNotNull(node);
				}
				int index = nodes.indexOf(node);
				Preconditions.checkState(index >= 0 && index < weights.length,
						"Bad index=%s for tree %s level %s node %s with %s weights",
						index, treeIndex, treeLevelIndex, node.getName(), weights.length);
				ret.get(b)[levelIndex] = index;
				weights[index]++;
			}
			
			int numNonZero = 0;
			for (double count : weights)
				if (count > 0d)
					numNonZero++;
			if (numNonZero <= 1) {
				levelWeights.add(null);
			} else {
				for (int i=0; i<weights.length; i++)
					weights[i] /= (double)numSamples;
				levelWeights.add(weights);
			}
		}
		
		return ret;
	}
	
	private List<Integer> buildMovableTreeIndexes() {
		List<Integer> ret = new ArrayList<>();
		for (int t=1; t<trees.size(); t++) {
			for (int levelIndex : treeLevelIndexes.get(t)) {
				if (levelIndex < 0)
					continue;
				if (levelWeights.get(levelIndex) != null) {
					ret.add(t);
					break;
				}
			}
		}
		return ret;
	}
	
	private int countPairsToIterate() {
		int ret = 0;
		for (int l1=0; l1<levelWeights.size(); l1++) {
			if (levelWeights.get(l1) == null)
				continue;
			for (int l2=l1+1; l2<levelWeights.size(); l2++)
				if (includePairs[l1][l2] && levelWeights.get(l2) != null)
					ret++;
		}
		return ret;
	}
	
	public void iterate(int numIterations, Random r, boolean verbose) {
		iterate(numIterations, PairwiseLogicTreeTools.OBJECTIVE_FUNCTION_DEFAULT, r, verbose);
	}
	
	public void iterate(int numIterations, PairwiseLogicTreeTools.ObjectiveFunction of, Random r, boolean verbose) {
		Preconditions.checkState(!movableTreeIndexes.isEmpty(),
				"No non-fixed tree has any level with multiple sampled choices");
		Preconditions.checkState(numPairsToIterate > 0,
				"No cross-tree level pairs have multiple sampled choices in both levels");
		
		PairwiseLogicTreeTools.PairwiseMisfits[][] misfits = PairwiseLogicTreeTools.calcPairwiseMisfits(
				branchNodeIndexes, levelWeights, includePairs, of);
		if (verbose) {
			System.out.println("===============================");
			System.out.println("Initial cross-tree misfits:");
			PairwiseLogicTreeTools.printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelWeights, includePairs);
			System.out.println("===============================");
		}
		double misfit = PairwiseLogicTreeTools.calcMisfitSum(misfits);
		initialMisfit = misfit;
		
		System.out.println("Pairwise iterating "+trees.size()+" logic trees with "+numSamples
				+" branches and "+numIterations+" iterations");
		
		DecimalFormat pDF = new DecimalFormat("0.00%");
		for (int n=0; n<numIterations; n++) {
			if (verbose && n % 1000 == 0) {
				System.out.println("Pairwise tree iteration "+n+"; misfit="+(float)misfit
						+"; reduction="+formatReduction(pDF, initialMisfit, misfit));
			}
			
			int branchIndex1 = r.nextInt(numSamples);
			int branchIndex2 = r.nextInt(numSamples);
			while (branchIndex1 == branchIndex2)
				branchIndex2 = r.nextInt(numSamples);
			
			int treeIndex = movableTreeIndexes.get(r.nextInt(movableTreeIndexes.size()));
			int[] levelIndexes = treeLevelIndexes.get(treeIndex);
			
			int[] branchIndexes1 = branchNodeIndexes.get(branchIndex1);
			int[] branchIndexes2 = branchNodeIndexes.get(branchIndex2);
			if (matchesAll(branchIndexes1, branchIndexes2, levelIndexes))
				continue;
			
			double deltaMisfit = PairwiseLogicTreeTools.swap(misfits, branchIndexes1, branchIndexes2, levelIndexes);
			if (deltaMisfit > 0 || ((float)deltaMisfit == 0f && r.nextBoolean())) {
				PairwiseLogicTreeTools.swap(misfits, branchIndexes1, branchIndexes2, levelIndexes);
			} else {
				misfit += deltaMisfit;
				int[] myBranchIndexes = branchIndexes.get(treeIndex);
				int origIndex1 = myBranchIndexes[branchIndex1];
				myBranchIndexes[branchIndex1] = myBranchIndexes[branchIndex2];
				myBranchIndexes[branchIndex2] = origIndex1;
			}
		}
		
		misfits = PairwiseLogicTreeTools.calcPairwiseMisfits(branchNodeIndexes, levelWeights, includePairs, of);
		finalMisfit = PairwiseLogicTreeTools.calcMisfitSum(misfits);
		Preconditions.checkState(Precision.equals(misfit, finalMisfit, 1e-3),
				"Misfit drift! Calculated final=%s, iterated=%s, diff=%s", finalMisfit, misfit, misfit-finalMisfit);
		if (verbose)
			System.out.println("===============================");
		System.out.println("Final misfit after "+numIterations+" iterations: "+(float)finalMisfit
				+"; reduction="+formatReduction(pDF, initialMisfit, finalMisfit));
		if (verbose) {
			PairwiseLogicTreeTools.printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelWeights, includePairs);
			System.out.println("Misfits for each possible objective function (we used "+of.name()+"):");
			for (PairwiseLogicTreeTools.ObjectiveFunction of2 : PairwiseLogicTreeTools.ObjectiveFunction.values())
				System.out.println("\t"+of2.name()+":\t"
						+(float)PairwiseLogicTreeTools.calcMisfitSum(PairwiseLogicTreeTools.calcPairwiseMisfits(
								branchNodeIndexes, levelWeights, includePairs, of2)));
			System.out.println("===============================");
		}
	}
	
	private static boolean matchesAll(int[] branchIndexes1, int[] branchIndexes2, int[] levelIndexes) {
		for (int levelIndex : levelIndexes) {
			if (levelIndex < 0)
				continue;
			if (branchIndexes1[levelIndex] != branchIndexes2[levelIndex])
				return false;
		}
		return true;
	}
	
	private static String formatReduction(DecimalFormat pDF, double initialMisfit, double misfit) {
		if (initialMisfit == 0d)
			return pDF.format(0d);
		return pDF.format((initialMisfit-misfit)/initialMisfit);
	}
	
	public double getInitialMisfit() {
		Preconditions.checkState(Double.isFinite(initialMisfit), "iterate must be called first");
		return initialMisfit;
	}
	
	public double getFinalMisfit() {
		Preconditions.checkState(Double.isFinite(finalMisfit), "iterate must be called first");
		return finalMisfit;
	}
	
	public List<int[]> getBranchIndexes() {
		List<int[]> ret = new ArrayList<>(branchIndexes.size());
		for (int[] indexes : branchIndexes)
			ret.add(indexes.clone());
		return ret;
	}
	
	public int[] getBranchIndexes(int treeIndex) {
		return branchIndexes.get(treeIndex).clone();
	}
	
	public List<LogicTree<E>> getReorderedTrees() {
		List<LogicTree<E>> ret = new ArrayList<>(trees.size());
		ret.add(trees.get(0));
		for (int t=1; t<trees.size(); t++) {
			LogicTree<E> tree = trees.get(t);
			List<LogicTreeBranch<E>> reorderedBranches = new ArrayList<>(numSamples);
			int[] indexes = branchIndexes.get(t);
			for (int i=0; i<numSamples; i++)
				reorderedBranches.add(tree.getBranch(indexes[i]));
			ret.add(buildTree(tree, reorderedBranches));
		}
		return ret;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private LogicTree<E> buildTree(LogicTree<E> tree, List<LogicTreeBranch<E>> reorderedBranches) {
		LogicTree ret = LogicTree.fromExisting((List)tree.getLevels(), (List)reorderedBranches);
		BranchWeightProvider weightProvider = tree.getWeightProvider();
		ret.setWeightProvider(weightProvider);
		ret.setSamplingParameters(tree.getSamplingRandomSeed(), tree.getSamplingOrigNumBranches(), tree.getSamplingMethod());
		return ret;
	}

}
