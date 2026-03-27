package org.opensha.commons.logicTree;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.logicTree.LogicTreeNode.FixedWeightNode;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.erf.logicTree.TectonicRegionBranchTreeNode;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

/**
 * Representation of a logic tree: collection of logic tree branches that have the same set of levels
 * 
 * @author kevin
 *
 * @param <E>
 */
@JsonAdapter(LogicTree.Adapter.class)
public class LogicTree<E extends LogicTreeNode> implements Iterable<LogicTreeBranch<E>>, JSON_BackedModule {
	
	private ImmutableList<LogicTreeLevel<? extends E>> levels;
	private ImmutableList<LogicTreeBranch<E>> branches;
	private HashSet<LogicTreeBranch<E>> branchesSet;

	// default to using original weights when this logic tree was instantiated
	private static final BranchWeightProvider DEFAULT_WEIGHTS = new BranchWeightProvider.OriginalWeights();
	
	private BranchWeightProvider weightProvider = DEFAULT_WEIGHTS;
	
	private LogicTree(BranchWeightProvider weightProvider) {
		this.weightProvider = weightProvider;
	}
	
	protected LogicTree(List<LogicTreeLevel<? extends E>> levels, Collection<? extends LogicTreeBranch<E>> branches,
			BranchWeightProvider weightProvider) {
		Preconditions.checkState(levels != null);
		Preconditions.checkState(branches != null);
		this.levels = ImmutableList.copyOf(levels);
		this.branches = ImmutableList.copyOf(branches);
		this.weightProvider = weightProvider;
		for (LogicTreeBranch<E> branch : branches) {
			Preconditions.checkState(branch.size() == levels.size(),
					"Branch has %s levels but expected %s", branch.size(), levels.size());
			for (int i=0; i<levels.size(); i++) {
				LogicTreeLevel<? extends E> myLevel = levels.get(i);
				LogicTreeLevel<? extends E> branchLevel = branch.getLevel(i);
				Preconditions.checkState(myLevel.equals(branchLevel),
						"Branch has different level at index %s:\nLogic tree level: %s\nBranch level: %s\nBranch: %s",
						i, myLevel, branchLevel, branch);
			}
		}
	}
	
	/**
	 * @return the number of logic tree branches
	 */
	public int size() {
		return branches.size();
	}
	
	public LogicTreeBranch<E> getBranch(int index) {
		return branches.get(index);
	}
	
	/**
	 * Uses the selected {@link BranchWeightProvider} to fetch/calculate the weight for the given branch. Shortcut to
	 * getWeightProvider().getWeight(getBranch(index)).
	 * 
	 * @param index
	 * @return weight
	 */
	public double getBranchWeight(int index) {
		return weightProvider.getWeight(getBranch(index));
	}
	
	/**
	 * @param branch
	 * @return true if this logic tree contains the given branch, false otherwise
	 */
	public boolean contains(LogicTreeBranch<?> branch) {
		if (branchesSet == null) {
			synchronized (this) {
				if (branchesSet == null) {
					branchesSet = new HashSet<>(branches);
				}
			}
		}
		return branchesSet.contains(branch);
	}
	
	/**
	 * Uses the selected {@link BranchWeightProvider} to fetch/calculate the weight for the given branch. Shortcut to
	 * getWeightProvider().getWeight(branch).
	 * 
	 * @param branch
	 * @return weight
	 */
	public double getBranchWeight(LogicTreeBranch<?> branch) {
		return weightProvider.getWeight(branch);
	}
	
	public BranchWeightProvider getWeightProvider() {
		return weightProvider;
	}
	
	public void setWeightProvider(BranchWeightProvider weightProvider) {
		Preconditions.checkNotNull(weightProvider);
		this.weightProvider = weightProvider;
	}

	@Override
	public Iterator<LogicTreeBranch<E>> iterator() {
		return branches.iterator();
	}
	
	/**
	 * @return immutable list of levels for this logic tree
	 */
	public ImmutableList<LogicTreeLevel<? extends E>> getLevels() {
		return levels;
	}
	
	/**
	 * @return immutable list of branches for this logic tree
	 */
	public ImmutableList<LogicTreeBranch<E>> getBranches() {
		return branches;
	}
	
	/**
	 * @return sum of the weight of all branches in this logic tree
	 */
	public double getTotalWeight() {
		double totalWeight = 0d;
		for (LogicTreeBranch<E> branch : branches)
			totalWeight += branch.getBranchWeight();
		return totalWeight;
	}
	
