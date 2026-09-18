package org.opensha.commons.data.sampling.optimization;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SwappablePointSet;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyConfig;

/**
 * Scalar quantity minimized by point-set optimization. An objective is stateless and reusable; {@link #prepare}
 * binds it to one swappable point set and creates any mutable caches needed while swaps are evaluated.
 */
@FunctionalInterface
public interface PointSetObjective {

	/** @return finite objective value, where lower values are better */
	double evaluate(PointSet pointSet);

	/**
	 * Prepares this objective for repeated swap proposals. The default implementation recalculates the full objective
	 * against a no-copy proposed-swap view. Specialized objectives can override this to maintain incremental state.
	 */
	default SwapSession prepare(SwappablePointSet pointSet) {
		return PointSetHillClimber.recalculatingSession(this, pointSet);
	}

	/**
	 * Creates the optimized quantized projection-discrepancy objective used by the quantized scorer. Most callers should
	 * obtain this through {@code ProjectionDiscrepancyScorer.quantized(...).objective(...)}.
	 */
	static PointSetObjective quantizedProjectionDiscrepancy(int continuousBins,
			ProjectionDiscrepancyConfig config) {
		return QuantizedProjectionSwapSession.objective(continuousBins, config);
	}

	/**
	 * Mutable state for optimizing one point set. This nested interface is primarily an implementation extension point;
	 * ordinary callers can pass an objective directly to {@link PointSetHillClimber}.
	 */
	interface SwapSession {

		SwappablePointSet getPointSet();

		/** @return current committed objective value */
		double getCurrentValue();

		/**
		 * Evaluates a grouped swap without changing the committed point set.
		 *
		 * @return candidate value minus current value; negative values are improvements
		 */
		double evaluateSwap(int groupIndex, int point1, int point2);

		/** Commits the most recently evaluated swap. */
		void applySwap();

		/** Discards the most recently evaluated swap. */
		void discardSwap();

		boolean hasPendingSwap();

		/** Rebuilds any retained state from the current committed point set and returns its objective value. */
		double recalculate();
	}
}
