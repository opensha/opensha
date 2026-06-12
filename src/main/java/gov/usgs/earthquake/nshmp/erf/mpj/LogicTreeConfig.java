package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;

import com.google.common.base.Preconditions;

public final class LogicTreeConfig {

	private final Source source;

	private LogicTreeConfig(Source source) {
		this.source = source;
	}

	Source source() {
		return source;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private LogicTree<LogicTreeNode> suppliedLogicTree;
		private LogicTree<LogicTreeNode> suppliedAnalysisTree;
		private LogicTree<LogicTreeNode> suppliedOriginalTree;
		private List<LogicTreeLevel<? extends LogicTreeNode>> levels;
		private final List<RandomlyGeneratedLevel<?>> randomLevels = new ArrayList<>();
		private LogicTreeNode[] requiredNodes;
		private boolean forceRequiredNonZeroWeight;
		private int samplingBranchCountMultiplier = 1;
		private Integer downsampleCount;
		private long randomSeed = 12345678L;
		private Class<? extends LogicTreeNode> sortBy;

		public Builder forSuppliedLogicTree(LogicTree<LogicTreeNode> suppliedLogicTree) {
			return forSuppliedLogicTree(suppliedLogicTree, null, null);
		}

		public Builder forSuppliedLogicTree(LogicTree<LogicTreeNode> suppliedLogicTree,
				LogicTree<LogicTreeNode> suppliedAnalysisTree) {
			return forSuppliedLogicTree(suppliedLogicTree, suppliedAnalysisTree, null);
		}

		public Builder forSuppliedLogicTree(LogicTree<LogicTreeNode> suppliedLogicTree,
				LogicTree<LogicTreeNode> suppliedAnalysisTree,
				LogicTree<LogicTreeNode> suppliedOriginalTree) {
			this.suppliedLogicTree = suppliedLogicTree;
			this.suppliedAnalysisTree = suppliedAnalysisTree;
			this.suppliedOriginalTree = suppliedOriginalTree;
			this.levels = null;
			this.randomLevels.clear();
			this.requiredNodes = null;
			this.downsampleCount = null;
			this.forceRequiredNonZeroWeight = false;
			this.sortBy = null;
			this.samplingBranchCountMultiplier = 1;
			return this;
		}

		public Builder forLogicTreeLevels(List<LogicTreeLevel<? extends LogicTreeNode>> levels) {
			this.levels = levels;
			this.suppliedLogicTree = null;
			this.suppliedAnalysisTree = null;
			this.suppliedOriginalTree = null;
			return this;
		}

		public Builder requiredNodes(LogicTreeNode... requiredNodes) {
			this.requiredNodes = requiredNodes;
			return this;
		}

		public Builder forceRequiredNonZeroWeight(boolean forceRequiredNonZeroWeight) {
			this.forceRequiredNonZeroWeight = forceRequiredNonZeroWeight;
			return this;
		}

		public Builder addRandomLevel(RandomlyGeneratedLevel<?> randomLevel) {
			if (randomLevel != null)
				this.randomLevels.add(randomLevel);
			return this;
		}

		public Builder samplingBranchCountMultiplier(int samplingBranchCountMultiplier) {
			this.samplingBranchCountMultiplier = samplingBranchCountMultiplier;
			return this;
		}

		public Builder downsampleCount(Integer downsampleCount) {
			this.downsampleCount = downsampleCount;
			return this;
		}

		public Builder randomSeed(long randomSeed) {
			this.randomSeed = randomSeed;
			return this;
		}

		public Builder sortBy(Class<? extends LogicTreeNode> sortBy) {
			this.sortBy = sortBy;
			return this;
		}

		public LogicTreeConfig build() {
			Preconditions.checkState(suppliedLogicTree != null || levels != null,
					"must supply a logic tree or logic tree levels");
			Source source;
			if (suppliedLogicTree != null) {
				source = new SuppliedSource(suppliedLogicTree, suppliedAnalysisTree, suppliedOriginalTree);
			} else {
				source = new LevelBasedSource(levels, requiredNodes, forceRequiredNonZeroWeight,
						randomLevels, samplingBranchCountMultiplier, downsampleCount, randomSeed, sortBy);
			}
			return new LogicTreeConfig(source);
		}
	}

	interface Source {
		Resolution resolve() throws IOException;
	}

	static final class Resolution {
		private final LogicTree<LogicTreeNode> logicTree;
		private final LogicTree<LogicTreeNode> analysisTree;
		private final LogicTree<?> originalTree;
		private final List<LogicTreeLevel<? extends LogicTreeNode>> levels;

		Resolution(LogicTree<LogicTreeNode> logicTree, LogicTree<LogicTreeNode> analysisTree,
				LogicTree<?> originalTree, List<LogicTreeLevel<? extends LogicTreeNode>> levels) {
			this.logicTree = logicTree;
			this.analysisTree = analysisTree;
			this.originalTree = originalTree;
			this.levels = levels;
		}

		LogicTree<LogicTreeNode> logicTree() {
			return logicTree;
		}

		LogicTree<LogicTreeNode> analysisTree() {
			return analysisTree;
		}

		LogicTree<?> originalTree() {
			return originalTree;
		}

		List<LogicTreeLevel<? extends LogicTreeNode>> levels() {
			return levels;
		}
	}

	static final class SuppliedSource implements Source {
		private final LogicTree<LogicTreeNode> logicTree;
		private final LogicTree<LogicTreeNode> analysisTree;
		private final LogicTree<LogicTreeNode> originalTree;

		SuppliedSource(LogicTree<LogicTreeNode> logicTree, LogicTree<LogicTreeNode> analysisTree,
				LogicTree<LogicTreeNode> originalTree) {
			this.logicTree = logicTree;
			this.analysisTree = analysisTree;
			this.originalTree = originalTree;
		}

