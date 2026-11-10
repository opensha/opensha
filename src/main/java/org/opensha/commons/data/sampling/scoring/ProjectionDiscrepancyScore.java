package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detailed projection scores and their normalized aggregate.
 */
public final class ProjectionDiscrepancyScore {

	private final List<ProjectionResult> projectionResults;
	private final Map<Integer, Double> orderMeanScores;
	private final double normalizedScore;

	ProjectionDiscrepancyScore(List<ProjectionResult> projectionResults, Map<Integer, Double> orderMeanScores,
			double normalizedScore) {
		this.projectionResults = Collections.unmodifiableList(new ArrayList<>(projectionResults));
		this.orderMeanScores = Collections.unmodifiableMap(new LinkedHashMap<>(orderMeanScores));
		this.normalizedScore = normalizedScore;
	}

	/**
	 * Aggregates projection results using the configuration's per-order averaging and weights.
	 */
	public static ProjectionDiscrepancyScore aggregate(List<ProjectionResult> projectionResults,
			ProjectionDiscrepancyConfig config) {
		if (projectionResults == null)
			throw new NullPointerException("Projection results cannot be null");
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		for (ProjectionResult score : projectionResults)
			if (score == null)
				throw new NullPointerException("Projection result cannot be null");
		return ProjectionDiscrepancyUtils.aggregate(projectionResults, config);
	}

	/** @return immutable results for each scored coordinate projection */
	public List<ProjectionResult> getProjectionResults() {
		return projectionResults;
	}

	/** @return immutable map from projection order to mean normalized score */
	public Map<Integer, Double> getOrderMeanScores() {
		return orderMeanScores;
	}

	/**
	 * @return mean normalized score for an included projection order
	 * @throws IllegalArgumentException if no projection of that order was scored
	 */
	public double getOrderMeanScore(int order) {
		Double score = orderMeanScores.get(order);
		if (score == null)
			throw new IllegalArgumentException("No projections of order " + order + " were scored");
		return score;
	}

	/** @return weighted mean of per-order normalized scores */
	public double getNormalizedScore() {
		return normalizedScore;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("ProjectionDiscrepancyScore[normalizedScore=")
				.append(formatScore(normalizedScore)).append(", orderMeans={");
		boolean first = true;
		for (Map.Entry<Integer, Double> entry : orderMeanScores.entrySet()) {
			if (first)
				first = false;
			else
				builder.append(", ");
			builder.append(entry.getKey()).append('=').append(formatScore(entry.getValue()));
		}
		return builder.append("}]").toString();
	}

	private static String formatScore(double score) {
		return String.format(Locale.US, "%.5f", score);
	}

	/** Exact ideal-target discrepancy and IID-random normalization for one coordinate projection. */
	public static final class ProjectionResult {

		private final PointSetProjection projection;
		private final double rawScore;
		private final double expectedRandomScore;

		private ProjectionResult(PointSetProjection projection, double rawScore, double expectedRandomScore) {
			this.projection = projection;
			this.rawScore = rawScore;
			this.expectedRandomScore = expectedRandomScore;
		}

		/**
		 * Builds a projection result from a raw discrepancy and its positive IID-random expectation. This factory is
		 * also used by stateful optimization sessions.
		 */
		public static ProjectionResult of(PointSetProjection projection, double rawScore,
				double expectedRandomScore) {
			if (projection == null)
				throw new NullPointerException("Projection cannot be null");
			if (!Double.isFinite(rawScore) || rawScore < 0d)
				throw new IllegalArgumentException("Raw score must be finite and nonnegative, have " + rawScore);
			if (!Double.isFinite(expectedRandomScore) || expectedRandomScore <= 0d)
				throw new IllegalArgumentException("Expected random score must be finite and positive, have "
						+ expectedRandomScore);
			return new ProjectionResult(projection, rawScore, expectedRandomScore);
		}

		public PointSetProjection getProjection() {
			return projection;
		}

		/** @return squared product-kernel discrepancy from the ideal target */
		public double getRawScore() {
			return rawScore;
		}

		/** @return expected raw score for IID samples from the ideal target */
		public double getExpectedRandomScore() {
			return expectedRandomScore;
		}

		/** @return raw score divided by its IID-random expectation */
		public double getNormalizedScore() {
			return rawScore/expectedRandomScore;
		}
	}
}
