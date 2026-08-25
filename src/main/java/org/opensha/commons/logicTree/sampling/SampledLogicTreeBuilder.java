package org.opensha.commons.logicTree.sampling;

import java.util.List;
import java.util.Random;

import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.PermutedPointSet;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.PointSetTransform;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.data.sampling.optimization.PointSetHillClimber;
import org.opensha.commons.data.sampling.optimization.QuantizedIncrementalPointSetScorer;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

/** Builds sampled logic trees from generated or externally supplied unit-hypercube point sets. */
public final class SampledLogicTreeBuilder<E extends LogicTreeNode> {

	public static final int PAIRWISE_CONTINUOUS_BINS = 100;

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
		PointSet points = generatePointSet(numSamples, mapper.dimensions(), seed, samplingMethod);
		points = mapper.decorate(points);
		points = optimizeIfRequested(points, samplingMethod, seed);
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

	/** Generates an undecorated point set. Pairwise optimization is performed separately after decoration. */
	public static PointSet generatePointSet(int numSamples, int dimensions, long seed,
			SamplingMethod samplingMethod) {
		Preconditions.checkArgument(numSamples > 0, "NumSamples must be positive");
		Preconditions.checkArgument(dimensions > 0, "Dimensions must be positive");
		Preconditions.checkNotNull(samplingMethod, "Sampling method cannot be null");
		Preconditions.checkArgument(samplingMethod != SamplingMethod.EXTERNAL,
				"External point sets cannot be generated");
		return samplingMethod.createGenerator(seed).generate(numSamples, dimensions);
	}

	/** Generates, decorates, and, when requested by the method, pairwise-optimizes a point set. */
	public static PointSet preparePointSet(int numSamples, List<? extends SamplingDimension> dimensions,
			long seed, SamplingMethod samplingMethod) {
		Preconditions.checkNotNull(dimensions, "Sampling dimensions cannot be null");
		PointSet points = generatePointSet(numSamples, dimensions.size(), seed, samplingMethod);
		points = new DimensionedPointSet(points, dimensions);
		return optimizeIfRequested(points, samplingMethod, seed);
	}

	/** Applies the standard pairwise optimization policy when selected by the sampling method. */
	public static PointSet optimizeIfRequested(PointSet pointSet, SamplingMethod samplingMethod, long seed) {
		Preconditions.checkNotNull(pointSet, "Point set cannot be null");
		Preconditions.checkNotNull(samplingMethod, "Sampling method cannot be null");
		if (!samplingMethod.isPairwiseOptimized() || pointSet.size() < 2)
			return pointSet;
		PermutedPointSet permuted = PermutedPointSet.independentDimensions(pointSet);
		if (permuted.swapGroupCount() < 2)
			return pointSet;
		QuantizedIncrementalPointSetScorer scorer =
				new QuantizedIncrementalPointSetScorer(permuted, PAIRWISE_CONTINUOUS_BINS);
		PointSetHillClimber.optimize(scorer, pairwiseIterations(pointSet.size()), new Random(seed));
		return permuted;
	}

	public static long pairwiseIterations(int numSamples) {
		Preconditions.checkArgument(numSamples > 0, "NumSamples must be positive");
		return Math.min(10_000_000L, Math.max(100_000L, Math.multiplyExact((long)numSamples, 1000L)));
	}
}