		@Override
		public Resolution resolve() {
			return new Resolution(logicTree, analysisTree, originalTree, new ArrayList<>(logicTree.getLevels()));
		}
	}

	static final class LevelBasedSource implements Source {
		private final List<LogicTreeLevel<? extends LogicTreeNode>> levels;
		private final LogicTreeNode[] requiredNodes;
		private final boolean forceRequiredNonZeroWeight;
		private final List<RandomlyGeneratedLevel<?>> randomLevels;
		private final int samplingBranchCountMultiplier;
		private final Integer downsampleCount;
		private final long randomSeed;
		private final Class<? extends LogicTreeNode> sortBy;

		LevelBasedSource(List<LogicTreeLevel<? extends LogicTreeNode>> levels, LogicTreeNode[] requiredNodes,
				boolean forceRequiredNonZeroWeight, List<RandomlyGeneratedLevel<?>> randomLevels,
				int samplingBranchCountMultiplier, Integer downsampleCount, long randomSeed,
				Class<? extends LogicTreeNode> sortBy) {
			this.levels = List.copyOf(levels);
			this.requiredNodes = requiredNodes;
			this.forceRequiredNonZeroWeight = forceRequiredNonZeroWeight;
			this.randomLevels = List.copyOf(randomLevels);
			this.samplingBranchCountMultiplier = samplingBranchCountMultiplier;
			this.downsampleCount = downsampleCount;
			this.randomSeed = randomSeed;
			this.sortBy = sortBy;
		}

		@Override
		public Resolution resolve() {
			LogicTree<LogicTreeNode> logicTree;
			if (forceRequiredNonZeroWeight && requiredNodes != null && requiredNodes.length > 0) {
				logicTree = LogicTree.buildExhaustive(levels, true,
						new BranchWeightProvider.NodeWeightOverrides(requiredNodes, 1d), requiredNodes);
			} else {
				logicTree = LogicTree.buildExhaustive(levels, true, requiredNodes);
			}
			LogicTree<?> originalTree = logicTree;
			List<LogicTreeLevel<? extends LogicTreeNode>> resolvedLevels = new ArrayList<>(levels);

			if (!randomLevels.isEmpty()) {
				System.out.println("Building tree with "+randomLevels.size()+" random levels & sampling multiplier: "
						+samplingBranchCountMultiplier);
				Preconditions.checkState(samplingBranchCountMultiplier >= 1,
						"samplingBranchCountMultiplier must be >= 1");
				List<LogicTreeLevel<? extends LogicTreeNode>> modLevels = new ArrayList<>(resolvedLevels);
				modLevels.addAll(randomLevels);
				int numBranches = logicTree.size()*samplingBranchCountMultiplier;
				Random rand = new Random(randomSeed);
				List<List<? extends RandomlyGeneratedNode>> levelNodes = new ArrayList<>();
				for (RandomlyGeneratedLevel<?> level : randomLevels) {
					level.build(rand.nextLong(), numBranches);
					levelNodes.add(level.getNodes());
				}

				List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>();
				for (int i=0; i<logicTree.size(); i++) {
					LogicTreeBranch<LogicTreeNode> branch = logicTree.getBranch(i);
					for (int n=0; n<samplingBranchCountMultiplier; n++) {
						List<LogicTreeNode> modValues = new ArrayList<>(modLevels.size());
						for (LogicTreeNode val : branch)
							modValues.add(val);
						int randIndex = modBranches.size();
						for (List<? extends RandomlyGeneratedNode> randNodes : levelNodes)
							modValues.add(randNodes.get(randIndex));
						LogicTreeBranch<LogicTreeNode> modBranch = new LogicTreeBranch<>(modLevels, modValues);
						modBranch.setOrigBranchWeight(branch.getOrigBranchWeight());
						modBranches.add(modBranch);
					}
				}

				resolvedLevels = modLevels;
				logicTree = LogicTree.fromExisting(modLevels, modBranches);
				logicTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
			}
			System.out.println("Built "+logicTree.size()+" branches");

			if (downsampleCount != null && downsampleCount > 0 && downsampleCount < logicTree.size()) {
				logicTree = logicTree.sample(downsampleCount, true, randomSeed);
				System.out.println("Downsampled to "+logicTree.size()+" branches");
			}

			if (sortBy != null)
				logicTree = logicTree.sorted(new LogicTreeBranchSorter(sortBy));

			return new Resolution(logicTree, null, originalTree == logicTree ? null : originalTree, resolvedLevels);
		}
	}

	private static final class LogicTreeBranchSorter implements Comparator<LogicTreeBranch<?>> {
		private final Class<? extends LogicTreeNode> sortBy;

		private LogicTreeBranchSorter(Class<? extends LogicTreeNode> sortBy) {
			this.sortBy = sortBy;
		}

		@SuppressWarnings("unchecked")
		@Override
		public int compare(LogicTreeBranch<?> o1, LogicTreeBranch<?> o2) {
			LogicTreeNode v1 = o1.getValue(sortBy);
			LogicTreeNode v2 = o2.getValue(sortBy);
			boolean fallback = false;
			if (v1 == null || v2 == null) {
				if (v1 == null && v2 == null)
					fallback = true;
				else if (v1 == null)
					return -1;
				else
					return 1;
			} else if (v1.equals(v2)) {
				fallback = true;
			}
			if (fallback)
				return ((LogicTreeBranch<LogicTreeNode>)o1).compareTo((LogicTreeBranch<LogicTreeNode>)o2);
			return v1.getShortName().compareTo(v2.getShortName());
		}
	}
}
