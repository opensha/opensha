package org.opensha.commons.logicTree.sampling;

import java.util.List;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.PointSetTransform;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.sampling.SamplingMethod.PreparedPointSet;

import com.google.common.base.Preconditions;

/** Builds sampled logic trees from generated or externally supplied unit-hypercube point sets. */
public final class SampledLogicTreeBuilder<E extends LogicTreeNode> {

	private final LogicTreePointSetMapper<E> mapper;
	private PointSetTransform transform;
	private int storedPointSetDimensions = -1;

	public SampledLogicTreeBuilder(List<? extends LogicTreeLevel<? extends E>> levels,
			LogicTreeNode... required) {
		mapper = new LogicTreePointSetMapper<>(levels, required);
	}

	/** Sets an optional transform applied after optimization and before logic-tree mapping. */
	public SampledLogicTreeBuilder<E> transform(PointSetTransform transform) {
		this.transform = transform;
		return this;
	}

	/**
	 * Sets the width of the point set retained with the sampled tree. It must be at least the number of dimensions
	 * currently mapped to logic-tree levels; additional dimensions are generated and stored for future extensions.
	 */
	public SampledLogicTreeBuilder<E> storedPointSetDimensions(int storedPointSetDimensions) {
		Preconditions.checkArgument(storedPointSetDimensions > 0,
				"Stored point-set dimension count must be positive");
		this.storedPointSetDimensions = storedPointSetDimensions;
		return this;
	}

	public LogicTree<E> build(int numSamples, long seed, SamplingMethod samplingMethod) {
		int mappedDimensions = mapper.dimensions();
		int storedDimensions = storedPointSetDimensions < 0 ? mappedDimensions : storedPointSetDimensions;
		Preconditions.checkArgument(storedDimensions >= mappedDimensions,
				"Stored point-set dimension count %s is less than mapped dimension count %s",
				storedDimensions, mappedDimensions);
		PreparedPointSet prepared = samplingMethod.prepare(
				numSamples, mapper.getSamplingDimensions(), storedDimensions, seed);
		PointSet finalMappedPoints = applyTransform(prepared.mappedPointSet());
		PointSet finalStoredPoints = transform == null ? prepared.pointSet()
				: prepared.replaceMappedPointSet(finalMappedPoints);
		LogicTree<E> tree = mapper.map(finalMappedPoints);
		tree.setSamplingParameters(samplingMethod.usesRandomSeed() ? seed : null, 0, samplingMethod);
		tree.setSamplingPointSet(finalStoredPoints, SamplingPointSetLayout.DIRECT,
				prepared.dimensionIndexes());
		return tree;
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
