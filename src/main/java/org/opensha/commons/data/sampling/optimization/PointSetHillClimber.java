package org.opensha.commons.data.sampling.optimization;

import java.util.random.RandomGenerator;

import org.opensha.commons.data.sampling.SwappablePointSet;

/**
 * Generic strict hill climber for an {@link IncrementalPointSetScorer}. Each iteration chooses a swap group uniformly,
 * chooses two distinct points uniformly, and commits the proposal only when it lowers the aggregate normalized score.
 * Swap groups define which dimensions move together; the optimizer does not need to know their scoring semantics.
 */
public final class PointSetHillClimber {

	private PointSetHillClimber() {}

	public static PointSetOptimizationResult optimize(IncrementalPointSetScorer scorer, long iterations,
			RandomGenerator random) {
		if (scorer == null)
			throw new NullPointerException("Incremental scorer cannot be null");
		if (random == null)
			throw new NullPointerException("Random generator cannot be null");
		if (iterations < 0L)
			throw new IllegalArgumentException("Iteration count cannot be negative, have " + iterations);
		if (scorer.hasPendingSwap())
			throw new IllegalStateException("Cannot start optimization with an unresolved swap proposal");
		SwappablePointSet pointSet = scorer.getPointSet();
		if (pointSet.swapGroupCount() == 0)
			throw new IllegalArgumentException("Point set has no swappable dimension groups");
		if (pointSet.size() < 2)
			throw new IllegalArgumentException("Point set must contain at least two points to optimize");

		double initialScore = scorer.getCurrentNormalizedScore();
		long accepted = 0L;
		for (long i=0L; i<iterations; i++) {
			int group = random.nextInt(pointSet.swapGroupCount());
			int point1 = random.nextInt(pointSet.size());
			int point2 = random.nextInt(pointSet.size()-1);
			if (point2 >= point1)
				point2++;
			double delta = scorer.evaluateSwap(group, point1, point2);
			if (delta < 0d) {
				scorer.applySwap();
				accepted++;
			} else {
				scorer.discardSwap();
			}
		}
		return new PointSetOptimizationResult(iterations, accepted, initialScore,
				scorer.getCurrentNormalizedScore());
	}
}
