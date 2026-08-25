package org.opensha.commons.data.sampling.scoring;

import org.opensha.commons.data.sampling.PointSet;

/**
 * Strategy for scoring finite point sets against their ideal dimension distributions. Implementations can use exact,
 * quantized, or other representations as long as they return the common {@link PointSetScore} result.
 */
public interface PointSetScorer {

	/**
	 * Scores every projection through order 2 with default order weights.
	 *
	 * @param pointSet point set to score
	 * @return point-set score
	 */
	default PointSetScore score(PointSet pointSet) {
		return score(pointSet, PointSetScoringConfig.defaults());
	}

	/**
	 * Scores every projection through {@code maxOrder} with default order weights.
	 *
	 * @param pointSet point set to score
	 * @param maxOrder maximum projection order
	 * @return point-set score
	 */
	default PointSetScore score(PointSet pointSet, int maxOrder) {
		return score(pointSet, PointSetScoringConfig.builder().maxOrder(maxOrder).build());
	}

	/**
	 * Scores the projections and order weights selected by {@code config}.
	 *
	 * @param pointSet point set to score
	 * @param config projection selection and aggregation configuration
	 * @return point-set score
	 */
	PointSetScore score(PointSet pointSet, PointSetScoringConfig config);
}
