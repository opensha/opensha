package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;

/**
 * Exact product-kernel discrepancy scorer for finite point sets. Each projection is compared with its ideal product
 * distribution and divided by its expected score for IID samples from that target.
 * <p>
 * This reference implementation is intentionally unquantized. For {@code P} projections of order {@code k}, its
 * dominant cost is {@code O(P*N^2*k)} and it does not allocate point-pair matrices.
 */
public final class ExactPointSetScorer implements PointSetScorer {

	private static final double NEGATIVE_ROUNDOFF_TOLERANCE = 1e-12;

	@Override
	public PointSetScore score(PointSet pointSet, PointSetScoringConfig config) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		validatePointSet(pointSet);

		List<PointSetProjection> projections = config.hasExplicitProjections()
				? config.getExplicitProjections() : enumerateProjections(pointSet.dimensions(), config.getMaxOrder());
		for (PointSetProjection projection : projections)
			validateProjection(projection, pointSet.dimensions());

		List<ProjectionScore> scores = new ArrayList<>(projections.size());
		Map<Integer, Double> orderSums = new TreeMap<>();
		Map<Integer, Integer> orderCounts = new TreeMap<>();
		for (PointSetProjection projection : projections) {
			ProjectionScore score = scoreProjectionValidated(pointSet, projection);
			scores.add(score);
			int order = projection.order();
			orderSums.merge(order, score.getNormalizedScore(), Double::sum);
			orderCounts.merge(order, 1, Integer::sum);
		}

