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
public final class PointSetScore {

	private final List<ProjectionScore> projectionScores;
	private final Map<Integer, Double> orderMeanScores;
	private final double normalizedScore;

	PointSetScore(List<ProjectionScore> projectionScores, Map<Integer, Double> orderMeanScores,
			double normalizedScore) {
		this.projectionScores = Collections.unmodifiableList(new ArrayList<>(projectionScores));
		this.orderMeanScores = Collections.unmodifiableMap(new LinkedHashMap<>(orderMeanScores));
		this.normalizedScore = normalizedScore;
	}

	/**
	 * Aggregates projection results using the configuration's per-order averaging and weights.
	 */
	public static PointSetScore aggregate(List<ProjectionScore> projectionScores, PointSetScoringConfig config) {
		if (projectionScores == null)
			throw new NullPointerException("Projection scores cannot be null");
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		for (ProjectionScore score : projectionScores)
			if (score == null)
				throw new NullPointerException("Projection score cannot be null");
		return PointSetScoringUtils.aggregate(projectionScores, config);
	}

	public List<ProjectionScore> getProjectionScores() {
		return projectionScores;
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
		StringBuilder builder = new StringBuilder("PointSetScore[normalizedScore=")
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
}
