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
 * Exact product-kernel discrepancy scorer for finite point sets. Each projection is compared with its ideal product
 * distribution and divided by its expected score for IID samples from that target.
 * <p>
 * This reference implementation is intentionally unquantized. Its worst-case cost for {@code P} projections of order
 * {@code k} is {@code O(P*N^2*k)}, although fully categorical projections are scored from joint-category counts in
 * {@code O(P*N*(k+log(N)))} time. It does not allocate point-pair matrices. Instances configured with more than one
 * worker score projections concurrently; calculations within each projection retain their serial order.
 */
public final class ExactPointSetScorer implements PointSetScorer {

	private final int parallelism;

	/** Builds a serial exact scorer. */
	public ExactPointSetScorer() {
		this(1);
	}

	/**
	 * @param parallelism maximum number of projections scored concurrently
	 */
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
		ExactPointSetData prepared = ExactPointSetData.build(pointSet);

		List<ProjectionScore> scores = scoreProjections(prepared, projections);
		return PointSetScoringUtils.aggregate(scores, config);
	}

	/**
	 * Scores one projection directly. This is primarily useful for diagnostics and verification; general callers can
	 * select one projection through {@link PointSetScoringConfig} and the {@link PointSetScorer} interface.
	 */
	public ProjectionScore scoreProjection(PointSet pointSet, PointSetProjection projection) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		PointSetScoringUtils.validatePointSet(pointSet);
		// Resolve a one-projection configuration so this convenience path enforces the same inactive-dimension rules as
		// the general scoring API.
		PointSetScoringUtils.resolveProjections(pointSet,
				PointSetScoringConfig.builder().projections(projection).build());
		return scoreProjectionPrepared(ExactPointSetData.build(pointSet), projection);
	}

	private List<ProjectionScore> scoreProjections(ExactPointSetData prepared,
			List<PointSetProjection> projections) {
		if (parallelism == 1 || projections.size() == 1) {
			List<ProjectionScore> scores = new ArrayList<>(projections.size());
			for (PointSetProjection projection : projections)
				scores.add(scoreProjectionPrepared(prepared, projection));
			return scores;
		}

		ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, projections.size()));
		List<Future<ProjectionScore>> futures = new ArrayList<>(projections.size());
		try {
			for (PointSetProjection projection : projections)
				futures.add(executor.submit(() -> scoreProjectionPrepared(prepared, projection)));
			List<ProjectionScore> scores = new ArrayList<>(projections.size());
			for (Future<ProjectionScore> future : futures)
				scores.add(future.get());
			return scores;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while scoring point-set projections", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException)
				throw (RuntimeException)cause;
			if (cause instanceof Error)
				throw (Error)cause;
			throw new IllegalStateException("Projection scoring failed", cause);
		} finally {
			for (Future<ProjectionScore> future : futures)
				future.cancel(true);
			executor.shutdownNow();
		}
	}

	private ProjectionScore scoreProjectionPrepared(ExactPointSetData prepared, PointSetProjection projection) {
		int n = prepared.numPoints;
		PreparedDimension[] dimensions = new PreparedDimension[projection.order()];
		int categoricalCount = 0;
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			dimensions[i] = prepared.dimensions[projection.dimension(i)];
			if (dimensions[i].categoricalStates != null)
				categoricalCount++;
			targetGrandMean *= dimensions[i].targetGrandMean;
			targetDiagonalMean *= dimensions[i].targetDiagonalMean;
		}
		requireFinite(targetGrandMean, "product target grand mean", projection);
		requireFinite(targetDiagonalMean, "product target diagonal mean", projection);

		double targetSum = 0d;
		// For each observed projected point, multiply its per-dimension similarity to the ideal target. Averaging these
		// products gives the sample-to-target term in the squared distribution distance.
		for (int p=0; p<n; p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++)
				product *= dimensions[i].targetMeans[p];
			targetSum += product;
		}
		requireFinite(targetSum, "target-mean sum", projection);

		double pairSum;
		if (categoricalCount == dimensions.length) {
			// For equality kernels, the complete product is one exactly when two points occupy the same joint category.
			// Therefore sum_ij k(x_i,x_j) is simply the sum of squared joint-category counts, avoiding all N^2 pairs.
			pairSum = categoricalPairSum(dimensions, n);
		} else {
			pairSum = kernelPairSum(dimensions, categoricalCount, n);
		}
		requireFinite(pairSum, "kernel-pair sum", projection);

		// Shared finalization applies target-target - 2*sample-target + sample-sample and IID normalization.
		return PointSetScoringUtils.projectionScore(projection, n, targetGrandMean, targetDiagonalMean,
				targetSum, pairSum);
	}

	private static double kernelPairSum(PreparedDimension[] dimensions, int categoricalCount, int numPoints) {
		PreparedDimension[] pairDimensions = dimensions;
		if (categoricalCount > 0) {
			// Equality checks are cheap and commonly reject a pair. Put them first so continuous kernels are only evaluated
			// for points that match in every categorical dimension.
			pairDimensions = new PreparedDimension[dimensions.length];
			int categoricalIndex = 0;
			int otherIndex = categoricalCount;
			for (PreparedDimension dimension : dimensions)
				pairDimensions[dimension.categoricalStates == null ? otherIndex++ : categoricalIndex++] = dimension;
		}

		double pairSum = 0d;
		for (int p1=0; p1<numPoints; p1++) {
			double diagonalProduct = 1d;
			for (PreparedDimension dimension : dimensions)
				diagonalProduct *= dimension.diagonalValues[p1];
			pairSum += diagonalProduct;
			pointPair:
			for (int p2=0; p2<p1; p2++) {
				for (int i=0; i<categoricalCount; i++) {
					int[] states = pairDimensions[i].categoricalStates;
					if (states[p1] != states[p2])
						continue pointPair;
				}
				double product = 1d;
				for (int i=categoricalCount; i<pairDimensions.length; i++) {
					PreparedDimension dimension = pairDimensions[i];
					product *= dimension.kernel.value(dimension.values[p1], dimension.values[p2]);
					if (product == 0d)
						break;
				}
				pairSum += 2d*product;
			}
		}
		return pairSum;
	}

	private static double categoricalPairSum(PreparedDimension[] dimensions, int numPoints) {
		long combinations = 1L;
		for (PreparedDimension dimension : dimensions) {
			if (combinations > Long.MAX_VALUE/dimension.categoricalStateCount)
				return categoricalPairSumQuadratic(dimensions, numPoints);
			combinations *= dimension.categoricalStateCount;
		}

		long[] jointStates = new long[numPoints];
		for (int p=0; p<numPoints; p++) {
			long jointState = 0L;
			for (PreparedDimension dimension : dimensions)
				jointState = jointState*dimension.categoricalStateCount+dimension.categoricalStates[p];
			jointStates[p] = jointState;
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
		double pairSum = numPoints; // every point matches itself
		for (int p1=0; p1<numPoints; p1++) {
			pointPair:
			for (int p2=0; p2<p1; p2++) {
				for (PreparedDimension dimension : dimensions)
					if (dimension.categoricalStates[p1] != dimension.categoricalStates[p2])
						continue pointPair;
				pairSum += 2d;
			}
		}
		return pairSum;
	}

	private static double requireFinite(double value, String quantity, PointSetProjection projection) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Non-finite " + quantity + " for projection " + projection + ": " + value);
		return value;
	}

}
