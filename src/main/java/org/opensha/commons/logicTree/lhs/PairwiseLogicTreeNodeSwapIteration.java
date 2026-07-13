package org.opensha.commons.logicTree.lhs;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.numbers.core.Precision;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;

import com.google.common.base.Preconditions;

/**
 * Pairwise optimizer for logic tree node combinations across a sampled logic tree.
 * @param <E>
 */
public class PairwiseLogicTreeNodeSwapIteration<E extends LogicTreeNode> {
	
	private List<LogicTreeLevel<? extends E>> levels;
	private List<LogicTreeBranch<E>> branches;
	private List<double[]> levelFixedWeights;
	private int numLevelsToIterate;
	private List<LogicTreeLevel<?>> pairwiseLevels;
	private List<Integer> binnedLevelIndexes;
	
	private boolean trackSwaps = false;
	private List<int[]> originalBranchIndexes;
	
	@SuppressWarnings("unchecked")
	public PairwiseLogicTreeNodeSwapIteration(List<LogicTreeLevel<? extends E>> levels, List<LogicTreeBranch<E>> branches,
			List<double[]> levelFixedWeights) {
		Preconditions.checkState(levelFixedWeights.size() == levels.size());
		Preconditions.checkState(levels.size() > 1);
		Preconditions.checkState(branches.size() > 1);
		this.levels = levels;
		this.branches = branches;
		this.levelFixedWeights = levelFixedWeights;
		
		pairwiseLevels = new ArrayList<>(levels);
		numLevelsToIterate = 0;
		binnedLevelIndexes = new ArrayList<>(levels.size());
		for (int l=0; l<levels.size(); l++) {
			LogicTreeLevel<? extends E> level = levels.get(l);
			double[] weights = levelFixedWeights.get(l);
			if (weights != null) {
				double sum = 0d;
				int numNonZero = 0;
				for (double weight : weights) {
					if (weight > 0)
						numNonZero++;
					sum += weight;
				}
				if (numNonZero == 1) {
					levelFixedWeights.set(l, null);
					continue;
				}
				if ((float)sum != 1f) {
					for (int i=0; i<weights.length; i++)
						weights[i] /= sum;
				}
				numLevelsToIterate++;
			} else if (level instanceof BinnableLevel<?,?,?>) {
				LogicTreeLevel<? extends LogicTreeNode> binnedLevel =
						(LogicTreeLevel<? extends LogicTreeNode>)((BinnableLevel<?,?,?>)level).toBinnedLevel();
				binnedLevelIndexes.add(l);
				List<? extends LogicTreeNode> binnedNodes = binnedLevel.getNodes();
				weights = new double[binnedNodes.size()];
				double sum = 0d;
				int numNonZero = 0;
				for (int n=0; n<binnedNodes.size(); n++) {
					weights[n] = binnedNodes.get(n).getNodeWeight(null);
					sum += weights[n];
					if (weights[n] > 0)
						numNonZero++;
				}
				if (numNonZero == 1)
					continue;
				if ((float)sum != 1f) {
					for (int i=0; i<weights.length; i++)
						weights[i] /= sum;
				}
				levelFixedWeights.set(l, weights);
				pairwiseLevels.set(l, binnedLevel);
				numLevelsToIterate++;
			}
		}
	}
	
	public void iterate(int numIterations, Random r, boolean verbose) {
		iterate(numIterations, PairwiseLogicTreeTools.OBJECTIVE_FUNCTION_DEFAULT, r, verbose);
	}
	
