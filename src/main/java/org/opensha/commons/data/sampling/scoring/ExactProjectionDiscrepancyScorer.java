package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.data.sampling.scoring.ExactProjectionDiscrepancyScorer.ExactScoringData.PreparedDimension;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore.ProjectionResult;

/**
 * Optimized exact product-kernel discrepancy scorer. It visits each point pair once, evaluates each needed
 * one-dimensional kernel at most once for that pair, and uses a prepared projection tree to reuse partial products
 * between projections. Fully categorical projections use exact joint-category counts instead.
 * <p>
 * This implementation remains unquantized and deterministic. Its dominant continuous-projection cost is
 * {@code O(N^2*(d+P))}, where {@code d} is the number of used dimensions and {@code P} the number of prepared
 * projection-tree nodes. It uses {@code O(T*P)} accumulator memory for {@code T} workers and does not materialize
 * point-pair matrices. A direct formula-oriented implementation is retained in the test suite as a correctness oracle.
 */
final class ExactProjectionDiscrepancyScorer implements ProjectionDiscrepancyScorer {

	private final int parallelism;

	/** Builds a serial exact scorer. */
	ExactProjectionDiscrepancyScorer() {
		this(1);
	}

	/** @param parallelism maximum number of point-pair ranges processed concurrently */
	ExactProjectionDiscrepancyScorer(int parallelism) {
		if (parallelism < 1)
			throw new IllegalArgumentException("Parallelism must be positive, have " + parallelism);
		this.parallelism = parallelism;
	}

	int getParallelism() {
		return parallelism;
	}

	@Override
	public ProjectionDiscrepancyScore score(PointSet pointSet, ProjectionDiscrepancyConfig config) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = ProjectionDiscrepancyUtils.resolveProjections(pointSet, config);
		List<ProjectionResult> scores = scorePrepared(ExactScoringData.build(pointSet), projections);
		return ProjectionDiscrepancyUtils.aggregate(scores, config);
	}

	/** Scores one projection directly, primarily for diagnostics and verification. */
	public ProjectionResult scoreProjection(PointSet pointSet, PointSetProjection projection) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		ProjectionDiscrepancyUtils.resolveProjections(pointSet,
				ProjectionDiscrepancyConfig.builder().projections(projection).build());
		return scorePrepared(ExactScoringData.build(pointSet), List.of(projection)).get(0);
	}

	private List<ProjectionResult> scorePrepared(ExactScoringData prepared,
			List<PointSetProjection> projections) {
		ProjectionPlan plan = ProjectionPlan.build(prepared, projections);
		double[] pairSums = calculatePairSums(prepared, plan);

		List<ProjectionResult> scores = new ArrayList<>(projections.size());
		for (int i=0; i<plan.projections.length; i++) {
			PreparedProjection projection = plan.projections[i];
			if (projection.pureCategorical)
				pairSums[i] = categoricalPairSum(projection.dimensions, prepared.numPoints);
			scores.add(ProjectionDiscrepancyUtils.projectionScore(projection.projection, prepared.numPoints,
					projection.targetGrandMean, projection.targetDiagonalMean, projection.targetSum, pairSums[i]));
		}
		return scores;
	}

	private double[] calculatePairSums(ExactScoringData prepared, ProjectionPlan plan) {
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
		private final ExactScoringData prepared;
		private final ProjectionPlan plan;
		private final double[] dimensionValues;
		private final int[] dimensionValueStamps;
		private final double[] nodeProducts;
		private final double[] pairSums;
		private int stamp;

		PairAccumulator(ExactScoringData prepared, ProjectionPlan plan) {
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

		static ProjectionPlan build(ExactScoringData prepared, List<PointSetProjection> projections) {
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

		static PreparedProjection build(ExactScoringData prepared, PointSetProjection projection) {
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

	/** Coordinates and exact kernel quantities prepared once before any quadratic projection scoring. */
	static final class ExactScoringData {

		final int numPoints;
		final PreparedDimension[] dimensions;

		private ExactScoringData(int numPoints, PreparedDimension[] dimensions) {
			this.numPoints = numPoints;
			this.dimensions = dimensions;
		}

		static ExactScoringData build(PointSet pointSet) {
			PreparedDimension[] dimensions = new PreparedDimension[pointSet.dimensions()];
			for (int d=0; d<dimensions.length; d++) {
				SamplingDimension dimension = pointSet.getDimension(d);
				if (!dimension.isActive())
					continue;
				DiscrepancyKernel kernel = dimension.getDiscrepancyKernel();
				if (kernel == null)
					throw new NullPointerException("Discrepancy kernel for dimension " + d + " is null");
				double[] values = new double[pointSet.size()];
				double[] targetMeans = new double[pointSet.size()];
				double[] diagonalValues = new double[pointSet.size()];
				int[] categoricalStates = dimension instanceof CategoricalSamplingDimension
						? new int[pointSet.size()] : null;
				for (int p=0; p<pointSet.size(); p++) {
					double value = pointSet.get(p, d);
					values[p] = value;
					if (categoricalStates == null) {
						targetMeans[p] = requireFinite(kernel.targetMean(value), "target mean", d, p);
						diagonalValues[p] = requireFinite(kernel.value(value, value),
								"diagonal kernel value", d, p);
					} else {
						CategoricalSamplingDimension categorical = (CategoricalSamplingDimension)dimension;
						int state = categorical.categoryIndex(value);
						categoricalStates[p] = state;
						targetMeans[p] = categorical.categoryProbability(state);
						diagonalValues[p] = 1d;
					}
				}
				dimensions[d] = new PreparedDimension(kernel, values, targetMeans, diagonalValues,
						categoricalStates, categoricalStates == null ? 0
								: ((CategoricalSamplingDimension)dimension).categoryCount(),
						requireFinite(kernel.targetGrandMean(), "target grand mean", d, -1),
						requireFinite(kernel.targetDiagonalMean(), "target diagonal mean", d, -1));
			}
			return new ExactScoringData(pointSet.size(), dimensions);
		}

		private static double requireFinite(double value, String quantity, int dimension, int point) {
			if (!Double.isFinite(value))
				throw new IllegalStateException("Non-finite " + quantity + " for dimension " + dimension
						+ (point < 0 ? "" : ", point " + point) + ": " + value);
			return value;
		}

		static final class PreparedDimension {
			final DiscrepancyKernel kernel;
			final double[] values;
			final double[] targetMeans;
			final double[] diagonalValues;
			final int[] categoricalStates;
			final int categoricalStateCount;
			final double targetGrandMean;
			final double targetDiagonalMean;

			PreparedDimension(DiscrepancyKernel kernel, double[] values, double[] targetMeans,
					double[] diagonalValues, int[] categoricalStates, int categoricalStateCount,
					double targetGrandMean, double targetDiagonalMean) {
				this.kernel = kernel;
				this.values = values;
				this.targetMeans = targetMeans;
				this.diagonalValues = diagonalValues;
				this.categoricalStates = categoricalStates;
				this.categoricalStateCount = categoricalStateCount;
				this.targetGrandMean = targetGrandMean;
				this.targetDiagonalMean = targetDiagonalMean;
			}

			double pairValue(int point1, int point2) {
				if (categoricalStates != null)
					return categoricalStates[point1] == categoricalStates[point2] ? 1d : 0d;
				return kernel.value(values[point1], values[point2]);
			}
		}
	}
}
