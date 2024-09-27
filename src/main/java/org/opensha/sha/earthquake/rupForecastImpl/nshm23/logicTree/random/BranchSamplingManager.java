package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlySampledNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSetSplitMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;

import com.google.common.base.Preconditions;

/**
 * This class manages instances of and random seeds used by {@link AbstractSamplingNode}. It also handles building
 * subsets of those instances for the case of a {@link ClusterSpecificInversionSolver}.
 */
public class BranchSamplingManager implements SplittableRuptureModule<BranchSamplingManager> {
	
	private FaultSystemRupSet rupSet;
	private LogicTreeBranch<?> branch;
	
	// random seeds for each branch node that will be the same on any branch using that node
	private long[] nodeSpecificSeeds;
	// random seeds for each branch node that will be unique to that node on this specific branch
	private long[] branchNodeUniqueSeeds;
	
	private List<BranchDependentSampler<?>> samplers;
	
	/**
	 * 
	 * @param branch
	 * @return true if this branch contains any {@link AbstractSamplingNode} instances, false otherwise
	 */
	public static boolean hasSamplingNodes(LogicTreeBranch<?> branch) {
		for (LogicTreeNode node : branch)
			if (node instanceof AbstractSamplingNode<?>)
				return true;
		return false;
	}

	public BranchSamplingManager(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		this.rupSet = rupSet;
		this.branch = branch;
		
		nodeSpecificSeeds = new long[branch.size()];
		for (int l=0; l<branch.size(); l++) {
			LogicTreeLevel<?> level = branch.getLevel(l);
			LogicTreeNode node = branch.getValue(l);
			// determine a random seed for each branch that is repeatable and unique
			long nodeSeed;
			if (node instanceof RandomlySampledNode)
				// use the assigned random seed for this node
				nodeSeed = ((RandomlySampledNode)node).getSeed();
			else
				// generate one using the level and node names
				nodeSeed = Objects.hash(level.getName(), node.getName());
			
			nodeSpecificSeeds[l] = nodeSeed;
		}
		
		// now generate branchNodeUniqueSeeds that are unique both to the node and to the branch
		long overallSeed = uniqueSeedCombination(nodeSpecificSeeds);
		Random rand = new Random(overallSeed);
		branchNodeUniqueSeeds = new long[branch.size()];
		for (int l=0; l<branchNodeUniqueSeeds.length; l++)
			branchNodeUniqueSeeds[l] = rand.nextLong();
	}
	
	private BranchSamplingManager(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch, long[] nodeSpecificSeeds,
			long[] branchNodeUniqueSeeds, List<BranchDependentSampler<?>> samplers) {
		Preconditions.checkState(samplers != null && samplers.size() == branch.size(), "Samplers must be initialized");
		this.rupSet = rupSet;
		this.branch = branch;
		this.nodeSpecificSeeds = nodeSpecificSeeds;
		this.branchNodeUniqueSeeds = branchNodeUniqueSeeds;
	}
	
	/**
	 * Generates a 64-bit random seed that is a repeatable and psuedo-unique combination of the input seeds.
	 * 
	 * This is based on {@link Arrays#hashCode(int[])}, but modified for longs.
	 * 
	 * @param seeds
	 * @return
	 */
	public static long uniqueSeedCombination(long[] seeds) {
		if (seeds == null)
			return 0l;
		
		long result = 1;
		for (long element : seeds)
			result = 31l * result + element;
		return result;
	}
	
	private void checkInitSamplers() {
		if (samplers == null) {
			synchronized (this) {
				if (samplers == null) {
					List<BranchDependentSampler<?>> samplers = new ArrayList<>(branch.size());
					for (int i=0; i<branch.size(); i++) {
						LogicTreeNode node = branch.getValue(i);
						if (node instanceof AbstractSamplingNode<?>) {
							samplers.add(((AbstractSamplingNode<?>)node).buildSampler(rupSet, branch, branchNodeUniqueSeeds[i]));
						} else {
							samplers.add(null);
						}
					}
					this.samplers = samplers;
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public <E extends BranchDependentSampler<E>> E getSampler(AbstractSamplingNode<E> node) {
		int index = nodeIndex(node);
		checkInitSamplers();
		BranchDependentSampler<?> sampler = samplers.get(index);
		return (E)sampler;
	}
	
	private int nodeIndex(LogicTreeNode node) {
		int ret = -1;
		String name = node.getName();
		for (int i=0; i<branch.size(); i++) {
			LogicTreeNode oNode = branch.getValue(i);
			if (oNode != null && oNode.equals(node)) {
				Preconditions.checkState(ret < 0, "Node %s matches multiple levels", name);
				ret = i;
			}
		}
		Preconditions.checkState(ret >= 0, "Node '%s' not found on our branch: %s", name, branch);
		return ret;
	}

	@Override
	public String getName() {
		return "Branch Sampling Manager";
	}

	@Override
	public BranchSamplingManager getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		checkInitSamplers();
		List<BranchDependentSampler<?>> subsetSamplers = new ArrayList<>(samplers.size());
		for (BranchDependentSampler<?> sampler : samplers) {
			if (sampler == null)
				subsetSamplers.add(null);
			else
				subsetSamplers.add(sampler.getForRuptureSubSet(rupSubSet, mappings));
		}
		return new BranchSamplingManager(rupSubSet, branch, nodeSpecificSeeds, branchNodeUniqueSeeds, subsetSamplers);
	}

	@Override
	public BranchSamplingManager getForSplitRuptureSet(FaultSystemRupSet splitRupSet,
			RuptureSetSplitMappings mappings) {
		throw new UnsupportedOperationException();
	}

}