		Map<Integer, Double> orderMeans = new LinkedHashMap<>();
		double weightedSum = 0d;
		double weightSum = 0d;
		for (Map.Entry<Integer, Double> entry : orderSums.entrySet()) {
			int order = entry.getKey();
			double mean = entry.getValue()/orderCounts.get(order);
			orderMeans.put(order, mean);
			double weight = config.getOrderWeight(order);
			weightedSum += weight*mean;
			weightSum += weight;
		}
		if (!(weightSum > 0d))
			throw new IllegalArgumentException("At least one included projection order must have positive weight");
		return new PointSetScore(scores, orderMeans, weightedSum/weightSum);
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
		validatePointSet(pointSet);
		validateProjection(projection, pointSet.dimensions());
		return scoreProjectionValidated(pointSet, projection);
	}

	private ProjectionScore scoreProjectionValidated(PointSet pointSet, PointSetProjection projection) {
		int n = pointSet.size();
		DiscrepancyKernel[] kernels = new DiscrepancyKernel[projection.order()];
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			SamplingDimension dimension = pointSet.getDimension(projection.dimension(i));
			if (dimension == null)
				throw new NullPointerException("Point-set dimension " + projection.dimension(i) + " is null");
			DiscrepancyKernel kernel = dimension.getDiscrepancyKernel();
			if (kernel == null)
				throw new NullPointerException("Discrepancy kernel for dimension " + projection.dimension(i) + " is null");
			kernels[i] = kernel;
			targetGrandMean *= requireFinite(kernel.targetGrandMean(), "target grand mean", projection);
			targetDiagonalMean *= requireFinite(kernel.targetDiagonalMean(), "target diagonal mean", projection);
		}
		requireFinite(targetGrandMean, "product target grand mean", projection);
		requireFinite(targetDiagonalMean, "product target diagonal mean", projection);

		double targetSum = 0d;
		// For each observed projected point, multiply its per-dimension similarity to the ideal target. Averaging these
		// products gives the sample-to-target term in the squared distribution distance.
		for (int p=0; p<n; p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++)
				product *= requireFinite(kernels[i].targetMean(pointSet.get(p, projection.dimension(i))),
						"target mean", projection);
			requireFinite(product, "product target mean", projection);
			targetSum += product;
		}
		requireFinite(targetSum, "target-mean sum", projection);

		// Compare every pair of observed projected points. Multiplying kernels means a pair is similar in the projection
		// only to the extent that it is similar in every included dimension. Use symmetry to halve kernel evaluations
		// without materializing an N x N matrix.
		double pairSum = 0d;
		for (int p1=0; p1<n; p1++) {
			double diagonalProduct = 1d;
			for (int i=0; i<projection.order(); i++) {
				double value = pointSet.get(p1, projection.dimension(i));
				diagonalProduct *= requireFinite(kernels[i].value(value, value), "diagonal kernel value", projection);
			}
			requireFinite(diagonalProduct, "product diagonal kernel value", projection);
			pairSum += diagonalProduct;
			for (int p2=0; p2<p1; p2++) {
				double product = 1d;
				for (int i=0; i<projection.order(); i++) {
					int dimension = projection.dimension(i);
					product *= requireFinite(kernels[i].value(pointSet.get(p1, dimension), pointSet.get(p2, dimension)),
							"kernel value", projection);
				}
				requireFinite(product, "product kernel value", projection);
				pairSum += 2d*product;
			}
		}
		requireFinite(pairSum, "kernel-pair sum", projection);

		// Squared kernel distance: target-target - 2*sample-target + sample-sample.
		double targetTerm = 2d*targetSum/n;
		double pairTerm = pairSum/((double)n*n);
		double rawScore = targetGrandMean-targetTerm+pairTerm;
		requireFinite(rawScore, "raw score", projection);
		double scale = Math.max(1d, Math.abs(targetGrandMean)+Math.abs(targetTerm)+Math.abs(pairTerm));
		if (rawScore < 0d) {
			if (rawScore >= -NEGATIVE_ROUNDOFF_TOLERANCE*scale)
				rawScore = 0d;
			else
				throw new IllegalStateException("Calculated materially negative discrepancy " + rawScore
						+ " for projection " + projection);
		}

		// An IID sample has N self-pairs rather than N^2 fully independent pairs. That diagonal excess is the entire
		// expected finite-sample discrepancy; normalizing by it makes an IID-random projection have expectation 1.
		double expectedRandomScore = (targetDiagonalMean-targetGrandMean)/n;
		if (!Double.isFinite(expectedRandomScore) || expectedRandomScore <= 0d)
			throw new IllegalStateException("Expected IID-random score must be finite and positive, have "
					+ expectedRandomScore + " for projection " + projection);
		return new ProjectionScore(projection, rawScore, expectedRandomScore);
	}

	private static double requireFinite(double value, String quantity, PointSetProjection projection) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Non-finite " + quantity + " for projection " + projection + ": " + value);
		return value;
	}

	private static void validatePointSet(PointSet pointSet) {
		if (pointSet.size() < 1)
			throw new IllegalArgumentException("Point set must contain at least one point");
		if (pointSet.dimensions() < 1)
			throw new IllegalArgumentException("Point set must contain at least one dimension");
		for (int d=0; d<pointSet.dimensions(); d++) {
			if (pointSet.getDimension(d) == null)
				throw new NullPointerException("Point-set dimension " + d + " is null");
			for (int p=0; p<pointSet.size(); p++) {
				double value = pointSet.get(p, d);
				if (!Double.isFinite(value) || value < 0d || value >= 1d)
					throw new IllegalArgumentException("Coordinate [" + p + "][" + d
							+ "] must be finite and in [0,1), have " + value);
			}
		}
	}

	private static void validateProjection(PointSetProjection projection, int dimensions) {
		for (int i=0; i<projection.order(); i++)
			if (projection.dimension(i) >= dimensions)
				throw new IllegalArgumentException("Projection " + projection + " references dimension "
						+ projection.dimension(i) + " but point set has " + dimensions + " dimensions");
	}

	private static List<PointSetProjection> enumerateProjections(int dimensions, int maxOrder) {
		List<PointSetProjection> projections = new ArrayList<>();
		for (int order=1; order<=Math.min(maxOrder, dimensions); order++)
			enumerateProjections(dimensions, new int[order], 0, 0, projections);
		return projections;
	}

	private static void enumerateProjections(int dimensions, int[] indexes, int position, int minimum,
			List<PointSetProjection> projections) {
		if (position == indexes.length) {
			projections.add(new PointSetProjection(indexes));
			return;
		}
		int remaining = indexes.length-position-1;
		for (int dimension=minimum; dimension<dimensions-remaining; dimension++) {
			indexes[position] = dimension;
			enumerateProjections(dimensions, indexes, position+1, dimension+1, projections);
		}
	}
}
