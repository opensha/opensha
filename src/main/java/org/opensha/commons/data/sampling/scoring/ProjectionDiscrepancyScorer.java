package org.opensha.commons.data.sampling.scoring;

import org.opensha.commons.data.sampling.PointSet;

/**
 * Strategy for scoring finite point sets against their ideal dimension distributions. Implementations can use exact,
 * quantized, or other representations as long as they return the common {@link ProjectionDiscrepancyScore} result.
 */
public interface ProjectionDiscrepancyScorer {

	/** @return a deterministic, unquantized scorer using one worker */
	static ProjectionDiscrepancyScorer exact() {
		return new ExactProjectionDiscrepancyScorer();
	}

	/**
	 * @param parallelism maximum number of point-pair ranges processed concurrently
	 * @return a deterministic, unquantized scorer
	 */
	static ProjectionDiscrepancyScorer exact(int parallelism) {
		return new ExactProjectionDiscrepancyScorer(parallelism);
	}

	/**
	 * @param continuousBins number of equal-width states used for each continuous dimension
	 * @return a deterministic scorer using discretized continuous kernels
	 */
	static ProjectionDiscrepancyScorer quantized(int continuousBins) {
		return new QuantizedProjectionDiscrepancyScorer(continuousBins);
	}

	/**
	 * Scores every projection through order 2 with default order weights.
	 *
	 * @param pointSet point set to score
	 * @return point-set score
	 */
	default ProjectionDiscrepancyScore score(PointSet pointSet) {
		return score(pointSet, ProjectionDiscrepancyConfig.defaults());
	}

	/**
	 * Scores every projection through {@code maxOrder} with default order weights.
	 *
	 * @param pointSet point set to score
	 * @param maxOrder maximum projection order
	 * @return point-set score
	 */
	default ProjectionDiscrepancyScore score(PointSet pointSet, int maxOrder) {
		return score(pointSet, ProjectionDiscrepancyConfig.builder().maxOrder(maxOrder).build());
	}

	/**
	 * Scores the projections and order weights selected by {@code config}.
	 *
	 * @param pointSet point set to score
	 * @param config projection selection and aggregation configuration
	 * @return point-set score
	 */
	ProjectionDiscrepancyScore score(PointSet pointSet, ProjectionDiscrepancyConfig config);

	/**
	 * Scores one selected coordinate projection. This is equivalent to constructing an explicit one-projection
	 * configuration, but is convenient for diagnostics and per-projection plots.
	 */
	default ProjectionDiscrepancyScore.ProjectionResult scoreProjection(PointSet pointSet,
			PointSetProjection projection) {
		ProjectionDiscrepancyScore score = score(pointSet,
				ProjectionDiscrepancyConfig.builder().projections(projection).build());
		return score.getProjectionResults().get(0);
	}
}
