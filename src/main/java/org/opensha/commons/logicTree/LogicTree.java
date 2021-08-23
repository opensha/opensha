package org.opensha.commons.logicTree;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
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
	
	@SuppressWarnings("unused")
	private LogicTree() {
		// for serialization
	}
	
	protected LogicTree(List<LogicTreeLevel<? extends E>> levels, Collection<? extends LogicTreeBranch<E>> branches) {
		Preconditions.checkState(levels != null);
		Preconditions.checkState(branches != null);
		this.levels = ImmutableList.copyOf(levels);
		this.branches = ImmutableList.copyOf(branches);
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
	public final LogicTree<E> matchingAll(E... values) {
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builder();
		for (LogicTreeBranch<E> branch : branches) {
			boolean matches = true;
			for (E value : values) {
				if (!branch.hasValue(value)) {
					matches = false;
					break;
				}
			}
			if (matches)
				matching.add(branch);
		}
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>();
		ret.branches = matching.build();
		ret.levels = levels;
		return ret;
	}
	
	/**
	 * @param values
	 * @return a subset of this logic tree where each branch contains at least one of the given values
	 */
	@SafeVarargs
	public final LogicTree<E> matchingAny(E... values) {
		ImmutableList.Builder<LogicTreeBranch<E>> matching = ImmutableList.builder();
		for (LogicTreeBranch<E> branch : branches) {
			boolean matches = false;
			for (E value : values) {
				if (branch.hasValue(value)) {
					matches = true;
					break;
				}
			}
			if (matches)
				matching.add(branch);
		}
		// do it this way to skip consistency checks
		LogicTree<E> ret = new LogicTree<>();
		ret.branches = matching.build();
		ret.levels = levels;
		return ret;
	}
	
	public static <E extends LogicTreeNode> LogicTree<E> buildExhaustive(
			List<LogicTreeLevel<? extends E>> levels, boolean onlyNonZeroWeight) {
		List<LogicTreeBranch<E>> branches = new ArrayList<>();
		
		LogicTreeBranch<E> emptyBranch = new LogicTreeBranch<>(levels);
		
		buildBranchesRecursive(levels, branches, emptyBranch, 0, onlyNonZeroWeight);
		
		return new LogicTree<>(levels, branches);
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
		return new LogicTree<>(levels, branches);
	}
	
	public static void main(String[] args) {
		LogicTree<LogicTreeBranchNode<?>> fullU3 = buildExhaustive(U3LogicTreeBranch.getLogicTreeLevels(), true);
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
	
	public LogicTree<E> sorted(Comparator<? super LogicTreeBranch<E>> comparator) {
		List<LogicTreeBranch<E>> sorted = new ArrayList<>(branches);
		sorted.sort(comparator);
		return new LogicTree<>(levels, sorted);
	}
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTree<E>> {
		
		private final LogicTreeLevel.Adapter<E> levelAdapter = new LogicTreeLevel.Adapter<>();

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
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public LogicTree<E> read(JsonReader in) throws IOException {
			in.beginObject();
			
			Class<? extends LogicTreeBranch<E>> type = null;
			List<LogicTreeLevel<? extends E>> levels = null;
			List<LogicTreeBranch<E>> branches = null;
			
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
								if (possible.getShortName().equals(shortName)) {
									node = possible;
									break;
								}
							}
							Preconditions.checkNotNull(node, "No matching node found with shortName=%s for level %s",
									shortName, level.getName());
							branch.setValue(node);
							index++;
						}
						in.endArray();
						branches.add(branch);
					}
					in.endArray();
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			return new LogicTree<>(levels, branches);
		}
		
	}

}