	/**
	 * @param values
	 * @return a subset of this logic tree where each branch contains all of the given values
	 */
	@SafeVarargs
	public final LogicTree<E> matchingAll(LogicTreeNode... values) {
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builder();
		for (LogicTreeBranch<E> branch : branches) {
			boolean matches = true;
			for (LogicTreeNode value : values) {
				if (!branch.hasValue(value)) {
					matches = false;
					break;
				}
			}
			if (matches)
				matching.add(branch);
		}
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProvider);
		ret.branches = matching.build();
		ret.levels = levels;
		return ret;
	}
	
	/**
	 * @param values
	 * @return a subset of this logic tree where each branch contains at least one of the given values
	 */
	@SafeVarargs
	public final LogicTree<E> matchingAny(LogicTreeNode... values) {
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builder();
		for (LogicTreeBranch<E> branch : branches) {
			boolean matches = false;
			for (LogicTreeNode value : values) {
				if (branch.hasValue(value)) {
					matches = true;
					break;
				}
			}
			if (matches)
				matching.add(branch);
		}
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProvider);
		ret.branches = matching.build();
		ret.levels = levels;
		return ret;
	}
	
	/**
	 * @param values
	 * @return a subset of this logic tree where no branch contains any of the given values
	 */
	@SafeVarargs
	public final LogicTree<E> matchingNone(LogicTreeNode... values) {
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builder();
		for (LogicTreeBranch<E> branch : branches) {
			boolean matches = false;
			for (LogicTreeNode value : values) {
				if (branch.hasValue(value)) {
					matches = true;
					break;
				}
			}
			if (!matches)
				matching.add(branch);
		}
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProvider);
		ret.branches = matching.build();
		ret.levels = levels;
		return ret;
	}
	
	/**
	 * @param values
	 * @return a subset of this logic tree with the specified branches
	 */
	public final LogicTree<E> subset(Collection<LogicTreeBranch<?>> subsetBranches) {
		HashSet<LogicTreeBranch<?>> set = subsetBranches instanceof HashSet<?> ?
				(HashSet<LogicTreeBranch<?>>)subsetBranches : new HashSet<>(subsetBranches);
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builderWithExpectedSize(subsetBranches.size());
		for (LogicTreeBranch<E> branch : branches)
			if (set.contains(branch))
				matching.add(branch);
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProvider);
		ret.branches = matching.build();
		Preconditions.checkState(subsetBranches.size() == ret.branches.size(),
				"Not all passed in branches were found in the tree");
		ret.levels = levels;
		return ret;
	}
	
	/**
	 * @param numSamples number of random samples
	 * @param redrawDuplicates if true, each branch will be unique, drawing another branch if an already sampled branch
	 * has been selected. Branches that are drawn multiple times will be assigned greater weight and the total number
	 * of branches will exactly match the specified number of samples.
	 * @return a randomly sampled subset of this logic tree, according to their weights. The returned logic tree will
	 * use a {@link BranchWeightProvider} instance modified to reflect the even (post-sampling) weights.
	 */
	public final LogicTree<E> sample(int numSamples, boolean redrawDuplicates) {
		return sample(numSamples, redrawDuplicates, new Random());
	}
	
	/**
	 * @param numSamples number of random samples
	 * @param redrawDuplicates if true, each branch will be unique, drawing another branch if an already sampled branch
	 * has been selected. Branches that are drawn multiple times will be assigned greater weight and the total number
	 * of branches will exactly match the specified number of samples.
	 * @param rand random number generator
	 * @return a randomly sampled subset of this logic tree, according to their weights. The returned logic tree will
	 * use a {@link BranchWeightProvider} instance modified to reflect the even (post-sampling) weights.
	 */
	public final LogicTree<E> sample(int numSamples, boolean redrawDuplicates, Random rand) {
		return sample(numSamples, redrawDuplicates, rand, true);
	}
	
	/**
	 * @param numSamples number of random samples
	 * @param redrawDuplicates if true, each branch will be unique, drawing another branch if an already sampled branch
	 * has been selected. Branches that are drawn multiple times will be assigned greater weight and the total number
	 * of branches will exactly match the specified number of samples.
	 * @param rand random number generator
	 * @return a randomly sampled subset of this logic tree, according to their weights. The returned logic tree will
	 * use a {@link BranchWeightProvider} instance modified to reflect the even (post-sampling) weights.
	 */
	public final LogicTree<E> sample(int numSamples, boolean redrawDuplicates, Random rand, boolean verbose) {
		if (verbose) System.out.println("Resampling logic tree of size="+size()+" to "+numSamples+" samples...");
		Preconditions.checkArgument(numSamples > 0);
		Preconditions.checkState(!redrawDuplicates || numSamples <= size(),
				"Cannot randomly sample %s branches from %s values without any duplicates!", numSamples, size());
		IntegerPDF_FunctionSampler sampler = getSampler();
		int[] indexCounts = new int[branches.size()];
		int sampleCountSum = 0;
		int uniqueBranches = 0;
		if (redrawDuplicates) {
			while (uniqueBranches < numSamples) {
				int index = sampler.getRandomInt(rand);
				if (indexCounts[index] == 0)
					// first time this branch has been sampled
					uniqueBranches++;
				sampleCountSum++;
				indexCounts[index]++;
			}
		} else {
			for (int i=0; i<numSamples; i++) {
				int index = sampler.getRandomInt(rand);
				if (indexCounts[index] == 0)
					uniqueBranches++;
				sampleCountSum++;
				indexCounts[index]++;
			}
		}
		double weightEach = 1d/(double)sampleCountSum;
//		ImmutableList.Builder<LogicTreeBranch<E>> samples = ImmutableList.builder();
		List<LogicTreeBranch<E>> samples = new ArrayList<>(uniqueBranches);
		Map<LogicTreeNode, Integer> sampledNodeCounts = new HashMap<>();
		// iterate in original index order, which keeps the original order (just skipping branches that weren't sampled)
		// (many processing routines are faster when branches are in order, even if some are skipped)
		int mostSamples = 0;
		for (int index=0; index<indexCounts.length; index++) {
			int count = indexCounts[index];
			if (count == 0)
				// never sampled
				continue;
			mostSamples = Integer.max(mostSamples, count);
			LogicTreeBranch<E> branch = getBranch(index).copy();
			if (redrawDuplicates) {
				branch.setOrigBranchWeight((double)count*weightEach);
				samples.add(branch);
			} else {
				branch.setOrigBranchWeight(weightEach);
				for (int i=0; i<count; i++)
					samples.add(branch);
			}
			for (LogicTreeNode node : branch) {
				if (sampledNodeCounts.containsKey(node))
					sampledNodeCounts.put(node, sampledNodeCounts.get(node)+count);
				else
					sampledNodeCounts.put(node, count);
			}
		}
		// if we don't have any duplicates we can just set it to constant values
		// otherwise, we'll use the 'original weights' which we have just overridden
		BranchWeightProvider weightProv = uniqueBranches == sampleCountSum ?
				new BranchWeightProvider.ConstantWeights(weightEach) : new BranchWeightProvider.OriginalWeights();
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProv);
		
		ret.branches = ImmutableList.copyOf(samples);
		ret.levels = levels;
		
		if (verbose) {
			System.out.println("\tSampled "+uniqueBranches+" unique branches a total of "+sampleCountSum
					+" times. The most any single branch was sampled is "+mostSamples+" time(s).");
			Map<LogicTreeNode, Integer> origNodeCounts = new HashMap<>();
			Map<LogicTreeNode, Double> origNodeWeights = new HashMap<>();
			double totWeight = 0d;
			for (LogicTreeBranch<?> branch : branches) {
				double weight = getBranchWeight(branch);
				totWeight += weight;
				for (LogicTreeNode node : branch) {
					if (origNodeCounts.containsKey(node)) {
						origNodeCounts.put(node, origNodeCounts.get(node) + 1);
						origNodeWeights.put(node, origNodeWeights.get(node) + weight);
					} else {
						origNodeCounts.put(node, 1);
						origNodeWeights.put(node, weight);
					}
				}
			}
			if (totWeight != 1d)
				for (LogicTreeNode node : List.copyOf(origNodeWeights.keySet()))
					origNodeWeights.put(node, origNodeWeights.get(node)/totWeight);
			printSamplingStats(levels, weightEach, sampledNodeCounts, origNodeCounts, origNodeWeights);
				
		}
		return ret;
	}

	public static void printSamplingStats(List<? extends LogicTreeLevel<?>> levels,
			double sampledWeightEach, Map<LogicTreeNode, Integer> sampledNodeCounts,
			Map<LogicTreeNode, Integer> origNodeCounts, Map<LogicTreeNode, Double> origNodeWeights) {
		Map<LogicTreeNode, Double> sampledNodeWeights = new HashMap<>(sampledNodeCounts.size());
		for (LogicTreeNode node : sampledNodeCounts.keySet())
			sampledNodeWeights.put(node, sampledNodeCounts.get(node)*sampledWeightEach);
		printSamplingStats(levels, sampledNodeCounts, sampledNodeWeights, origNodeCounts, origNodeWeights);
	}

	public static void printSamplingStats(List<? extends LogicTreeLevel<?>> levels,
			Map<LogicTreeNode, Integer> sampledNodeCounts, Map<LogicTreeNode, Double> sampledNodeWeights,
			Map<LogicTreeNode, Integer> origNodeCounts, Map<LogicTreeNode, Double> origNodeWeights) {
		System.out.println("Sampled Logic Tree:");
		DecimalFormat weightDF = new DecimalFormat("0.0000");
		DecimalFormat countDF = new DecimalFormat("0.#");
		countDF.setGroupingSize(3);
		countDF.setGroupingUsed(true);
		for (LogicTreeLevel<?> level : levels) {
			List<LogicTreeNode> origNodes = new ArrayList<>();
			for (LogicTreeNode node : level.getNodes())
				if (origNodeCounts.containsKey(node))
					origNodes.add(node);
			if (origNodes.size() < 2)
				continue;
			System.out.println("\t"+level.getName());
			int minNumSamples = Integer.MAX_VALUE;
			int maxNumSamples = 0;
			int totNumSamples = 0;
			boolean abbreviate = origNodes.size() > 20;
			int abbrevPrintCount = 10;
			for (int i=0; i<origNodes.size(); i++) {
				LogicTreeNode node = origNodes.get(i);
				int origCount = origNodeCounts.get(node);
				double origWeight = origNodeWeights.get(node);
				Integer sampleCount = sampledNodeCounts.get(node);
				if (sampleCount == null)
					sampleCount = 0;
				Double sampledWeight = sampledNodeWeights.get(node);
				if (sampledWeight == null) {
					System.out.println("\t\t"+node.getShortName()+":\tORIG count="+countDF.format(origCount)
						+" weight="+weightDF.format(origWeight)+";\tNO SAMPLES");
					continue;
				}
				System.out.println("\t\t"+node.getShortName()+":\tORIG count="+countDF.format(origCount)
							+" weight="+weightDF.format(origWeight)+";\tSAMPLED count="+countDF.format(sampleCount)
							+" weight="+weightDF.format(sampledWeight));
				if (abbreviate && i == abbrevPrintCount-1) {
					int skipped = 0;
					int skippedOrigCount = 0;
					double skippedOrigWeight = 0d;
					int skippedSampledCount = 0;
					double skippedSampledWeight = 0d;
					for (; i<origNodes.size()-2; i++) {
						node = origNodes.get(i);
						origCount = origNodeCounts.get(node);
						origWeight = origNodeWeights.get(node);
						sampleCount = sampledNodeCounts.get(node);
						if (sampleCount == null)
							sampleCount = 0;
						sampledWeight = sampledNodeWeights.get(node);

						skipped++;
						skippedOrigCount += origCount;
						skippedOrigWeight += origWeight;
						skippedSampledCount += sampleCount;
						skippedSampledWeight += sampledWeight;
						minNumSamples = Integer.min(minNumSamples, sampleCount);
						maxNumSamples = Integer.max(maxNumSamples, sampleCount);
						totNumSamples += sampleCount;
					}
					System.out.println("\t\t(...Skipping "+skipped+" branches with:\tORIG count="+skippedOrigCount
							+", weight="+weightDF.format(skippedOrigWeight)+";\tSAMPLED count="+skippedSampledCount
							+", weight="+weightDF.format(skippedSampledWeight)+"...)");
				} else {
					minNumSamples = Integer.min(minNumSamples, sampleCount);
					maxNumSamples = Integer.max(maxNumSamples, sampleCount);
					totNumSamples += sampleCount;
				}
			}
			System.out.println("\t\t\tSAMPLE COUNTS: ["+minNumSamples+", "+maxNumSamples+"]; avg="
					+countDF.format((double)totNumSamples/(origNodes.size())));
		}
	}
	
	private transient IntegerPDF_FunctionSampler sampler = null;
	public IntegerPDF_FunctionSampler getSampler() {
		if (sampler == null) {
			double[] weights = new double[size()];
			for (int i=0; i<weights.length; i++)
				weights[i] = getBranchWeight(i);
			sampler = new IntegerPDF_FunctionSampler(weights);
		}
		return sampler;
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> buildExhaustive(
			List<LogicTreeLevel<? extends E>> levels, boolean onlyNonZeroWeight, LogicTreeNode... required) {
		return buildExhaustive(levels, onlyNonZeroWeight, new BranchWeightProvider.CurrentWeights(), required);
	}
	
	/**
	 * Builds a complete logic tree from the given levels. If onlyNonZeroWeight == true, then only branches with nonzero
	 * weight will be included. If a {@link BranchWeightProvider} is supplied, then that is used to determine weights,
	 * and it's weight will be set as the original weight in each created branch. 
	 * 
	 * @param <E>
	 * @param levels
	 * @param onlyNonZeroWeight
	 * @param weightProv
	 * @return
	 */
	public static <E extends LogicTreeNode> LogicTree<E> buildExhaustive(
			List<LogicTreeLevel<? extends E>> levels, boolean onlyNonZeroWeight, BranchWeightProvider weightProv, LogicTreeNode... required) {
		List<LogicTreeBranch<E>> branches = new ArrayList<>();
		
		LogicTreeBranch<E> emptyBranch = new LogicTreeBranch<>(levels);
		
		buildBranchesRecursive(levels, branches, emptyBranch, 0, onlyNonZeroWeight, weightProv, required);
		
		return new LogicTree<>(levels, branches, DEFAULT_WEIGHTS);
	}
	
	private static <E extends LogicTreeNode> void buildBranchesRecursive(List<LogicTreeLevel<? extends E>> levels,
			List<LogicTreeBranch<E>> branches, LogicTreeBranch<E> curBranch, int curIndex, boolean onlyNonZeroWeight,
			BranchWeightProvider weightProv, LogicTreeNode[] required) {
		LogicTreeLevel<? extends E> level = levels.get(curIndex);
		for (E node : level.getNodes()) {
			if (onlyNonZeroWeight && weightProv == null && node.getNodeWeight(curBranch) == 0d)
				continue;
			if (required != null) {
				boolean hasRequired = true;
				for (LogicTreeNode requiredNode : required) {
					if (level.isMember(requiredNode)) {
						// there's a requirement for this level
						if (!node.equals(requiredNode)) {
							hasRequired = false;
							break;
						}
					}
				}
				if (!hasRequired)
					continue;
			}
			LogicTreeBranch<E> copy = curBranch.copy();
			copy.setValue(curIndex, node);
			if (onlyNonZeroWeight && weightProv != null && weightProv.getWeight(copy) == 0d)
				continue;
			if (curIndex == levels.size()-1) {
				// fully specified
				Preconditions.checkState(copy.isFullySpecified());
				if (weightProv != null) {
					double weight = weightProv.getWeight(copy);
					copy.setOrigBranchWeight(weight);
					if (onlyNonZeroWeight)
						Preconditions.checkState(weight > 0d);
				} else if (onlyNonZeroWeight) {
					double weight = copy.getBranchWeight();
					Preconditions.checkState(weight > 0d);
				}
				if (required != null) {
					// make sure we actually satisfied all of the requirements
					for (LogicTreeNode requiredNode : required)
						Preconditions.checkState(copy.hasValue(requiredNode), "Built a branch but missed a required node: %s. Full branch: %s", requiredNode.getShortName(), copy);
				}
				branches.add(copy);
			} else {
				// continue to the next level
				buildBranchesRecursive(levels, branches, copy, curIndex+1, onlyNonZeroWeight, weightProv, required);
			}
		}
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> fromExisting(List<LogicTreeLevel<? extends E>> levels,
			Collection<? extends LogicTreeBranch<E>> branches) {
		return new LogicTree<>(levels, branches, DEFAULT_WEIGHTS);
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> buildSampled(
			List<LogicTreeLevel<? extends E>> levels, int numSamples, long seed, LogicTreeNode... required) {
		Random r = new Random(seed);
		List<LogicTreeBranch<E>> branches = new ArrayList<>(numSamples);
		// initialize empty
		double weightEach = 1d/(double)numSamples;
		for (int i=0; i<numSamples; i++) {
			LogicTreeBranch<E> branch = new LogicTreeBranch<>(levels);
			branch.setOrigBranchWeight(weightEach);
			branches.add(branch);
		}
		for (int l=0; l<levels.size(); l++) {
			LogicTreeLevel<?> level = levels.get(l);
			List<? extends LogicTreeNode> samples;
			if (level instanceof RandomLevel<?,?>) {
				((RandomLevel<?,?>)level).build(r.nextLong(), numSamples);
				samples = ((RandomLevel<?,?>)level).getNodes();
				Preconditions.checkState(samples.size() == numSamples);
			} else {
				LogicTreeNode fixed = null;
				if (required != null && required.length > 0) {
					for (LogicTreeNode node : required) {
						if (level.isMember(node)) {
							if (fixed != null)
								throw new IllegalStateException("Multiple required members belong to level "
										+level.getName()+": "+fixed.getName()+" and "+node.getName());
							fixed = node;
						}
					}
				}
				List<LogicTreeNode> mySamples = new ArrayList<>(numSamples);
				if (fixed != null) {
					for (int i=0; i<numSamples; i++)
						mySamples.add(fixed);
				} else {
					List<? extends LogicTreeNode> nodes = level.getNodes();
					if (FixedWeightNode.class.isAssignableFrom(level.getType())) {
						// fixed weights, simple
						List<LogicTreeNode> nonzeroWeightNodes = new ArrayList<>(nodes.size());
						List<Double> nonzeroWeights = new ArrayList<>(nodes.size());
						for (LogicTreeNode node : nodes) {
							double weight = ((FixedWeightNode)node).getNodeWeight();
							if (weight > 0d) {
								nonzeroWeightNodes.add(node);
								nonzeroWeights.add(weight);
							}
						}
						Preconditions.checkState(!nonzeroWeightNodes.isEmpty());
						if (nonzeroWeightNodes.size() == 1) {
							for (int i=0; i<numSamples; i++)
								mySamples.add(nonzeroWeightNodes.get(0));
						} else {
							IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(Doubles.toArray(nonzeroWeights));
							for (int i=0; i<numSamples; i++)
								mySamples.add(nonzeroWeightNodes.get(sampler.getRandomInt(r)));
						}
					} else {
						// potentially-varying based on upstream
						IntegerPDF_FunctionSampler sampler = null;
						double[] curWeights = new double[nodes.size()];
						for (int i=0; i<numSamples; i++) {
							LogicTreeNode firstNonzero = null;
							int numNonZero = 0;
							LogicTreeBranch<E> branch = branches.get(i);
							for (int n=0; n<curWeights.length; n++) {
								LogicTreeNode node = nodes.get(n);
								curWeights[n] = node.getNodeWeight(branch);
								if (curWeights[n] > 0) {
									if (numNonZero == 0)
										firstNonzero = node;
									else
										firstNonzero = null;
									numNonZero++;
								}
								if (sampler != null && curWeights[n] != sampler.getY(n))
									// can't reuse this sampler
									sampler = null;
							}
							if (numNonZero == 1) {
								// only one with nonzero weight
								mySamples.add(firstNonzero);
							} else {
								// multiple, need to sample
								if (sampler == null)
									sampler = new IntegerPDF_FunctionSampler(curWeights);
								mySamples.add(nodes.get(sampler.getRandomInt(r)));
							}
						}
					}
					
				}
				samples = mySamples;
			}
			Preconditions.checkState(samples.size() == numSamples);
			for (int i=0; i<numSamples; i++)
				branches.get(i).setValue(l, (E)samples.get(i));
		}
		
		return fromExisting(levels, branches);
	}
	
	public static LogicTree<LogicTreeNode> unrollTRTs(
			LogicTree<?> inputTree) {
		List<? extends LogicTreeLevel<?>> origLevels = inputTree.getLevels();
		boolean hasTRTs = false;
		for (LogicTreeLevel<?> level : origLevels) {
			if (level instanceof TectonicRegionBranchTreeNode.Level) {
				hasTRTs = true;
				break;
			}
		}
		if (!hasTRTs)
			return null;
		List<LogicTreeLevel<? extends LogicTreeNode>> modLevels = null;
		List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>();
		for (int i=0; i<inputTree.size(); i++) {
			// this preserves origWeight and sets the custom file name to the original
			LogicTreeBranch<LogicTreeNode> branch = TectonicRegionBranchTreeNode.unrollTRTBranches(inputTree.getBranch(i));
			if (i == 0)
				modLevels = branch.getLevels();
			modBranches.add(branch);
		}
		
		return fromExisting(modLevels, modBranches);
	}
	
	public static LogicTree<LogicTreeNode> applyBinning(
			LogicTree<?> inputTree) {
		List<Integer> binnedLevelIndexes = null;
		List<BinnedLevel<?, ?>> binnedLevels = null;
		List<? extends LogicTreeLevel<?>> origLevels = inputTree.getLevels();
		
		for (int l=0; l<origLevels.size(); l++) {
			LogicTreeLevel<?> level = origLevels.get(l);
			if (level instanceof LogicTreeLevel.BinnableLevel<?,?,?>) {
				BinnedLevel<?, ?> binned =
						((LogicTreeLevel.BinnableLevel<?,?,?>)level).toBinnedLevel();
				if (binnedLevelIndexes == null) {
					binnedLevelIndexes = new ArrayList<>();
					binnedLevels = new ArrayList<>();
				}
				binnedLevelIndexes.add(l);
				binnedLevels.add(binned);
				System.out.println("Binning "+level.getName()+":");
				for (LogicTreeNode node : ((LogicTreeLevel<?>)binned).getNodes())
					System.out.println("\t"+node.getName());
				
			}
		}
		if (binnedLevelIndexes == null)
			return null;
		List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>(inputTree.size());
		List<LogicTreeLevel<? extends LogicTreeNode>> modLevels = new ArrayList<>(origLevels.size());
		
		for (int l=0; l<origLevels.size(); l++)
			modLevels.add(origLevels.get(l));
		for (int i=0; i<binnedLevelIndexes.size(); i++)
			modLevels.set(binnedLevelIndexes.get(i), (LogicTreeLevel<?>)binnedLevels.get(i));
		
		for (LogicTreeBranch<?> branch : inputTree) {
			List<LogicTreeNode> values = new ArrayList<>();
			
			for (int l=0; l<origLevels.size(); l++)
				values.add(branch.getValue(l));
			for (int i=0; i<binnedLevelIndexes.size(); i++) {
				int l = binnedLevelIndexes.get(i);
				values.set(l, binnedLevels.get(i).getBinUnchecked(values.get(l)));
			}
			
			LogicTreeBranch<LogicTreeNode> modBranch = new LogicTreeBranch<>(modLevels, values);
			modBranch.setCustomFileName(branch.buildFileName());
			modBranch.setOrigBranchWeight(branch.getOrigBranchWeight());
			
			modBranches.add(modBranch);
		}
		return fromExisting(modLevels, modBranches);
	}
	
	public static void main(String[] args) {
		LogicTree<U3LogicTreeBranchNode<?>> fullU3 = buildExhaustive(U3LogicTreeBranch.getLogicTreeLevels(), true);
		System.out.println("Built "+fullU3.branches.size()+" U3 branches. Weight: "+(float)fullU3.getTotalWeight());
		System.out.println("FM3.1 branches: "+fullU3.matchingAll(FaultModels.FM3_1).branches.size());
		System.out.println("FM3.1or2 branches: "+fullU3.matchingAny(FaultModels.FM3_1, FaultModels.FM3_2).branches.size());
	}

	@Override
	public String getFileName() {
		return "logic_tree.json";
	}

	@Override
	public String getName() {
		return "Logic Tree";
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		Adapter<E> adapter = new Adapter<>();
		adapter.write(out, this);
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		Adapter<E> adapter = new Adapter<>();
		LogicTree<E> tree = adapter.read(in);
		this.levels = tree.levels;
		this.branches = tree.branches;
	}
	
	public void write(File jsonFile) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		gson.toJson(this, LogicTree.class, writer);
		writer.close();
	}
	
	public static LogicTree<LogicTreeNode> read(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return read(reader);
	}
	
	public static LogicTree<LogicTreeNode> read(Reader jsonReader) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.fromJson(jsonReader, TypeToken.getParameterized(LogicTree.class, LogicTreeNode.class).getType());
	}
	
	public LogicTree<E> sorted(Comparator<? super LogicTreeBranch<E>> comparator) {
		List<LogicTreeBranch<E>> sorted = new ArrayList<>(branches);
		sorted.sort(comparator);
		return new LogicTree<>(levels, sorted, weightProvider);
	}
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTree<E>> {

		private final LogicTreeLevel.Adapter<E> levelAdapter;
		private final BranchWeightProvider.Adapter weightAdapter = new BranchWeightProvider.Adapter();
		
		public Adapter() {
			this(new LogicTreeLevel.Adapter<>());
		}
		
		public Adapter(LogicTreeLevel.Adapter<E> levelAdapter) {
			this.levelAdapter = levelAdapter;
		}

		@Override
		public void write(JsonWriter out, LogicTree<E> value) throws IOException {
			out.beginObject();
			
			Class<?> type = null;
			for (LogicTreeBranch<E> branch : value) {
				if (type == null) {
					type = branch.getClass();
				} else if (!type.equals(branch.getClass())) {
					type = null;
					break;
				}
			}
			
			if (type != null)
				out.name("type").value(type.getName());
			
			out.name("levels").beginArray();
			for (LogicTreeLevel<? extends E> level : value.levels)
				levelAdapter.write(out, level);
			out.endArray();
			
			out.name("weightProvider");
			weightAdapter.write(out, value.weightProvider);

			double[] weights = new double[value.branches.size()];
			boolean allSameWeight = true;
			boolean hasCustomFileNames = false;
			out.name("branches").beginArray();
			for (int b=0; b<weights.length; b++) {
				LogicTreeBranch<E> branch = value.branches.get(b);
				hasCustomFileNames |= branch.hasCustomFileName();
				double weight = value.branches.get(b).getOrigBranchWeight();
				weights[b] = weight;
				if (b > 0)
					allSameWeight &= weight == weights[0];
				out.beginArray();
				for (int i=0; i<branch.size(); i++) {
					E node = branch.getValue(i);
					if (node == null)
						out.nullValue();
					else
						out.value(node.getFilePrefix());
				}
				out.endArray();
			}
			out.endArray();
			
			if (hasCustomFileNames) {
				out.name("filePrefixes").beginArray();
				
				for (LogicTreeBranch<E> branch : value.branches)
					out.value(branch.buildFileName());
				
				out.endArray();
			}
			
			if (allSameWeight && weights.length > 1) {
				out.name("origWeightEach").value(weights[0]);
			} else {
				out.name("origWeights").beginArray();
				for (double weight : weights)
					out.value(weight);
				out.endArray();
			}
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public LogicTree<E> read(JsonReader in) throws IOException {
			in.beginObject();
			
			Class<? extends LogicTreeBranch<E>> type = null;
			List<LogicTreeLevel<? extends E>> levels = null;
			List<LogicTreeBranch<E>> branches = null;
			List<Double> origWeights = null;
			Double origWeightEach = null;
			BranchWeightProvider weightProvider = null;
			
			List<String> customFilePrefixes = null;
			
			List<Map<String, E>> nodeMatchCache = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					try {
						type = (Class<? extends LogicTreeBranch<E>>) Class.forName(in.nextString());
					} catch (Exception e) {
						System.err.println("WARNING: can't load branches for logic tree as given type: "+e.getMessage());
					}
					break;
				case "levels":
					levels = new ArrayList<>();
					in.beginArray();
					nodeMatchCache = new ArrayList<>();
					while (in.hasNext()) {
						LogicTreeLevel<E> level = levelAdapter.read(in);
						Map<String, E> cache = new HashMap<>();
						// prepolulate the cache with the perfect match (file prefix) to avoid having to look
						// through the whole list for fuzzy matches (unless needed)
						for (E node : level.getNodes()) {
							String perfectMatch = node.getFilePrefix();
							if (cache.containsKey(perfectMatch)) {
								// duplicates, bad things will happen, don't precache
								cache.clear();
								break;
							} else {
								cache.put(perfectMatch, node);
							}
						}
						nodeMatchCache.add(cache);
						levels.add(level);
					}
					in.endArray();
					break;
				case "weightProvider":
					weightProvider = weightAdapter.read(in);
					break;
				case "branches":
					Preconditions.checkNotNull(levels, "levels must be supplied before branches");
					branches = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						LogicTreeBranch<E> branch;
						if (type == null) {
							branch = new LogicTreeBranch<>(levels);
						} else {
							Constructor<? extends LogicTreeBranch<E>> constructor;
							try {
								constructor = type.getDeclaredConstructor();
								constructor.setAccessible(true);
								branch = constructor.newInstance();
							} catch (Exception e) {
								System.err.println("WARNING: cannot instantiate empty branch as '"+type.getName()
										+"', will load as default type. Exception: "+e.getMessage());
								branch = new LogicTreeBranch<>(levels);
							}
						}
						branch.init(levels, null);
						in.beginArray();
						int index = 0;
						while (in.hasNext()) {
							LogicTreeLevel<? extends E> level = levels.get(index);
							String choice = in.nextString();
							Map<String, E> matchCache = nodeMatchCache.get(index);
							E node = matchCache.get(choice);
							if (node == null) {
								// first time we've encountered this string
								String modChoice = simplifyChoiceString(choice);
								int numFuzzyMatches = 0;
								boolean perfectMatch = false;
								for (E possible : level.getNodes()) {
									if (choice.equals(possible.getFilePrefix())) {
										// perfect match
										Preconditions.checkState(!perfectMatch, "Multiple choices for %s match %s",
												level.getName(), choice);
										node = possible;
										perfectMatch = true;
									}
								}
								if (!perfectMatch) {
									// look for partial matches
									for (E possible : level.getNodes()) {
										// look for a partial match
										boolean match = modChoice.equals(simplifyChoiceString(possible.getShortName()));
										match = match || modChoice.equals(simplifyChoiceString(possible.getFilePrefix()));
										match = match || (possible instanceof Enum<?> &&
												modChoice.equals(simplifyChoiceString(((Enum<?>)possible).name())));
										if (match) {
//											System.out.println(possible.getShortName()+" matches "+choice);
//											System.out.println("\t"+possible.getShortName()+"\t"+possible.getFilePrefix()+"\t"+((Enum<?>)node).name());
											node = possible;
											numFuzzyMatches++;
										}
									}
								}
								Preconditions.checkNotNull(node, "No matching node found for intputName=%s for level %s",
										choice, level.getName());
								Preconditions.checkState(perfectMatch || numFuzzyMatches == 1,
										"%s choices for %s match %s", numFuzzyMatches, level.getName(), choice);
								matchCache.put(choice, node);
							}
							branch.setValue(index, node);
							index++;
						}
						in.endArray();
						branches.add(branch);
					}
					in.endArray();
					break;
				case "filePrefixes":
					customFilePrefixes = branches == null ? new ArrayList<>() : new ArrayList<>(branches.size());
					in.beginArray();
					while (in.hasNext())
						customFilePrefixes.add(in.nextString());
					in.endArray();
					break;
				case "origWeightEach":
					origWeightEach = in.nextDouble();
					break;
				case "origWeights":
					origWeights = new ArrayList<>();
					in.beginArray();
					while (in.hasNext())
						origWeights.add(in.nextDouble());
					in.endArray();
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			if (weightProvider == null)
				weightProvider = DEFAULT_WEIGHTS;
			
			if (customFilePrefixes != null) {
				Preconditions.checkState(customFilePrefixes.size() == branches.size(),
						"branch custom file prefixes size does not match branch count");
				for (int i=0; i<branches.size(); i++) {
					LogicTreeBranch<E> branch = branches.get(i);
					String prefix = customFilePrefixes.get(i);
					if (prefix != null && !prefix.equals(branch.buildFileName()))
						branch.setCustomFileName(prefix);
				}
			}
			
			if (origWeights != null) {
				Preconditions.checkState(origWeights.size() == branches.size(),
						"branch orig weights size does not match branch count");
				for (int i=0; i<branches.size(); i++)
					branches.get(i).setOrigBranchWeight(origWeights.get(i));
			} else if (origWeightEach != null) {
				for (int i=0; i<branches.size(); i++)
					branches.get(i).setOrigBranchWeight(origWeightEach);
			}
			return new LogicTree<>(levels, branches, weightProvider);
		}
		
	}
	
	private static String simplifyChoiceString(String str) {
		str = str.replace(" ", "").replace("_", "").replace(",", "").toLowerCase();
		return str;
	}
	
	public static LogicTree<LogicTreeNode> readFileBacked(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return readFileBacked(reader);
	}
	
	public static LogicTree<LogicTreeNode> readFileBacked(Reader jsonReader) throws IOException {
		LogicTreeLevel.Adapter<LogicTreeNode> levelAdapter = new LogicTreeLevel.Adapter<LogicTreeNode>(true, true); // force file backed
		Adapter<LogicTreeNode> treeAdapter = new Adapter<>(levelAdapter);
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(LogicTreeLevel.class, levelAdapter)
				.registerTypeHierarchyAdapter(LogicTreeLevel.class, levelAdapter)
				.registerTypeAdapter(LogicTree.class, treeAdapter)
				.registerTypeHierarchyAdapter(LogicTree.class, treeAdapter)
				.create();
		return gson.fromJson(jsonReader, TypeToken.getParameterized(LogicTree.class, LogicTreeNode.class).getType());
	}

}
