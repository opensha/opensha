package org.opensha.commons.logicTree.sampling;

import java.util.List;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.PointSetTransform;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

/** Builds sampled logic trees from generated or externally supplied unit-hypercube point sets. */
public final class SampledLogicTreeBuilder<E extends LogicTreeNode> {

	private final LogicTreePointSetMapper<E> mapper;
	private PointSetTransform transform;

	public SampledLogicTreeBuilder(List<? extends LogicTreeLevel<? extends E>> levels,
			LogicTreeNode... required) {
		mapper = new LogicTreePointSetMapper<>(levels, required);
	}

	/** Sets an optional transform applied after optimization and before logic-tree mapping. */
	public SampledLogicTreeBuilder<E> transform(PointSetTransform transform) {
		this.transform = transform;
		return this;
	}

	public LogicTree<E> build(int numSamples, long seed, SamplingMethod samplingMethod) {
		PointSet points = samplingMethod.prepare(numSamples, mapper.getSamplingDimensions(), seed);
		return finish(points, samplingMethod, samplingMethod.usesRandomSeed() ? seed : null);
	}

	public LogicTree<E> build(PointSet pointSet) {
		return finish(mapper.decorate(pointSet), SamplingMethod.EXTERNAL, null);
	}

	private LogicTree<E> finish(PointSet pointSet, SamplingMethod samplingMethod, Long samplingRandomSeed) {
		PointSet finalPoints = applyTransform(pointSet);
		LogicTree<E> tree = mapper.map(finalPoints);
		tree.setSamplingParameters(samplingRandomSeed, 0, samplingMethod);
		tree.setSamplingPointSet(finalPoints);
		return tree;
	}

	private PointSet applyTransform(PointSet pointSet) {
		if (transform == null)
			return pointSet;
		PointSet transformed = Preconditions.checkNotNull(transform.apply(pointSet),
				"Point-set transform returned null");
		Preconditions.checkArgument(transformed.size() == pointSet.size(),
				"Point-set transform changed point count from %s to %s", pointSet.size(), transformed.size());
		return mapper.decorate(transformed);
	}

}
