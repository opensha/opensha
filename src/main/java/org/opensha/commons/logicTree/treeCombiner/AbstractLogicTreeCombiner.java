package org.opensha.commons.logicTree.treeCombiner;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Class for creating combinations of logic trees (outer * inner), and doing efficient sampling therein.
 * 
 * Can also run processors (e.g., to combine hazard curves across different source trees) for each combined logic tree
 * branch via the {@link LogicTreeCombinationProcessor} interface and {@link AbstractLogicTreeCombiner#processCombinations()}. 
 */
public abstract class AbstractLogicTreeCombiner {
	
	// inputs for all
	private LogicTree<?> outerTree;
	private LogicTree<?> innerTree;
	
	// results and intermediates
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> outerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> outerNodeRemaps;
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> innerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> innerNodeRemaps;

	private List<LogicTreeLevel<? extends LogicTreeNode>> combLevels;
	private int expectedNum;
	private List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels;
	private List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels;
	private Map<LogicTreeBranch<LogicTreeNode>, LogicTree<?>> commonSubtrees;
	private List<LogicTreeBranch<LogicTreeNode>> combBranches;
	private List<Integer> combBranchesOuterIndexes;
	private List<LogicTreeBranch<?>> combBranchesOuterPortion;
	private List<Integer> combBranchesInnerIndexes;
	private List<LogicTreeBranch<?>> combBranchesInnerPortion;

	private int numSamples = -1;
	private int numPairwiseSamples = -1;
	private Random pairwiseSampleRand;

	private LogicTree<LogicTreeNode> combTree;

	private LogicTree<?> origOuterTree = null;
	private LogicTree<?> origInnerTree = null;
	
	private List<LogicTreeCombinationProcessor> processors = new ArrayList<>();;
	
	public AbstractLogicTreeCombiner(LogicTree<?> outerLT, LogicTree<?> innerLT) {
		this(outerLT, innerLT, null, null);
	}
	
	public AbstractLogicTreeCombiner(LogicTree<?> outerLT, LogicTree<?> innerLT,
			List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels, List<LogicTreeLevel<?>> averageAcrossLevels) {
		System.out.println("Remapping outer logic tree levels");
		outerLevelRemaps = new HashMap<>();
		outerNodeRemaps = new HashMap<>();
		remapOuterTree(outerLT, outerLevelRemaps, outerNodeRemaps);
		System.out.println("Remapping inner logic tree levels");
		innerLevelRemaps = new HashMap<>();
		innerNodeRemaps = new HashMap<>();
		remapInnerTree(innerLT, innerLevelRemaps, innerNodeRemaps);
		
		outerTree = outerLT;
		innerTree = innerLT;
		
		int innerTreeSize = innerTree.size();
		if (commonLevels == null) {
			commonLevels = List.of();
		} else if (!commonLevels.isEmpty()) {
			// make sure none of these common levels were remapped
			Preconditions.checkState(commonLevels.size() < outerTree.getLevels().size(),
					"At least one level of the outer tree must be unique.");
			Preconditions.checkState(commonLevels.size() < innerTree.getLevels().size(),
					"At least one level of the inner tree must be unique.");
			for (LogicTreeLevel<?> level : commonLevels) {
				Preconditions.checkState(!outerLevelRemaps.containsKey(level) || outerLevelRemaps.get(level).equals(level),
						"Outer remaps include a common level: %s", level.getName());
				Preconditions.checkState(!innerLevelRemaps.containsKey(level) || innerLevelRemaps.get(level).equals(level),
						"Inner remaps include a common level: %s", level.getName());
				Preconditions.checkState(outerTree.getLevels().contains(level),
						"Outer tree doesn't contain level %s, but it's a common level?", level.getName());
				Preconditions.checkState(innerTree.getLevels().contains(level),
						"Inner tree doesn't contain level %s, but it's a common level?", level.getName());
			}
			commonSubtrees = new HashMap<>();
			for (LogicTreeBranch<?> outerBranch : outerTree) {
				List<LogicTreeNode> commonNodes = new ArrayList<>(commonLevels.size());
				for (LogicTreeLevel<?> level : commonLevels) {
					LogicTreeNode node = outerBranch.requireValue(level.getType());
					Preconditions.checkNotNull(node);
					commonNodes.add(node);
				}
				LogicTreeBranch<LogicTreeNode> commonBranch = new LogicTreeBranch<>(commonLevels, commonNodes);
				if (!commonSubtrees.containsKey(commonBranch)) {
					// new common branch
					LogicTreeNode[] matches = commonNodes.toArray(new LogicTreeNode[commonNodes.size()]);
					LogicTree<?> subtree = innerTree.matchingAll(matches);
					Preconditions.checkState(subtree.size() > 0,
							"Inner tree doesn't have any branches with these common values from the outer tree: %s", commonNodes);
					innerTreeSize = subtree.size();
					commonSubtrees.put(commonBranch, subtree);
				}
			}
		}
		if (averageAcrossLevels == null) {
			averageAcrossLevels = List.of();
		} else if (!averageAcrossLevels.isEmpty()) {
			// remove average across levels from each tree
			System.out.println("Averaging across "+averageAcrossLevels.size()+" levels");
			origOuterTree = outerTree;
			origInnerTree = innerTree;
			outerTree = removeAveragedOutLevels(outerTree, averageAcrossLevels);
			innerTree = removeAveragedOutLevels(innerTree, averageAcrossLevels);
			System.out.println("Reduced outerTree from "+origOuterTree.size()+" to "+outerTree.size()+" branches");
			System.out.println("Reduced innerTree from "+origInnerTree.size()+" to "+innerTree.size()+" branches");
			Preconditions.checkState(origOuterTree != outerTree || origInnerTree != innerTree);
		}
		combLevels = new ArrayList<>();
		for (LogicTreeLevel<?> level : outerTree.getLevels()) {
			if (averageAcrossLevels.contains(level))
				continue;
			if (outerLevelRemaps.containsKey(level))
				level = outerLevelRemaps.get(level);
			combLevels.add(level);
		}
		for (LogicTreeLevel<?> level : innerTree.getLevels()) {
			if (averageAcrossLevels.contains(level))
				continue;
			if (innerLevelRemaps.containsKey(level))
				level = innerLevelRemaps.get(level);
			if (!commonLevels.contains(level))
				// make sure this isn't common to both trees
				combLevels.add(level);
		}
		
		System.out.println("Combined levels:");
		for (LogicTreeLevel<?> level : combLevels)
			System.out.println(level.getName()+" ("+level.getShortName()+")");
		
		expectedNum = outerTree.size() * innerTreeSize;
		System.out.println("Total number of combinations: "+countDF.format(expectedNum));
		
		this.commonLevels = commonLevels;
		this.averageAcrossLevels = averageAcrossLevels;
	}
	
	private static LogicTree<?> removeAveragedOutLevels(LogicTree<?> tree, List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels) {
		List<LogicTreeLevel<? extends LogicTreeNode>> retainedLevels = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels())
			if (!averageAcrossLevels.contains(level))
				retainedLevels.add(level);
		Preconditions.checkState(!retainedLevels.isEmpty());
		if (retainedLevels.size() == tree.getLevels().size())
			return tree;
		Map<LogicTreeBranch<LogicTreeNode>, Integer> retainedBranchesMap = new HashMap<>();
		List<LogicTreeBranch<LogicTreeNode>> retainedBranches = new ArrayList<>();
		List<Double> retainedWeights = new ArrayList<>();
		for (LogicTreeBranch<?> branch : tree) {
			LogicTreeBranch<LogicTreeNode> retainedBranch = new LogicTreeBranch<>(retainedLevels);
			for (LogicTreeLevel<?> level : retainedLevels)
				retainedBranch.setValue(branch.getValue(level.getType()));
			for (int l=0; l<branch.size(); l++)
				Preconditions.checkNotNull(branch.getValue(l));
			Integer prevIndex = retainedBranchesMap.get(retainedBranch);
			if (prevIndex == null) {
				retainedBranchesMap.put(retainedBranch, retainedBranches.size());
				retainedBranches.add(retainedBranch);
				retainedWeights.add(tree.getBranchWeight(branch));
			} else {
				// duplicate
				retainedWeights.set(prevIndex, retainedWeights.get(prevIndex) + tree.getBranchWeight(branch));
			}
		}
		
		// set weights
		for (int i=0; i<retainedBranches.size(); i++)
			retainedBranches.get(i).setOrigBranchWeight(retainedWeights.get(i));
		
		LogicTree<LogicTreeNode> ret = LogicTree.fromExisting(retainedLevels, retainedBranches);
		ret.setWeightProvider(new BranchWeightProvider.OriginalWeights());
		return ret;
	}
	
	protected abstract void remapOuterTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	protected abstract void remapInnerTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	public LogicTree<?> getOuterTree() {
		return outerTree;
	}

	public LogicTree<?> getInnerTree() {
		return innerTree;
	}

	public LogicTree<LogicTreeNode> getCombTree() {
		if (combTree == null)
			buildCominedTree();
		return combTree;
	}
	
	public static LogicTree<?> pairwiseSampleLogicTrees(LogicTree<?> outerTree, LogicTree<?> innerTree, int numPairwiseSamples) {
		return pairwiseSampleLogicTrees(outerTree, innerTree, numPairwiseSamples, 1);
	}
	
	public static LogicTree<?> pairwiseSampleLogicTrees(LogicTree<?> outerTree, LogicTree<?> innerTree, int numOuterSamples, int numInnerPerOuter) {
		AbstractLogicTreeCombiner comb = new AbstractLogicTreeCombiner(outerTree, innerTree) {

			@Override
			protected void remapOuterTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
					Map<LogicTreeNode, LogicTreeNode> nodeRemaps) {}

			@Override
			protected void remapInnerTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
					Map<LogicTreeNode, LogicTreeNode> nodeRemaps) {}
			
		};
		
		long rand = (long)comb.expectedNum*(long)numOuterSamples*(long)numInnerPerOuter;
		
		comb.pairwiseSampleTree(numOuterSamples, numInnerPerOuter, rand);
		comb.buildCominedTree();
		
		return comb.combTree;
	}
	
	public static class LogicTreeCombinationContext {
		/**
		 * The total number of posssible combinations (inner branches * outer branches)
		 */
		public final int numPossibleCombinations;
		/**
		 * The number of random samples (regular or pairwise), or -1 if the tree was not randomly sampled
		 */
		public final int numRandomSamples;
		/**
		 * The number of pairwise samples (samples drawn from the inner tree for each outer tree branch), or -1 if
		 * pairwise sampling was not enabled
		 */
		public final int numPairwiseSamples;
		/**
		 * Combined logic tree
		 */
		public final LogicTree<?> combTree;
		/**
		 * Outer logic tree
		 */
		public final LogicTree<?> outerTree;
		/**
		 * Inner logic tree
		 */
		public final LogicTree<?> innerTree;
		/**
		 * Remapped levels from the outer tree (if any)
		 */
		public final Map<LogicTreeLevel<?>, LogicTreeLevel<?>> outerLevelRemaps;
		/**
		 * Remapped nodes from the outer tree (if any)
		 */
		public final Map<LogicTreeNode, LogicTreeNode> outerNodeRemaps;
		/**
		 * Remapped levels from the inner tree (if any)
		 */
		public final Map<LogicTreeLevel<?>, LogicTreeLevel<?>> innerLevelRemaps;
		/**
		 * Remapped nodes from the inner tree (if any)
		 */
		public final Map<LogicTreeNode, LogicTreeNode> innerNodeRemaps;
		/**
		 * Combined logic tree levels
		 */
		public final List<LogicTreeLevel<? extends LogicTreeNode>> combLevels;
		/**
		 * List of combined branches
		 */
		public final List<LogicTreeBranch<LogicTreeNode>> combBranches;
		/**
		 * Outer branch index for each combined branch index
		 */
		public final List<Integer> combBranchesOuterIndexes;
		/**
		 * Outer branch portions (outer branch minus any averaged-out levels) for each combined branch index
		 */
		public final List<LogicTreeBranch<?>> combBranchesOuterPortion;
		/**
		 * Inner branch index for each combined branch index
		 */
		public final List<Integer> combBranchesInnerIndexes;
		/**
		 * Inner branch portions (inner branch minus any averaged-out levels) for each combined branch index
		 */
		public final List<LogicTreeBranch<?>> combBranchesInnerPortion;
		/**
		 * Levels that were common to both the inner and outer trees
		 */
		public final List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels;
		/**
		 * Inner sub-trees for each each set of common branches (values for each {@link #commonLevels} from both trees)
		 */
		public final Map<LogicTreeBranch<LogicTreeNode>, LogicTree<?>> commonSubtrees;
		/**
		 * Levels that we're averaging across
		 */
		public final List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels;
		/**
		 * The outer tree before any averaged-across levels were removed
		 */
		public final LogicTree<?> preAveragingOuterTree;
		/**
		 * The inner tree before any averaged-across levels were removed
		 */
		public final LogicTree<?> preAveragingInnerTree;
		
		private LogicTreeCombinationContext(int numPossibleCombinations, int numRandomSamples, int numPairwiseSamples,
				LogicTree<?> combinedTree, LogicTree<?> outerTree, LogicTree<?> innerTree,
				Map<LogicTreeLevel<?>, LogicTreeLevel<?>> outerLevelRemaps,
				Map<LogicTreeNode, LogicTreeNode> outerNodeRemaps,
				Map<LogicTreeLevel<?>, LogicTreeLevel<?>> innerLevelRemaps,
				Map<LogicTreeNode, LogicTreeNode> innerNodeRemaps,
				List<LogicTreeLevel<? extends LogicTreeNode>> combLevels,
				List<LogicTreeBranch<LogicTreeNode>> combBranches,
				List<Integer> combBranchesOuterIndexes,
				List<LogicTreeBranch<?>> combBranchesOuterPortion,
				List<Integer> combBranchesInnerIndexes,
				List<LogicTreeBranch<?>> combBranchesInnerPortion,
				List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels,
				Map<LogicTreeBranch<LogicTreeNode>, LogicTree<?>> commonSubtrees,
				List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels, LogicTree<?> preAveragingOuterTree,
				LogicTree<?> preAveragingInnerTree) {
			super();
			this.numPossibleCombinations = numPossibleCombinations;
			this.numRandomSamples = numRandomSamples;
			this.numPairwiseSamples = numPairwiseSamples;
			this.combTree = combinedTree;
			this.outerTree = outerTree;
			this.innerTree = innerTree;
			this.outerLevelRemaps = outerLevelRemaps;
			this.outerNodeRemaps = outerNodeRemaps;
			this.innerLevelRemaps = innerLevelRemaps;
			this.innerNodeRemaps = innerNodeRemaps;
			this.combLevels = combLevels;
			this.combBranches = combBranches;
			this.combBranchesOuterIndexes = combBranchesOuterIndexes;
			this.combBranchesOuterPortion = combBranchesOuterPortion;
			this.combBranchesInnerIndexes = combBranchesInnerIndexes;
			this.combBranchesInnerPortion = combBranchesInnerPortion;
			this.commonLevels = commonLevels;
			this.commonSubtrees = commonSubtrees;
			this.averageAcrossLevels = averageAcrossLevels;
			this.preAveragingOuterTree = preAveragingOuterTree;
			this.preAveragingInnerTree = preAveragingInnerTree;
		}
	}
	
	private void buildCominedTree() {
		System.out.println("Building combined tree");
		if (commonLevels.isEmpty()) {
			combBranches = new ArrayList<>(expectedNum);
			combBranchesOuterPortion = new ArrayList<>(expectedNum);
			combBranchesInnerPortion = new ArrayList<>(expectedNum);
			combBranchesOuterIndexes = new ArrayList<>(expectedNum);
			combBranchesInnerIndexes = new ArrayList<>(expectedNum);
		} else {
			combBranches = new ArrayList<>();
			combBranchesOuterPortion = new ArrayList<>();
			combBranchesInnerPortion = new ArrayList<>();
			combBranchesOuterIndexes = new ArrayList<>();
			combBranchesInnerIndexes = new ArrayList<>();
		}
		
		boolean pairwise = numPairwiseSamples > 0;
		Table<LogicTreeBranch<?>, LogicTreeBranch<?>, Integer> prevPairs = null;
		Map<LogicTreeBranch<?>, Integer> outerSampleCounts = null;
		Map<LogicTreeBranch<?>, Double> outerTotalWeights = null;
		List<Integer> branchSampleCounts = null;
		int numPairDuplicates = 0;
		if (pairwise) {
			prevPairs = HashBasedTable.create();
			outerSampleCounts = new HashMap<>();
			outerTotalWeights = new HashMap<>();
			if (commonLevels.isEmpty())
				branchSampleCounts = new ArrayList<>(expectedNum);
			else
				branchSampleCounts = new ArrayList<>();
		}
		
		int printMod = 100;
		
		Map<LogicTreeBranch<?>, Integer> innerBranchIndexes = new HashMap<>();
		for (int i=0; i<innerTree.size(); i++)
			innerBranchIndexes.put(innerTree.getBranch(i), i);
		
		int debugMax = 100;
		
		for (int o=0; o<outerTree.size(); o++) {
			LogicTreeBranch<?> outerBranch = outerTree.getBranch(o);
			LogicTree<?> matchingInnerTree;
			if (commonSubtrees == null) {
				matchingInnerTree = innerTree;
			} else {
				List<LogicTreeNode> commonNodes = new ArrayList<>();
				for (LogicTreeLevel<?> level : commonLevels)
					commonNodes.add(outerBranch.requireValue(level.getType()));
				LogicTreeBranch<LogicTreeNode> commonBranch = new LogicTreeBranch<>(commonLevels, commonNodes);
				matchingInnerTree = commonSubtrees.get(commonBranch);
				Preconditions.checkNotNull(matchingInnerTree);
			}
			int numInner = numPairwiseSamples > 0 ? numPairwiseSamples : matchingInnerTree.size();
			for (int i=0; i<numInner; i++) {
				LogicTreeBranch<?> innerBranch;
				double weight;
				if (pairwise) {
					// sample an inner branch
					IntegerPDF_FunctionSampler sampler = matchingInnerTree.getSampler();
					innerBranch = matchingInnerTree.getBranch(matchingInnerTree.getSampler().getRandomInt(pairwiseSampleRand));
					int prevOuterSampleCount = outerSampleCounts.containsKey(outerBranch) ? outerSampleCounts.get(outerBranch) : 0;
					while (prevPairs.contains(outerBranch, innerBranch)) {
						// duplicate
						Preconditions.checkState(prevPairs.row(outerBranch).size() < matchingInnerTree.size(),
								"Already sampled all %s inner branches for outer branch %s: %s",
								matchingInnerTree.size(), o, outerBranch);
						int prevIndex = prevPairs.get(outerBranch, innerBranch);
						// register that we sampled this one again
						branchSampleCounts.set(prevIndex, branchSampleCounts.get(prevIndex)+1);
						// also register that we have an extra sample of the outer branch
						prevOuterSampleCount++;
						// now resample the inner branch
						innerBranch = matchingInnerTree.getBranch(sampler.getRandomInt(pairwiseSampleRand));
						numPairDuplicates++;
					}
					prevPairs.put(outerBranch, innerBranch, combBranches.size());
					outerSampleCounts.put(outerBranch, prevOuterSampleCount+1);
					double prevOuterWeight = outerTotalWeights.containsKey(outerBranch) ? outerTotalWeights.get(outerBranch) : 0d;
					outerTotalWeights.put(outerBranch, prevOuterWeight + outerTree.getBranchWeight(o));
					// register that this branch has been sampled 1 time
					branchSampleCounts.add(1);
					weight = 1d; // will fill in later
				} else {
					innerBranch = matchingInnerTree.getBranch(i);
					weight = outerTree.getBranchWeight(o) * innerTree.getBranchWeight(i);
				}
				
				LogicTreeBranch<LogicTreeNode> combBranch = new LogicTreeBranch<>(combLevels);
				int combNodeIndex = 0;
				for (int l=0; l<outerBranch.size(); l++) {
					LogicTreeNode node = outerBranch.getValue(l);
					if (outerNodeRemaps.containsKey(node)) {
						LogicTreeNode remappedNode = outerNodeRemaps.get(node);
						if (remappedNode != node) {
							node = remappedNode;
						}
					}
					combBranch.setValue(node);
					LogicTreeNode getNode = combBranch.getValue(combNodeIndex);
					Preconditions.checkState(getNode == node,
							"Set didn't work for node %s of combined branch: %s, has %s",
							combNodeIndex, node, getNode);
					combNodeIndex++;
				}
				for (int l=0; l<innerBranch.size(); l++) {
					if (commonLevels.contains(innerBranch.getLevel(l)))
						// skip common levels (already accounted for in the outer branch)
						continue;
					LogicTreeNode node = innerBranch.getValue(l);
					if (innerNodeRemaps.containsKey(node))
						node = innerNodeRemaps.get(node);
					combBranch.setValue(node);
					LogicTreeNode getNode = combBranch.getValue(combNodeIndex);
					Preconditions.checkState(getNode == node,
							"Set didn't work for node %s of combined branch: %s, has %s;\n\tInner branch: %s"
							+ "\n\tOuter branch: %s\n\tCombined branch: %s",
							combNodeIndex, node, getNode, innerBranch, outerBranch, combBranch);
					combNodeIndex++;
				}
				combBranch.setOrigBranchWeight(weight);
				
				if (combBranches.size() < debugMax) {
					System.out.println("Build debug for branch "+combBranches.size());
					System.out.println("\tCombined: "+combBranch);
					System.out.println("\tOuter "+o+": "+outerBranch);
					System.out.println("\tInner "+innerBranchIndexes.get(innerBranch)+": "+innerBranch);
				}
				
				combBranches.add(combBranch);
				combBranchesOuterPortion.add(outerBranch);
				combBranchesInnerPortion.add(innerBranch);
				combBranchesOuterIndexes.add(o);
				combBranchesInnerIndexes.add(innerBranchIndexes.get(innerBranch));
				
				int count = combBranches.size();
				if (count % printMod == 0) {
					String str = "\tBuilt "+countDF.format(count)+" branches";
					if (pairwise)
						str += " ("+numPairDuplicates+" pairwise duplicates redrawn)";
					System.out.println(str);
				}
				if (count >= printMod*10 && printMod < 1000)
					printMod *= 10;
			}
		}
		System.out.println("Built "+countDF.format(combBranches.size())+" branches");
		Preconditions.checkState(!commonLevels.isEmpty() || combBranches.size() == expectedNum);
		
		combTree = LogicTree.fromExisting(combLevels, combBranches);
		combTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
		if (numPairwiseSamples > 0) {
			System.out.println("Pairwise tree with numPairwiseSamples="+numPairwiseSamples
					+", and "+numPairDuplicates+" duplicate pairs encountered");
			// fill in weights
			double sumWeight = 0d;
			for (int i=0; i<combTree.size(); i++) {
				LogicTreeBranch<?> branch = combTree.getBranch(i);
				LogicTreeBranch<?> outerBranch = combBranchesOuterPortion.get(i);
				int branchSamples = branchSampleCounts.get(i);
				int outerTotSamples = outerSampleCounts.get(outerBranch);
				// the total weight (across all instances) allocated to this outer branch
				double outerWeight = outerTotalWeights.get(outerBranch);
				// the fraction of that weight allocated to this inner branch
				double thisBranchFract = (double)branchSamples / (double)outerTotSamples;
				double weight = outerWeight * thisBranchFract;
				branch.setOrigBranchWeight(weight);
				sumWeight += weight;
			}
			// print out sampling stats
			Map<LogicTreeNode, Integer> sampledNodeCounts = new HashMap<>();
			Map<LogicTreeNode, Double> sampledNodeWeights = new HashMap<>();
			for (LogicTreeBranch<?> branch : combBranches) {
				double weight = branch.getOrigBranchWeight()/sumWeight;
				for (LogicTreeNode node : branch) {
					int prevCount = 0;
					double prevWeight = 0d;
					if (sampledNodeCounts.containsKey(node)) {
						prevCount = sampledNodeCounts.get(node);
						prevWeight = sampledNodeWeights.get(node);
					}
					sampledNodeCounts.put(node, prevCount+1);
					sampledNodeWeights.put(node, prevWeight+weight);
				}
			}
			
			Map<LogicTreeNode, Integer> origCombNodeCounts = new HashMap<>();
			Map<LogicTreeNode, Double> origCombNodeWeights = new HashMap<>();
			for (boolean inner : new boolean[] {false,true}) {
				Map<LogicTreeNode, Integer> origNodeCounts = new HashMap<>();
				Map<LogicTreeNode, Double> origNodeWeights = new HashMap<>();
				double totWeight = 0d;
				LogicTree<?> tree = inner ? innerTree : outerTree;
				Map<LogicTreeNode, LogicTreeNode> nodeRemaps = inner ? innerNodeRemaps : outerNodeRemaps;
				for (LogicTreeBranch<?> branch : tree) {
					double weight = tree.getBranchWeight(branch);
					totWeight += weight;
					for (int l=0; l<branch.size(); l++) {
						if (!inner || !commonLevels.contains(branch.getLevel(l))) {
							LogicTreeNode node = branch.getValue(l);
							if (nodeRemaps.containsKey(node))
								node = nodeRemaps.get(node);
							if (origNodeCounts.containsKey(node)) {
								origNodeCounts.put(node, origNodeCounts.get(node) + 1);
								origNodeWeights.put(node, origNodeWeights.get(node) + weight);
							} else {
								origNodeCounts.put(node, 1);
								origNodeWeights.put(node, weight);
							}
						}
					}
				}
				for (LogicTreeNode node : origNodeCounts.keySet()) {
					Preconditions.checkState(!origCombNodeCounts.containsKey(node));
					int count = origNodeCounts.get(node);
					double weight = origNodeWeights.get(node);
					if (totWeight != 1d)
						weight /= totWeight;
					origCombNodeCounts.put(node, count);
					origCombNodeWeights.put(node, weight);
				}
			}
			LogicTree.printSamplingStats(combLevels, sampledNodeCounts, sampledNodeWeights, origCombNodeCounts, origCombNodeWeights);
		}
		if (pairwise)
			numSamples = combTree.size();
		else
			numSamples = -1;
	}
	
	public void pairwiseSampleTree(int numSamples) {
		pairwiseSampleTree(numSamples, (long)expectedNum*(long)(numSamples == 0 ? outerTree.size() : numSamples));
	}
	
	public void pairwiseSampleTree(int numSamples, long randSeed) {
		pairwiseSampleTree(numSamples, 1, randSeed);
	}
	
	public void pairwiseSampleTree(int numOuterSamples, int numInnerPerOuter, long randSeed) {
		Preconditions.checkState(numInnerPerOuter > 0);
		Preconditions.checkState(combTree == null, "Can't pairwise-sample if tree is already built");
		Preconditions.checkState(numPairwiseSamples < 1, "Can't pairwise-sample twice");
		pairwiseSampleRand = new Random(randSeed);
		if (numOuterSamples != 0 && numOuterSamples != outerTree.size()) {
			// first sample the outer tree
			System.out.println("Pre-sampling outer tree to "+numOuterSamples+" samples for pairwise");
			
			// redraw duplicates if we have almost as many (or more) samples than exist in the outer tree
			boolean redrawDuplicates = numOuterSamples < (int)(0.95*outerTree.size());
			LogicTree<?> sampledOuterTree = outerTree.sample(numOuterSamples, redrawDuplicates, pairwiseSampleRand, false);
			Preconditions.checkState(sampledOuterTree.size() == numOuterSamples,
					"Resampled outer tree from %s to %s, but asked for %s samples",
					outerTree.size(), sampledOuterTree.size(), numOuterSamples);
			this.outerTree = sampledOuterTree;
		}
		System.out.println("Pairwise-sampling inner tree with "+numInnerPerOuter+" samples per outer");
		numPairwiseSamples = numInnerPerOuter;
		expectedNum = outerTree.size()*numInnerPerOuter;
	}

	public void sampleTree(int maxNumCombinations) {
		sampleTree(maxNumCombinations, (long)expectedNum*(long)maxNumCombinations);
	}
	
	public void sampleTree(int maxNumCombinations, long randSeed) {
		if (combTree == null)
			// build it
			buildCominedTree();
		System.out.println("Samping down to "+maxNumCombinations+" samples");
		// keep track of the original indexes
		Map<LogicTreeBranch<?>, Integer> origIndexes = new HashMap<>(combTree.size());
		for (int i=0; i<combTree.size(); i++)
			origIndexes.put(combTree.getBranch(i), i);
		combTree = combTree.sample(maxNumCombinations, true, new Random(randSeed));
		
		// rebuild the lists
		List<LogicTreeBranch<LogicTreeNode>> modCombBranches = new ArrayList<>(maxNumCombinations);
		List<Integer> modCombBranchesOuterIndexes = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesOuterPortion = new ArrayList<>(maxNumCombinations);
		List<Integer> modCombBranchesInnerIndexes = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesInnerPortion = new ArrayList<>(maxNumCombinations);
		
		for (LogicTreeBranch<LogicTreeNode> branch : combTree) {
			int origIndex = origIndexes.get(branch);
			modCombBranches.add(branch);
			modCombBranchesOuterIndexes.add(combBranchesOuterIndexes.get(origIndex));
			modCombBranchesOuterPortion.add(combBranchesOuterPortion.get(origIndex));
			modCombBranchesInnerIndexes.add(combBranchesInnerIndexes.get(origIndex));
			modCombBranchesInnerPortion.add(combBranchesInnerPortion.get(origIndex));
		}
		
		this.combBranches = modCombBranches;
		this.combBranchesOuterIndexes = modCombBranchesOuterIndexes;
		this.combBranchesOuterPortion = modCombBranchesOuterPortion;
		this.combBranchesInnerIndexes = modCombBranchesInnerIndexes;
		this.combBranchesInnerPortion = modCombBranchesInnerPortion;
		
		numPairwiseSamples = -1;
		numSamples = combTree.size();
	}
	
	public void addProcessor(LogicTreeCombinationProcessor processor) {
		this.processors.add(processor);
	}
	
	public void processCombinations() throws IOException {
		Preconditions.checkState(!processors.isEmpty(), "No processors supplied");
		if (combTree == null)
			// build it
			buildCominedTree();
		
		int ioThreadCount = Integer.min(20, Integer.max(3, FaultSysTools.defaultNumThreads()));
		ExecutorService ioExec = ExecutorUtils.newNamedThreadPool(ioThreadCount, "ltCombinerIO");
		
		LogicTreeBranch<?> prevOuter = null;
		
		int numOutersProcessed = 0;
		
		ExecutorService exec = ExecutorUtils.newNamedThreadPool(FaultSysTools.defaultNumThreads(), "ltCombinerExec");
		
		Stopwatch watch = Stopwatch.createStarted();
		
		Stopwatch combineWatch = Stopwatch.createUnstarted();
		
		LogicTreeCombinationContext logicTreeContext = new LogicTreeCombinationContext(
				expectedNum, numSamples, numPairwiseSamples, combTree, outerTree, innerTree,
				outerLevelRemaps, outerNodeRemaps, innerLevelRemaps, innerNodeRemaps,
				combLevels, combBranches, combBranchesOuterIndexes, combBranchesOuterPortion, combBranchesInnerIndexes, combBranchesInnerPortion,
				commonLevels, commonSubtrees,
				averageAcrossLevels, origOuterTree, origInnerTree);
		
		List<CompletableFuture<Void>> processFutures = new ArrayList<>(processors.size());
		List<Stopwatch> processWatches = new ArrayList<>(processors.size());
		for (LogicTreeCombinationProcessor processor : processors) {
			processor.init(logicTreeContext, exec, ioExec);
			processFutures.add(null);
			processWatches.add(Stopwatch.createUnstarted());
		}
		
		int combTreeSize = combTree.size();
		
		for (int n=0; n<combTreeSize; n++) {
			final LogicTreeBranch<LogicTreeNode> combBranch = combBranches.get(n);
			System.out.println("Processing branch "+n+"/"+combTreeSize+": "+combBranch);
			Preconditions.checkState(combBranches.get(n).equals(combTree.getBranch(n)));
			final double combWeight = combBranch.getOrigBranchWeight();
			final LogicTreeBranch<?> outerBranch = combBranchesOuterPortion.get(n);
			final LogicTreeBranch<?> innerBranch = combBranchesInnerPortion.get(n);
			final int outerIndex = combBranchesOuterIndexes.get(n);
			final int innerIndex = combBranchesInnerIndexes.get(n);
			System.out.println("\tOuter branch "+outerIndex+": "+outerBranch);
			System.out.println("\tInner branch "+innerIndex+": "+innerBranch);
			Preconditions.checkState(innerBranch.equals(innerTree.getBranch(innerIndex)),
					"Inner branch for %s [%s] doesn't match outer branch at innerIndex=%s [%s]",
					n, innerBranch, innerIndex, innerTree.getBranch(innerIndex));
			Preconditions.checkState(outerBranch.equals(outerTree.getBranch(outerIndex)),
					"Outer branch for %s [%s] doesn't match outer branch at outerIndex=%s [%s]",
					n, outerBranch, outerIndex, outerTree.getBranch(outerIndex));
			for (LogicTreeNode node : innerBranch) {
				if (innerNodeRemaps.containsKey(node))
					node = innerNodeRemaps.get(node);
				Preconditions.checkState(combBranch.hasValue(node),
						"Inner branch has node %s which isn't on conbined branch: %s",
						node, combBranch);
			}
			for (LogicTreeNode node : outerBranch) {
				if (outerNodeRemaps.containsKey(node))
					node = outerNodeRemaps.get(node);
				Preconditions.checkState(combBranch.hasValue(node),
						"Outer branch has node %s which isn't on conbined branch: %s",
						node, combBranch);
			}
			
			if (prevOuter == null || !outerBranch.equals(prevOuter)) {
				System.out.println("New outer branch: "+n+"/"+outerTree.size()+": "+outerBranch);
				
				if (n > 0) {
					double fractDone = (double)n/(double)combTree.size();
					System.out.println("DONE outer branch "+numOutersProcessed+"/"+outerTree.size()+", "
							+n+"/"+combTree.size()+" total branches ("+pDF.format(fractDone)+")");
					printBlockingTimes(watch, combineWatch, processWatches);
					double totSecs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					double secsEach = totSecs / (double)n;
					double expectedSecs = secsEach*combTree.size();
					double secsLeft = expectedSecs - totSecs;
					double minsLeft = secsLeft/60d;
					double hoursLeft = minsLeft/60d;
					if (minsLeft > 90)
						System.out.println("\tEstimated time left: "+twoDigits.format(hoursLeft)+" hours");
					else if (secsLeft > 90)
						System.out.println("\tEstimated time left: "+twoDigits.format(minsLeft)+" mins");
					else
						System.out.println("\tEstimated time left: "+twoDigits.format(secsLeft)+" secs");
					
					numOutersProcessed++;
				}
			}
			
			int combIndex = n;
			for (int p=0; p<processors.size(); p++) {
				CompletableFuture<Void> prevFuture = processFutures.get(p);
				if (prevFuture != null) {
					Stopwatch processWatch = processWatches.get(p);
					processWatch.start();
					combineWatch.start();
					prevFuture.join();
					combineWatch.stop();
					processWatch.stop();
				}
				LogicTreeCombinationProcessor processor = processors.get(p);
				processFutures.set(p, CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							processor.processBranch(combBranch, combIndex, combWeight,
									outerBranch, outerIndex, innerBranch, innerIndex);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				}));
				
			}
			
			prevOuter = outerBranch;
		}
		
		for (int p=0; p<processors.size(); p++) {
			CompletableFuture<Void> prevFuture = processFutures.get(p);
			if (prevFuture != null) {
				Stopwatch processWatch = processWatches.get(p);
				processWatch.start();
				combineWatch.start();
				prevFuture.join();
				combineWatch.stop();
				processWatch.stop();
			}
		}
		
		exec.shutdown();
		ioExec.shutdown();
		
		watch.stop();
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Wrote for "+combBranches.size()+" branches ("+(float)(combBranches.size()/secs)+" /s)");
		
		for (LogicTreeCombinationProcessor processor : processors) {
			System.out.println("Finalizing "+processor.getName());
			processor.close();
		}
		
		System.out.println("DONE");
		printBlockingTimes(watch, combineWatch, processWatches);
	}

	private void printBlockingTimes(Stopwatch watch, Stopwatch combineWatch, List<Stopwatch> processWatches) {
		System.out.println("\tTotal time combining:\t"+blockingTimePrint(combineWatch, watch));
		for (int i=0; i < processors.size(); i++) {
			LogicTreeCombinationProcessor processor = processors.get(i);
			Stopwatch processorWatch = processWatches.get(i);
			String processorTimeStr = processor.getTimeBreakdownString(watch);
			if (processorTimeStr != null && !processorTimeStr.isBlank())
				processorTimeStr = ";\t"+processorTimeStr;
			else
				processorTimeStr = "";
			System.out.println("\t\t"+processor.getName()+":\t"+blockingTimePrint(processorWatch, watch)+processorTimeStr);
		}
	}
	
	private static final DecimalFormat twoDigits = new DecimalFormat("0.00");
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
	private static final DecimalFormat countDF = new DecimalFormat("0.#");
	static {
		countDF.setGroupingSize(3);
		countDF.setGroupingUsed(true);
	}
	
	public static String blockingTimePrint(Stopwatch blockingWatch, Stopwatch totalWatch) {
		double blockSecs = blockingWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		double blockMins = blockSecs/60d;
		String timeStr;
		if (blockMins > 1d) {
			timeStr = twoDigits.format(blockMins)+" m";
		} else {
			timeStr = twoDigits.format(blockSecs)+" s";
		}
		
		double totSecs = totalWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		return timeStr+" ("+pDF.format(blockSecs/totSecs)+")";
	}
	
}
