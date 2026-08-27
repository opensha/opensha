package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.scoring.ExactPointSetData.PreparedDimension;

/**
 * Optimized exact product-kernel discrepancy scorer. It visits each point pair once, evaluates each needed
 * one-dimensional kernel at most once for that pair, and uses a prepared projection tree to reuse partial products
 * between projections. Fully categorical projections use exact joint-category counts instead.
 * <p>
 * This implementation remains unquantized and deterministic. Its dominant continuous-projection cost is
 * {@code O(N^2*(d+P))}, where {@code d} is the number of used dimensions and {@code P} the number of prepared
 * projection-tree nodes. It uses {@code O(T*P)} accumulator memory for {@code T} workers and does not materialize
 * point-pair matrices. See {@link ReferenceExactPointSetScorer} for the direct formula-oriented implementation.
 */
public final class ExactPointSetScorer implements PointSetScorer {

	private final int parallelism;

	/** Builds a serial exact scorer. */
	public ExactPointSetScorer() {
		this(1);
	}

	/** @param parallelism maximum number of point-pair ranges processed concurrently */
	public ExactPointSetScorer(int parallelism) {
		if (parallelism < 1)
			throw new IllegalArgumentException("Parallelism must be positive, have " + parallelism);
		this.parallelism = parallelism;
	}

	public int getParallelism() {
		return parallelism;
	}

	@Override
	public PointSetScore score(PointSet pointSet, PointSetScoringConfig config) {
		PointSetScoringUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = PointSetScoringUtils.resolveProjections(pointSet, config);
		List<ProjectionScore> scores = scorePrepared(ExactPointSetData.build(pointSet), projections);
		return PointSetScoringUtils.aggregate(scores, config);
	}

