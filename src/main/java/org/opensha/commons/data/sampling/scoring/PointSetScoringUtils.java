package org.opensha.commons.data.sampling.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opensha.commons.data.sampling.PointSet;

/** Shared validation, projection enumeration, and result aggregation for scorer implementations. */
final class PointSetScoringUtils {

	private static final double NEGATIVE_ROUNDOFF_TOLERANCE = 1e-12;

	private PointSetScoringUtils() {}

	static void validatePointSet(PointSet pointSet) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
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

	static List<PointSetProjection> resolveProjections(PointSet pointSet, PointSetScoringConfig config) {
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		return config.resolveProjections(pointSet.dimensions());
	}

	/** Validates that every dimension selected by a projection exists in the point set. */
	static void validateProjection(PointSetProjection projection, int dimensions) {
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		if (dimensions < 1)
			throw new IllegalArgumentException("Point-set dimensionality must be positive, have " + dimensions);
		for (int i=0; i<projection.order(); i++)
			if (projection.dimension(i) >= dimensions)
				throw new IllegalArgumentException("Projection " + projection + " references dimension "
						+ projection.dimension(i) + " but point set has " + dimensions + " dimensions");
	}

	static PointSetScore aggregate(List<ProjectionScore> scores, PointSetScoringConfig config) {
		Map<Integer, Double> orderSums = new TreeMap<>();
		Map<Integer, Integer> orderCounts = new TreeMap<>();
		for (ProjectionScore score : scores) {
			int order = score.getProjection().order();
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

	static ProjectionScore projectionScore(PointSetProjection projection, int numPoints,
			double targetGrandMean, double targetDiagonalMean, double targetSum, double pairSum) {
		requireFinite(targetGrandMean, "product target grand mean", projection);
		requireFinite(targetDiagonalMean, "product target diagonal mean", projection);
		requireFinite(targetSum, "target-mean sum", projection);
		requireFinite(pairSum, "kernel-pair sum", projection);
		double targetTerm = 2d*targetSum/numPoints;
		double pairTerm = pairSum/((double)numPoints*numPoints);
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
		double expectedRandomScore = (targetDiagonalMean-targetGrandMean)/numPoints;
		if (!Double.isFinite(expectedRandomScore) || expectedRandomScore <= 0d)
			throw new IllegalStateException("Expected IID-random score must be finite and positive, have "
					+ expectedRandomScore + " for projection " + projection);
		return ProjectionScore.of(projection, rawScore, expectedRandomScore);
	}

	static double requireFinite(double value, String quantity, PointSetProjection projection) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Non-finite " + quantity + " for projection " + projection + ": " + value);
		return value;
	}

}