	public void iterate(int numIterations, PairwiseLogicTreeTools.ObjectiveFunction of, Random r, boolean verbose) {
		if (numLevelsToIterate < 2) {
			System.err.println("WARNING: won't pairwise-iterate LHS sampling because we only have "+numLevelsToIterate+" candidate level");
			return;
		}
		
		int numSamples = branches.size();
		
		System.out.println("Pairwise iterating "+numSamples+" LHS samples with "+numIterations+" iterations");
		
		// remap each branch instead onto a set of indexes of their (possibly-binned) values
		List<int[]> branchNodeIndexes = new ArrayList<>(numSamples);
		if (trackSwaps)
			originalBranchIndexes = new ArrayList<>(numSamples);
		for (int b=0; b<numSamples; b++) {
			int[] indexes = new int[levels.size()];
			for (int i=0; i<indexes.length; i++)
				indexes[i] = -1;
			branchNodeIndexes.add(indexes);
			if (trackSwaps) {
				int[] branchIndexes = new int[levels.size()];
				for (int i=0; i<branchIndexes.length; i++)
					branchIndexes[i] = b;
				originalBranchIndexes.add(branchIndexes);
			}
		}
		for (int l=0; l<levels.size(); l++) {
			double[] weights = levelFixedWeights.get(l);
			if (weights == null)
				continue;
			LogicTreeLevel<?> level = pairwiseLevels.get(l);
			BinnedLevel<?, ? extends LogicTreeNode> binnedLevel = null;
			if (binnedLevelIndexes.contains(l)) {
				binnedLevel = (BinnedLevel<?, ? extends LogicTreeNode>)level;
			}
			List<? extends LogicTreeNode> nodes = pairwiseLevels.get(l).getNodes();
			Preconditions.checkState(nodes.size() == weights.length);
			for (int b=0; b<numSamples; b++) {
				LogicTreeNode node = branches.get(b).getValue(l);
				if (binnedLevel != null) {
					node = binnedLevel.getBinUnchecked(node);
					Preconditions.checkNotNull(node);
				}
				int index = nodes.indexOf(node);
				Preconditions.checkState(index >= 0 && index < weights.length, "bad index=%s for %s with %s weights",
						index, node.getName(), weights.length);
				branchNodeIndexes.get(b)[l] = index;
			}
		}
		
		
		PairwiseLogicTreeTools.PairwiseMisfits[][] misfits = PairwiseLogicTreeTools.calcPairwiseMisfits(branchNodeIndexes, levelFixedWeights, of);
		if (verbose) {
			System.out.println("===============================");
			System.out.println("Initial misfits:");
			PairwiseLogicTreeTools.printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelFixedWeights);
			System.out.println("===============================");
		}
		double misfit = PairwiseLogicTreeTools.calcMisfitSum(misfits);
		double misfit0 = misfit;
		DecimalFormat pDF = new DecimalFormat("0.00%");
		for (int n=0; n<numIterations; n++) {
			if (verbose && n % 1000 == 0) {
				System.out.println("Pairwise misfit iteration "+n+"; misfit="+(float)misfit+"; reduction="+pDF.format((misfit0-misfit)/misfit0));
			}
			// branch indexes for which we will (possibly) swap a level
			int branchIndex1 = r.nextInt(numSamples);
			int branchIndex2 = r.nextInt(numSamples);
			while (branchIndex1 == branchIndex2)
				branchIndex2 = r.nextInt(numSamples);
			
			// level index which we will (possibly) swap values
			int levelIndex = r.nextInt(levels.size());
			while (levelFixedWeights.get(levelIndex) == null)
				levelIndex = r.nextInt(levels.size());
			
			int[] branchIndexes1 = branchNodeIndexes.get(branchIndex1);
			int[] branchIndexes2 = branchNodeIndexes.get(branchIndex2);
			if (branchIndexes1[levelIndex] == branchIndexes2[levelIndex])
				// already the same, no swap possible
				continue;
			
			// do the swap
			double deltaMisfit = PairwiseLogicTreeTools.swap(misfits, branchIndexes1, branchIndexes2, levelIndex);
			if (deltaMisfit > 0 || ((float)deltaMisfit == 0f && r.nextBoolean())) {
				// we made things worse, reverse it
				PairwiseLogicTreeTools.swap(misfits, branchIndexes1, branchIndexes2, levelIndex);
			} else {
				// we improved things, keep it
				misfit += deltaMisfit;
				// apply the swap to the actual branch values
				E value1 = branches.get(branchIndex1).getValue(levelIndex);
				E value2 = branches.get(branchIndex2).getValue(levelIndex);
				branches.get(branchIndex1).setValue(levelIndex, value2);
				branches.get(branchIndex2).setValue(levelIndex, value1);
				if (trackSwaps) {
					int prevIndex1 = originalBranchIndexes.get(branchIndex1)[levelIndex];
					int prevIndex2 = originalBranchIndexes.get(branchIndex2)[levelIndex];
					originalBranchIndexes.get(branchIndex1)[levelIndex] = prevIndex2;
					originalBranchIndexes.get(branchIndex2)[levelIndex] = prevIndex1;
				}
			}
		}
		// recalculate it to check against any drift
		misfits = PairwiseLogicTreeTools.calcPairwiseMisfits(branchNodeIndexes, levelFixedWeights, of);
		double finalMisfit = PairwiseLogicTreeTools.calcMisfitSum(misfits);
		Preconditions.checkState(Precision.equals(misfit, finalMisfit, 1e-3),
				"Misfit drift! Calculated final=%s, iterated=%s, diff=%s", finalMisfit, misfit, misfit-finalMisfit);
		if (verbose)
			System.out.println("===============================");
		System.out.println("Final misfit after "+numIterations+" iterations: "+(float)finalMisfit+"; reduction="+pDF.format((misfit0-finalMisfit)/misfit0));
		if (verbose) {
			PairwiseLogicTreeTools.printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelFixedWeights);
			System.out.println("Misfits for each possible objective function (we used "+of.name()+"):");
			for (PairwiseLogicTreeTools.ObjectiveFunction of2 : PairwiseLogicTreeTools.ObjectiveFunction.values())
				System.out.println("\t"+of2.name()+":\t"
						+(float)PairwiseLogicTreeTools.calcMisfitSum(PairwiseLogicTreeTools.calcPairwiseMisfits(
								branchNodeIndexes, levelFixedWeights, of2)));
			System.out.println("===============================");
		}
	}
	
	public void setTrackSwaps(boolean trackSwaps) {
		this.trackSwaps = trackSwaps;
		this.originalBranchIndexes = null;
	}
	
	public List<int[]> getOriginalBranchIndexes() {
		Preconditions.checkNotNull(originalBranchIndexes, "trackSwaps must be true and set before iterate");
		return originalBranchIndexes;
	}
	
}