	/** Scores one projection directly, primarily for diagnostics and verification. */
	public ProjectionScore scoreProjection(PointSet pointSet, PointSetProjection projection) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		PointSetScoringUtils.validatePointSet(pointSet);
		PointSetScoringUtils.resolveProjections(pointSet,
				PointSetScoringConfig.builder().projections(projection).build());
		return scorePrepared(ExactPointSetData.build(pointSet), List.of(projection)).get(0);
	}

	private List<ProjectionScore> scorePrepared(ExactPointSetData prepared,
			List<PointSetProjection> projections) {
		ProjectionPlan plan = ProjectionPlan.build(prepared, projections);
		double[] pairSums = calculatePairSums(prepared, plan);

		List<ProjectionScore> scores = new ArrayList<>(projections.size());
		for (int i=0; i<plan.projections.length; i++) {
			PreparedProjection projection = plan.projections[i];
			if (projection.pureCategorical)
				pairSums[i] = categoricalPairSum(projection.dimensions, prepared.numPoints);
			scores.add(PointSetScoringUtils.projectionScore(projection.projection, prepared.numPoints,
					projection.targetGrandMean, projection.targetDiagonalMean, projection.targetSum, pairSums[i]));
		}
		return scores;
	}

	private double[] calculatePairSums(ExactPointSetData prepared, ProjectionPlan plan) {
		if (plan.nodeDimensions.length == 0)
			return new double[plan.projections.length];
		int workers = Math.min(parallelism, prepared.numPoints);
		if (workers == 1)
			return new PairAccumulator(prepared, plan).calculate(0, prepared.numPoints);

		ExecutorService executor = Executors.newFixedThreadPool(workers);
		List<Future<double[]>> futures = new ArrayList<>(workers);
		try {
			for (int worker=0; worker<workers; worker++) {
				// Work for point1 grows in proportion to point1 because it is paired with every preceding point. Square-root
				// boundaries give workers approximately equal numbers of point pairs rather than equal numbers of point1s.
				int start = (int)Math.round(prepared.numPoints*Math.sqrt((double)worker/workers));
				int end = (int)Math.round(prepared.numPoints*Math.sqrt((double)(worker+1)/workers));
				futures.add(executor.submit(() -> new PairAccumulator(prepared, plan).calculate(start, end)));
			}
			double[] sums = new double[plan.projections.length];
			for (Future<double[]> future : futures) {
				double[] workerSums = future.get();
				for (int i=0; i<sums.length; i++)
					sums[i] += workerSums[i];
			}
			return sums;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while scoring point-set pairs", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException)
				throw (RuntimeException)cause;
			if (cause instanceof Error)
				throw (Error)cause;
			throw new IllegalStateException("Point-pair scoring failed", cause);
		} finally {
			for (Future<double[]> future : futures)
				future.cancel(true);
			executor.shutdownNow();
		}
	}

	private static final class PairAccumulator {
		private final ExactPointSetData prepared;
		private final ProjectionPlan plan;
		private final double[] dimensionValues;
		private final int[] dimensionValueStamps;
		private final double[] nodeProducts;
		private final double[] pairSums;
		private int stamp;

		PairAccumulator(ExactPointSetData prepared, ProjectionPlan plan) {
			this.prepared = prepared;
			this.plan = plan;
			this.dimensionValues = new double[prepared.dimensions.length];
			this.dimensionValueStamps = new int[prepared.dimensions.length];
			this.nodeProducts = new double[plan.nodeDimensions.length];
			this.pairSums = new double[plan.projections.length];
		}

		double[] calculate(int startPoint1, int endPoint1) {
			for (int point1=startPoint1; point1<endPoint1; point1++) {
				accumulate(point1, point1, 1d, true);
				for (int point2=0; point2<point1; point2++)
					accumulate(point1, point2, 2d, false);
			}
			return pairSums;
		}

		private void accumulate(int point1, int point2, double symmetryFactor, boolean diagonal) {
			if (++stamp == 0) {
				Arrays.fill(dimensionValueStamps, 0);
				stamp = 1;
			}
			for (int node=0; node<plan.nodeDimensions.length; node++) {
				int parent = plan.nodeParents[node];
				double parentProduct = parent < 0 ? 1d : nodeProducts[parent];
				double product;
				if (parentProduct == 0d) {
					product = 0d;
				} else {
					int dimensionIndex = plan.nodeDimensions[node];
					if (dimensionValueStamps[dimensionIndex] != stamp) {
						PreparedDimension dimension = prepared.dimensions[dimensionIndex];
						dimensionValues[dimensionIndex] = diagonal ? dimension.diagonalValues[point1]
								: dimension.pairValue(point1, point2);
						dimensionValueStamps[dimensionIndex] = stamp;
					}
					product = parentProduct*dimensionValues[dimensionIndex];
				}
				nodeProducts[node] = product;
				int projectionIndex = plan.nodeProjectionIndexes[node];
				if (projectionIndex >= 0)
					pairSums[projectionIndex] += symmetryFactor*product;
			}
		}
	}

	private static final class ProjectionPlan {
		final PreparedProjection[] projections;
		final int[] nodeDimensions;
		final int[] nodeParents;
		final int[] nodeProjectionIndexes;

		ProjectionPlan(PreparedProjection[] projections, int[] nodeDimensions, int[] nodeParents,
				int[] nodeProjectionIndexes) {
			this.projections = projections;
			this.nodeDimensions = nodeDimensions;
			this.nodeParents = nodeParents;
			this.nodeProjectionIndexes = nodeProjectionIndexes;
		}

		static ProjectionPlan build(ExactPointSetData prepared, List<PointSetProjection> projections) {
			PreparedProjection[] preparedProjections = new PreparedProjection[projections.size()];
			MutableNode root = MutableNode.root();
			List<MutableNode> nodes = new ArrayList<>();
			for (int projectionIndex=0; projectionIndex<projections.size(); projectionIndex++) {
				PreparedProjection projection = PreparedProjection.build(prepared, projections.get(projectionIndex));
				preparedProjections[projectionIndex] = projection;
				if (projection.pureCategorical)
					continue;

				MutableNode parent = root;
				// Equality kernels come first so a mismatch zeros the entire continuous descendant subtree.
				for (int i=0; i<projection.dimensions.length; i++)
					if (projection.dimensions[i].categoricalStates != null)
						parent = parent.child(projection.dimensionIndexes[i], nodes);
				for (int i=0; i<projection.dimensions.length; i++)
					if (projection.dimensions[i].categoricalStates == null)
						parent = parent.child(projection.dimensionIndexes[i], nodes);
				parent.projectionIndex = projectionIndex;
			}

			int[] nodeDimensions = new int[nodes.size()];
			int[] nodeParents = new int[nodes.size()];
			int[] nodeProjectionIndexes = new int[nodes.size()];
			for (int i=0; i<nodes.size(); i++) {
				MutableNode node = nodes.get(i);
				nodeDimensions[i] = node.dimension;
				nodeParents[i] = node.parentIndex;
				nodeProjectionIndexes[i] = node.projectionIndex;
			}
			return new ProjectionPlan(preparedProjections, nodeDimensions, nodeParents, nodeProjectionIndexes);
		}
	}

	private static final class MutableNode {
		final int dimension;
		final int index;
		final int parentIndex;
		final List<MutableNode> children = new ArrayList<>();
		int projectionIndex = -1;

		private MutableNode(int dimension, int index, int parentIndex) {
			this.dimension = dimension;
			this.index = index;
			this.parentIndex = parentIndex;
		}

		static MutableNode root() {
			return new MutableNode(-1, -1, -1);
		}

		MutableNode child(int childDimension, List<MutableNode> nodes) {
			for (MutableNode child : children)
				if (child.dimension == childDimension)
					return child;
			MutableNode child = new MutableNode(childDimension, nodes.size(), index);
			children.add(child);
			nodes.add(child);
			return child;
		}
	}

	private static final class PreparedProjection {
		final PointSetProjection projection;
		final PreparedDimension[] dimensions;
		final int[] dimensionIndexes;
		final boolean pureCategorical;
		final double targetGrandMean;
		final double targetDiagonalMean;
		final double targetSum;

		PreparedProjection(PointSetProjection projection, PreparedDimension[] dimensions, int[] dimensionIndexes,
				boolean pureCategorical, double targetGrandMean, double targetDiagonalMean, double targetSum) {
			this.projection = projection;
			this.dimensions = dimensions;
			this.dimensionIndexes = dimensionIndexes;
			this.pureCategorical = pureCategorical;
			this.targetGrandMean = targetGrandMean;
			this.targetDiagonalMean = targetDiagonalMean;
			this.targetSum = targetSum;
		}

		static PreparedProjection build(ExactPointSetData prepared, PointSetProjection projection) {
			PreparedDimension[] dimensions = new PreparedDimension[projection.order()];
			int[] dimensionIndexes = new int[projection.order()];
			boolean pureCategorical = true;
			double targetGrandMean = 1d;
			double targetDiagonalMean = 1d;
			for (int i=0; i<dimensions.length; i++) {
				int dimensionIndex = projection.dimension(i);
				dimensionIndexes[i] = dimensionIndex;
				dimensions[i] = prepared.dimensions[dimensionIndex];
				pureCategorical &= dimensions[i].categoricalStates != null;
				targetGrandMean *= dimensions[i].targetGrandMean;
				targetDiagonalMean *= dimensions[i].targetDiagonalMean;
			}
			double targetSum = 0d;
			for (int point=0; point<prepared.numPoints; point++) {
				double product = 1d;
				for (PreparedDimension dimension : dimensions)
					product *= dimension.targetMeans[point];
				targetSum += product;
			}
			return new PreparedProjection(projection, dimensions, dimensionIndexes, pureCategorical,
					targetGrandMean, targetDiagonalMean, targetSum);
		}
	}

	private static double categoricalPairSum(PreparedDimension[] dimensions, int numPoints) {
		long combinations = 1L;
		for (PreparedDimension dimension : dimensions) {
			if (combinations > Long.MAX_VALUE/dimension.categoricalStateCount)
				return categoricalPairSumQuadratic(dimensions, numPoints);
			combinations *= dimension.categoricalStateCount;
		}

		long[] jointStates = new long[numPoints];
		for (int point=0; point<numPoints; point++) {
			long jointState = 0L;
			for (PreparedDimension dimension : dimensions)
				jointState = jointState*dimension.categoricalStateCount+dimension.categoricalStates[point];
			jointStates[point] = jointState;
		}
		Arrays.sort(jointStates);

		double pairSum = 0d;
		int runStart = 0;
		while (runStart < jointStates.length) {
			int runEnd = runStart+1;
			while (runEnd < jointStates.length && jointStates[runEnd] == jointStates[runStart])
				runEnd++;
			long count = runEnd-runStart;
			pairSum += (double)count*count;
			runStart = runEnd;
		}
		return pairSum;
	}

	private static double categoricalPairSumQuadratic(PreparedDimension[] dimensions, int numPoints) {
		double pairSum = numPoints;
		for (int point1=0; point1<numPoints; point1++) {
			pointPair:
			for (int point2=0; point2<point1; point2++) {
				for (PreparedDimension dimension : dimensions)
					if (dimension.categoricalStates[point1] != dimension.categoricalStates[point2])
						continue pointPair;
				pairSum += 2d;
			}
		}
		return pairSum;
	}
}
