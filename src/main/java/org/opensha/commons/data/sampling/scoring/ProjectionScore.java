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
