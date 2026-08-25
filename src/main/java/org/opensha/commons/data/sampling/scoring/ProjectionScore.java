package org.opensha.commons.data.sampling.scoring;

/**
 * Exact ideal-target discrepancy and IID-random normalization for one projection.
 */
public final class ProjectionScore {

	private final PointSetProjection projection;
	private final double rawScore;
	private final double expectedRandomScore;

	ProjectionScore(PointSetProjection projection, double rawScore, double expectedRandomScore) {
		this.projection = projection;
		this.rawScore = rawScore;
		this.expectedRandomScore = expectedRandomScore;
	}

	/**
	 * Builds a projection result from a raw discrepancy and its positive IID-random expectation. This factory is used by
	 * scorer implementations outside this package, including stateful optimization sessions.
	 */
	public static ProjectionScore of(PointSetProjection projection, double rawScore, double expectedRandomScore) {
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		if (!Double.isFinite(rawScore) || rawScore < 0d)
			throw new IllegalArgumentException("Raw score must be finite and nonnegative, have " + rawScore);
		if (!Double.isFinite(expectedRandomScore) || expectedRandomScore <= 0d)
			throw new IllegalArgumentException("Expected random score must be finite and positive, have "
					+ expectedRandomScore);
		return new ProjectionScore(projection, rawScore, expectedRandomScore);
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
