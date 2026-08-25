package org.opensha.commons.data.sampling.optimization;

/** Summary of a completed point-set optimization run. */
public final class PointSetOptimizationResult {

	private final long iterations;
	private final long acceptedSwaps;
	private final double initialScore;
	private final double finalScore;

	PointSetOptimizationResult(long iterations, long acceptedSwaps, double initialScore, double finalScore) {
		this.iterations = iterations;
		this.acceptedSwaps = acceptedSwaps;
		this.initialScore = initialScore;
		this.finalScore = finalScore;
	}

	public long getIterations() {
		return iterations;
	}

	public long getAcceptedSwaps() {
		return acceptedSwaps;
	}

	public double getInitialScore() {
		return initialScore;
	}

	public double getFinalScore() {
		return finalScore;
	}

	public double getScoreReduction() {
		return initialScore-finalScore;
	}
}
