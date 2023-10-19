package org.opensha.commons.logicTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(BranchWeightProvider.Adapter.class)
public interface BranchWeightProvider {
	
	/**
	 * Returns the weight for the given {@link LogicTreeBranch}.
	 * 
	 * @param branch
	 * @return weight (should be normalized externally)
	 */
	public double getWeight(LogicTreeBranch<?> branch);
	
	/**
	 * This weight provider returns the weight that was calculated when each branch was first instantiated, ignoring
	 * any later changes to weighting of involved nodes
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalWeights implements BranchWeightProvider {

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return branch.getOrigBranchWeight();
		}
		
	}
	
	/**
	 * This weight provider just passes through to return the curren calculated weight for each branch
	 * 
	 * @author kevin
	 *
	 */
	public static class CurrentWeights implements BranchWeightProvider {

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return branch.getBranchWeight();
		}
		
	}
	
	/**
	 * This weight provider assigns constant weights to each logic tree branch
	 * 
	 * @author kevin
	 *
	 */
	public static class ConstantWeights implements BranchWeightProvider {
		
		private double weightEach;

		public ConstantWeights() {
			this(1d);
		}
		
		public ConstantWeights(double weightEach) {
			this.weightEach = weightEach;
		}

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return weightEach;
		}
		
	}
	
	/**
	 * This weight provider assigns constant weights to each logic tree branch
	 * 
	 * @author kevin
	 *
	 */
	public static class HardcodedWeights implements BranchWeightProvider {
		
		private Map<LogicTreeBranch<?>, Double> weights;

		public HardcodedWeights(Map<LogicTreeBranch<?>, Double> weights) {
			this.weights = weights;
		}

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			Double weight = weights.get(branch);
			Preconditions.checkState(weight != null, "No hardcoded weight exists for branch: %s", branch);
			return weight;
		}
		
	}
	
	public static class Adapter extends TypeAdapter<BranchWeightProvider> {
		
		private Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, BranchWeightProvider value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			
			out.beginObject();
			
			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);
			
			out.endObject();
		}

		@Override
		public BranchWeightProvider read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL)
				return null;
			
			in.beginObject();
			
			Class<? extends BranchWeightProvider> type = null;
			BranchWeightProvider ret = null;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					String typeStr = in.nextString();
					try {
						type = (Class<? extends BranchWeightProvider>) Class.forName(typeStr);
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					break;
				case "data":
					Preconditions.checkNotNull(type, "type must be specified before adapter data");
					ret = gson.fromJson(in, type);
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			Preconditions.checkNotNull(ret, "Missing 'data' and/or 'type' field, can't deserialize");
			return ret;
		}
		
	}
	
	public static class NodeWeightOverrides implements BranchWeightProvider {
		
		private Map<LogicTreeNode, Double> nodeWeights;
		
		public NodeWeightOverrides(LogicTreeNode[] nodes, double weight) {
			nodeWeights = new HashMap<>();
			for (LogicTreeNode node : nodes)
				nodeWeights.put(node, weight);
		}

		public NodeWeightOverrides(Map<LogicTreeNode, Double> nodeWeights) {
			this.nodeWeights = nodeWeights;
		}

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			double wt = 1;
			for (LogicTreeNode value : branch) {
				if (value != null) {
					Double hardcoded = nodeWeights.get(value);
					if (hardcoded != null)
						wt *= hardcoded;
					else
						wt *= value.getNodeWeight(branch);
				}
			}
			return wt;
		}
		
	}
	
	public static class TopDownWeights implements BranchWeightProvider {
		
		private LogicTree<?> tree;
		private Map<LogicTreeBranch<?>, Double> weights;

		public TopDownWeights(LogicTree<?> tree) {
			this.tree = tree;
		}

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			Preconditions.checkState(tree.contains(branch), "Given branch is not part of the logic tree: %s", branch);
			checkCalcWeights();
			return weights.get(branch);
		}
		
		private void checkCalcWeights() {
			if (weights != null)
				return;
			synchronized (this) {
				if (weights != null)
					return;
				
				double[] weightsArray = new double[tree.size()];
				for (int i=0; i<weightsArray.length; i++)
					weightsArray[i] = Double.NaN;
				
				Map<LogicTreeNode, List<LogicTreeBranch<?>>> nodeBranchesMap = new HashMap<>();
				Map<LogicTreeBranch<?>, Integer> branchIndexMap = new HashMap<>();
				for (int i=0; i<weightsArray.length; i++) {
					LogicTreeBranch<?> branch = tree.getBranch(i);
					branchIndexMap.put(branch, i);
					for (LogicTreeNode node : branch) {
						List<LogicTreeBranch<?>> nodeBranches = nodeBranchesMap.get(node);
						if (nodeBranches == null) {
							nodeBranches = new ArrayList<>();
							nodeBranchesMap.put(node, nodeBranches);
						}
						nodeBranches.add(branch);
					}
				}
				
				weightProcessRecursive(weightsArray, nodeBranchesMap, branchIndexMap, 0, null, 1d);
				
				Map<LogicTreeBranch<?>, Double> weights = new HashMap<>();
				
				for (int i=0; i<weightsArray.length; i++) {
					LogicTreeBranch<?> branch = tree.getBranch(i);
					Preconditions.checkState(Double.isFinite(weightsArray[i]), "Bad weight=%s for branch %s: %s",
							weightsArray[i], i, branch);
					weights.put(branch, weightsArray[i]);
				}
				
				this.weights = weights;
			}
		}
		
		private void weightProcessRecursive(double[] weightsArray,
				Map<LogicTreeNode, List<LogicTreeBranch<?>>> nodeBranchesMap,
				Map<LogicTreeBranch<?>, Integer> branchIndexMap,
				int startingLevelIndex, LogicTreeBranch<LogicTreeNode> upstreamBranch, double upstreamWeight) {
			Preconditions.checkState(upstreamWeight > 0,
					"Bad upstreamWeight=%s for upstreamBranch=%s", upstreamWeight, upstreamBranch);
			LogicTreeLevel<?> level = tree.getLevels().get(startingLevelIndex);
			
			boolean lastLevel = startingLevelIndex == tree.getLevels().size()-1;
			
			double sumWeight = 0d;
			
			List<LogicTreeNode> nodes = new ArrayList<>();
			List<List<LogicTreeBranch<?>>> nodeBranches = new ArrayList<>();
			List<Double> nodeWeights = new ArrayList<>();
			
			for (LogicTreeNode node : level.getNodes()) {
				List<LogicTreeBranch<?>> allNodeBranches = nodeBranchesMap.get(node);
				if (allNodeBranches == null)
					continue;
				double nodeWeight = node.getNodeWeight(upstreamBranch);
				List<LogicTreeBranch<?>> myNodeBranches = new ArrayList<>();
				for (LogicTreeBranch<?> branch : allNodeBranches) {
					boolean match = true;
					if (upstreamBranch != null) {
						for (LogicTreeNode upstreamNode : upstreamBranch) {
							if (!branch.hasValue(upstreamNode)) {
								match = false;
								break;
							}
						}
					}
					if (match)
						myNodeBranches.add(branch);
				}
				if (!myNodeBranches.isEmpty()) {
					nodes.add(node);
					nodeBranches.add(myNodeBranches);
					nodeWeights.add(nodeWeight);
					sumWeight += nodeWeight;
				}
			}
			
			Preconditions.checkState(sumWeight > 0d, "sumWeight=%s for level %s with upstream %s",
					sumWeight, level.getName(), upstreamBranch);
			
			List<LogicTreeLevel<?>> downstreamLevels = null;
			if (!lastLevel) {
				downstreamLevels = new ArrayList<>();
				if (upstreamBranch != null)
					for (LogicTreeLevel<?> upLevel : upstreamBranch.getLevels())
						downstreamLevels.add(upLevel);
				downstreamLevels.add(level);
			}
			
			for (int n=0; n<nodes.size(); n++) {
				LogicTreeNode node = nodes.get(n);
				double nodeWeight = nodeWeights.get(n);
				if (sumWeight != 1d)
					nodeWeight /= sumWeight;
				List<LogicTreeBranch<?>> branches = nodeBranches.get(n);
				
				Preconditions.checkState(nodeWeight > 0, "Bad nodeWeight=%s for node %s and upstreamBranch=%s",
						nodeWeight, node.getName(), upstreamBranch);
				
				if (lastLevel) {
					// set weights
					for (LogicTreeBranch<?> branch : branches) {
						int index = branchIndexMap.get(branch);
						weightsArray[index] = upstreamWeight*nodeWeight;
					}
				} else {
					// continue down the tree
					LogicTreeBranch<LogicTreeNode> downstreamBranch =
							new LogicTreeBranch<LogicTreeNode>(downstreamLevels);
					if (upstreamBranch != null)
						for (LogicTreeNode upstreamNode : upstreamBranch)
							downstreamBranch.setValue(upstreamNode);
					downstreamBranch.setValue(node);
					
					double downstreamWeight = upstreamWeight*nodeWeight;
					
					weightProcessRecursive(weightsArray, nodeBranchesMap, branchIndexMap,
							startingLevelIndex+1, downstreamBranch, downstreamWeight);
				}
			}
		}
		
	}

}
