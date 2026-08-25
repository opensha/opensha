package org.opensha.commons.data.sampling.optimization;

import java.util.Locale;

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

	@Override
	public String toString() {
		return String.format(Locale.US,
				"PointSetOptimizationResult[iterations=%d, accepted=%d, score=%.5f -> %.5f]",
				iterations, acceptedSwaps, initialScore, finalScore);
	}
}
