package org.opensha.commons.logicTree;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
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
import org.opensha.commons.logicTree.BranchWeightProvider.OriginalWeights;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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
	 * @param numSamples number of random samples
	 * @param redrawDuplicates if true, each branch will be unique, redrawing a branch if an already sampled branch
	 * has been selected, otherwise duplicate branches will be given additional weight and the returned branch count
	 * may be less than the input number of samples
	 * @return a randomly sampled subset of this logic tree, according to their weights. The returned logic tree will
	 * use a {@link BranchWeightProvider} instance modified to reflect the even (post-sampling) weights.
	 */
	public final LogicTree<E> sample(int numSamples, boolean redrawDuplicates) {
		return sample(numSamples, redrawDuplicates, new Random());
	}
	
	/**
	 * @param numSamples number of random samples
	 * @param redrawDuplicates if true, each branch will be unique, redrawing a branch if an already sampled branch
	 * has been selected, otherwise duplicate branches will be given additional weight and the returned branch count
	 * may be less than the input number of samples
	 * @param rand random number generator
	 * @return a randomly sampled subset of this logic tree, according to their weights. The returned logic tree will
	 * use a {@link BranchWeightProvider} instance modified to reflect the even (post-sampling) weights.
	 */
	public final LogicTree<E> sample(int numSamples, boolean redrawDuplicates, Random rand) {
		System.out.println("Resampling logic tree of size="+size()+" to "+numSamples+" samples...");
		Preconditions.checkArgument(numSamples > 0);
		Preconditions.checkState(!redrawDuplicates || numSamples <= size(),
				"Cannot randomly sample %s branches from %s values without any duplicates!", numSamples, size());
		double[] weights = new double[size()];
		for (int i=0; i<weights.length; i++)
			weights[i] = getBranchWeight(i);
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(weights);
		HashMap<Integer, Integer> indexCounts = new HashMap<>();
		for (int i=0; i<numSamples; i++) {
			int index = sampler.getRandomInt(rand);
			int prevCount = indexCounts.containsKey(index) ? indexCounts.get(index) : 0;
			while (redrawDuplicates && prevCount > 0)
				index = sampler.getRandomInt(rand);
			indexCounts.put(index, prevCount+1);
		}
		double weightEach = 1d/numSamples;
		ImmutableList.Builder<LogicTreeBranch<E>> samples = ImmutableList.builder();
		Map<LogicTreeNode, Integer> sampledNodeCounts = new HashMap<>();
		int mostSamples = 0;
		for (int index : indexCounts.keySet()) {
			int count = indexCounts.get(index);
			mostSamples = Integer.max(mostSamples, count);
			LogicTreeBranch<E> branch = getBranch(index).copy();
			branch.setOrigBranchWeight((double)count*weightEach);
			samples.add(branch);
			for (LogicTreeNode node : branch) {
				if (sampledNodeCounts.containsKey(node))
					sampledNodeCounts.put(node, sampledNodeCounts.get(node)+count);
				else
					sampledNodeCounts.put(node, count);
			}
		}
		// if we don't have any duplicates we can just set it to constant values
		// otherwise, we'll use the 'original weights' which we have just overridden
		BranchWeightProvider weightProv = indexCounts.size() == numSamples ?
				new BranchWeightProvider.ConstantWeights(weightEach) : new BranchWeightProvider.OriginalWeights();
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>(weightProv);
		ret.branches = samples.build();
		ret.levels = levels;
		
		System.out.println("\tSampled "+indexCounts.size()+" unique branches. The most any single "
				+ "branch was sampled is "+mostSamples+" time(s).");
		System.out.println("Sampled Logic Tree:");
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
		for (LogicTreeLevel<? extends E> level : levels) {
			List<LogicTreeNode> origNodes = new ArrayList<>();
			for (E node : level.getNodes())
				if (origNodeCounts.containsKey(node))
					origNodes.add(node);
			if (origNodes.size() < 2)
				continue;
			System.out.println("\t"+level.getName());
			for (LogicTreeNode node : origNodes) {
				int origCount = origNodeCounts.get(node);
				double origWeight = origNodeWeights.get(node)/totWeight;
				int sampleCount = sampledNodeCounts.get(node);
				double sampledWeight = sampleCount*weightEach;
				System.out.println("\t\t"+node.getShortName()+": ORIG count="+origCount+", weight="+(float)origWeight
						+";\tSAMPLED count="+sampleCount+", weight="+(double)sampledWeight);
			}
		}
		return ret;
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> buildExhaustive(
			List<LogicTreeLevel<? extends E>> levels, boolean onlyNonZeroWeight) {
		List<LogicTreeBranch<E>> branches = new ArrayList<>();
		
		LogicTreeBranch<E> emptyBranch = new LogicTreeBranch<>(levels);
		
		buildBranchesRecursive(levels, branches, emptyBranch, 0, onlyNonZeroWeight);
		
		return new LogicTree<>(levels, branches, DEFAULT_WEIGHTS);
	}
	
	private static <E extends LogicTreeNode> void buildBranchesRecursive(List<LogicTreeLevel<? extends E>> levels,
			List<LogicTreeBranch<E>> branches, LogicTreeBranch<E> curBranch, int curIndex, boolean onlyNonZeroWeight) {
		for (E node : levels.get(curIndex).getNodes()) {
			if (onlyNonZeroWeight && node.getNodeWeight(curBranch) == 0d)
				continue;
			LogicTreeBranch<E> copy = curBranch.copy();
			copy.setValue(curIndex, node);
			if (curIndex == levels.size()-1) {
				// fully specified
				Preconditions.checkState(copy.isFullySpecified());
				if (onlyNonZeroWeight)
					Preconditions.checkState(copy.getBranchWeight() > 0d);
				branches.add(copy);
			} else {
				// continue to the next level
				buildBranchesRecursive(levels, branches, copy, curIndex+1, onlyNonZeroWeight);
			}
		}
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> fromExisting(List<LogicTreeLevel<? extends E>> levels,
			Collection<? extends LogicTreeBranch<E>> branches) {
		return new LogicTree<>(levels, branches, DEFAULT_WEIGHTS);
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
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		gson.toJson(this, LogicTree.class, writer);
		writer.close();
	}
	
	public static LogicTree<LogicTreeNode> read(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.fromJson(reader, TypeToken.getParameterized(LogicTree.class, LogicTreeNode.class).getType());
	}
	
	public LogicTree<E> sorted(Comparator<? super LogicTreeBranch<E>> comparator) {
		List<LogicTreeBranch<E>> sorted = new ArrayList<>(branches);
		sorted.sort(comparator);
		return new LogicTree<>(levels, sorted, weightProvider);
	}
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTree<E>> {

		private final LogicTreeLevel.Adapter<E> levelAdapter = new LogicTreeLevel.Adapter<>();
		private final BranchWeightProvider.Adapter weightAdapter = new BranchWeightProvider.Adapter();

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
			
			out.name("branches").beginArray();
			for (LogicTreeBranch<E> branch : value.branches) {
				out.beginArray();
				for (int i=0; i<branch.size(); i++) {
					E node = branch.getValue(i);
					if (node == null)
						out.nullValue();
					else
						out.value(node.getShortName());
				}
				out.endArray();
			}
			out.endArray();
			
			out.name("origWeights").beginArray();
			for (LogicTreeBranch<E> branch : value.branches)
				out.value(branch.getOrigBranchWeight());
			out.endArray();
			
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
			BranchWeightProvider weightProvider = null;
			
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
					while (in.hasNext())
						levels.add(levelAdapter.read(in));
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
							String shortName = in.nextString();
							E node = null;
							for (E possible : level.getNodes()) {
								if (possible.getShortName().equals(shortName) || possible.getFilePrefix().equals(shortName)) {
									node = possible;
									break;
								}
							}
							Preconditions.checkNotNull(node, "No matching node found with shortName=%s for level %s",
									shortName, level.getName());
							branch.setValue(index, node);
							index++;
						}
						in.endArray();
						branches.add(branch);
					}
					in.endArray();
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
			
			if (origWeights != null) {
				Preconditions.checkState(origWeights.size() == branches.size(),
						"branch orig weights size does not match branch count");
				for (int i=0; i<branches.size(); i++)
					branches.get(i).setOrigBranchWeight(origWeights.get(i));
			}
			return new LogicTree<>(levels, branches, weightProvider);
		}
		
	}

}
