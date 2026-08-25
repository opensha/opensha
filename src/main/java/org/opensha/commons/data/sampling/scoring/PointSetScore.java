package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
}
