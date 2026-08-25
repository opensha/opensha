package org.opensha.commons.logicTree.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

/** Maps unit-hypercube points to logic-tree branches, one dimension per level. */
public final class LogicTreePointSetMapper<E extends LogicTreeNode> {

	private final List<LogicTreeLevel<? extends E>> levels;
	private final List<E> requiredByLevel;
	private final List<SamplingDimension> dimensions;

	@SafeVarargs
	public LogicTreePointSetMapper(List<? extends LogicTreeLevel<? extends E>> levels, LogicTreeNode... required) {
		if (levels == null || levels.isEmpty())
			throw new IllegalArgumentException("Levels must be non-null and nonempty");
		this.levels = Collections.unmodifiableList(new ArrayList<>(levels));
		this.requiredByLevel = new ArrayList<>(levels.size());
		this.dimensions = new ArrayList<>(levels.size());
		for (LogicTreeLevel<? extends E> level : levels) {
			E fixed = null;
			if (required != null) {
				for (LogicTreeNode node : required) {
					if (level.isMember(node)) {
						if (fixed != null)
							throw new IllegalArgumentException("Multiple required nodes belong to level " + level.getName());
						@SuppressWarnings("unchecked")
						E cast = (E)node;
						fixed = cast;
					}
				}
			}
			requiredByLevel.add(fixed);
			dimensions.add(fixed == null ? level.getSamplingDimension() : InactiveSamplingDimension.INSTANCE);
		}
	}

	public int dimensions() {
		return levels.size();
	}

	public List<SamplingDimension> getSamplingDimensions() {
		return Collections.unmodifiableList(dimensions);
	}

	public DimensionedPointSet decorate(PointSet pointSet) {
		validatePointSet(pointSet);
		return new DimensionedPointSet(pointSet, dimensions);
	}

	@SuppressWarnings("unchecked")
	public LogicTree<E> map(PointSet pointSet) {
		DimensionedPointSet dimensioned = decorate(pointSet);
		int numSamples = pointSet.size();
		double weightEach = 1d/numSamples;
		List<LogicTreeBranch<E>> branches = new ArrayList<>(numSamples);
		for (int p=0; p<numSamples; p++) {
			LogicTreeBranch<E> branch = new LogicTreeBranch<>(levels);
			branch.setOrigBranchWeight(weightEach);
			branches.add(branch);
		}

		for (int l=0; l<levels.size(); l++) {
			LogicTreeLevel<? extends E> level = levels.get(l);
			E required = requiredByLevel.get(l);
			if (required != null) {
				for (LogicTreeBranch<E> branch : branches)
					branch.setValue(l, required);
				continue;
			}
			if (level instanceof RandomLevel<?, ?> randomLevel) {
				randomLevel.build(dimensioned.getDimensionValues(l), weightEach);
				List<? extends LogicTreeNode> nodes = randomLevel.getNodes();
				if (nodes.size() != numSamples)
					throw new IllegalStateException("Random level " + level.getName() + " built " + nodes.size()
							+ " nodes for " + numSamples + " samples");
				for (int p=0; p<numSamples; p++)
					branches.get(p).setValue(l, (E)nodes.get(p));
				continue;
			}
			List<? extends E> nodes = level.getNodes();
			for (int p=0; p<numSamples; p++)
				branches.get(p).setValue(l, select(nodes, branches.get(p), dimensioned.get(p, l)));
		}
		return LogicTree.fromExisting(levels, branches);
	}

	private void validatePointSet(PointSet pointSet) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (pointSet.size() < 1)
			throw new IllegalArgumentException("Point set must contain at least one point");
		if (pointSet.dimensions() != levels.size())
			throw new IllegalArgumentException("Point set has " + pointSet.dimensions() + " dimensions for "
					+ levels.size() + " logic-tree levels");
		for (int p=0; p<pointSet.size(); p++) {
			for (int d=0; d<pointSet.dimensions(); d++) {
				double coordinate = pointSet.get(p, d);
				if (!Double.isFinite(coordinate) || coordinate < 0d || coordinate >= 1d)
					throw new IllegalArgumentException("Coordinate [" + p + "][" + d
							+ "] must be finite and in [0,1), have " + coordinate);
			}
		}
	}

	private static <E extends LogicTreeNode> E select(List<? extends E> nodes, LogicTreeBranch<?> branch,
			double unitSample) {
		double sum = 0d;
		for (E node : nodes) {
			double weight = node.getNodeWeight(branch);
			if (!Double.isFinite(weight) || weight < 0d)
				throw new IllegalStateException("Node " + node.getName() + " has invalid weight " + weight);
			sum += weight;
		}
		if (!(sum > 0d))
			throw new IllegalStateException("No positive node weights are available");
		double target = unitSample*sum;
		double cumulative = 0d;
		E lastPositive = null;
		for (E node : nodes) {
			double weight = node.getNodeWeight(branch);
			if (weight <= 0d)
				continue;
			lastPositive = node;
			cumulative += weight;
			if (target < cumulative)
				return node;
		}
		return lastPositive;
	}
}
