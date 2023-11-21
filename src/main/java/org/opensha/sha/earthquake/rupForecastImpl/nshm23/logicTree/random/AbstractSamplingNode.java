package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.opensha.commons.calc.WeightedSampler;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlySampledNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

abstract class AbstractSamplingNode<E extends BranchDependentSampler<E>> implements RandomlySampledNode {
	
	private String name;
	private String shortName;
	private String prefix;
	private double weight;
	private long seed;

	protected AbstractSamplingNode() {};
	
	protected AbstractSamplingNode(String name, String shortName, String prefix, double weight, long seed) {
		init(name, shortName, prefix, weight, seed);
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return prefix;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long getSeed() {
		return seed;
	}

	@Override
	public void init(String name, String shortName, String prefix, double weight, long seed) {
		this.name = name;
		this.shortName = shortName;
		this.prefix = prefix;
		this.weight = weight;
		this.seed = seed;
	}
	
	/**
	 * Builds a sampler for the given rupture set. Depending on the implementation, this branch may choose to use
	 * the node-specific seed returned by {@link #getSeed()} or the passed in <code>branchNodeSamplingSeed</code>.
	 * The latter will be unique to this node on this particular branch, useful if you want unique (but still
	 * repeatable) randomness on each branch that uses this same node.
	 * 
	 * @param rupSet
	 * @param branch
	 * @param branchNodeSamplingSeed random seed that is uniquely determined from this node's seed and the values at
	 * every other branching level for this branch
	 * @return
	 */
	public abstract E buildSampler(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch, long branchNodeSamplingSeed);
	
	/**
	 * Convenience method for building a {@link WeightedSampler} instance for a list of logic tree nodes
	 * 
	 * @param <E>
	 * @param rand
	 * @param nodes
	 * @return
	 */
	static <E extends LogicTreeNode> WeightedSampler<E> weightedNodeValueSampler(Random rand, List<E> nodes) {
		List<E> filteredNodes = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		for (E node : nodes) {
			double weight = node.getNodeWeight(null);
			if (weight > 0d) {
				filteredNodes.add(node);
				weights.add(node.getNodeWeight(null));
			}
		}
		return new WeightedSampler<>(filteredNodes, weights, rand);
	}
	
	/**
	 * Convenience method for building a {@link WeightedSampler} instance for a logic tree enum
	 * 
	 * @param <E>
	 * @param rand
	 * @param nodes
	 * @return
	 */
	static <E extends Enum<E> & LogicTreeNode> WeightedSampler<E> weightedNodeValueSampler(Random rand, Class<E> enumClass) {
		List<E> nodes = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		for (E node : enumClass.getEnumConstants()) {
			double weight = node.getNodeWeight(null);
			if (weight > 0d) {
				nodes.add(node);
				weights.add(node.getNodeWeight(null));
			}
		}
		return new WeightedSampler<>(nodes, weights, rand);
	}

}
