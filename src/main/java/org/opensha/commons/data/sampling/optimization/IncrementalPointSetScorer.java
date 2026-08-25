package org.opensha.commons.data.sampling.optimization;

import org.opensha.commons.data.sampling.SwappablePointSet;
import org.opensha.commons.data.sampling.scoring.PointSetScore;

/**
 * Stateful scoring session for transactional point-set swaps. A proposal must be either applied or discarded before
 * another proposal is evaluated.
 */
public interface IncrementalPointSetScorer {

	SwappablePointSet getPointSet();

	/** @return current aggregate normalized score without constructing a detailed result */
	double getCurrentNormalizedScore();

	/** @return detailed immutable snapshot of the current projection scores */
	PointSetScore getCurrentScore();

	/**
	 * Evaluates a grouped swap without changing the point set or committed scorer state.
	 *
	 * @return change in aggregate normalized score; negative values are improvements
	 */
	double evaluateSwap(int groupIndex, int point1, int point2);

	/** Applies the most recently evaluated swap to scorer caches and the point set. */
	void applySwap();

	/** Discards the most recently evaluated swap. */
	void discardSwap();

	boolean hasPendingSwap();

	/** Rebuilds retained scoring caches from the current committed state. */
	PointSetScore recalculate();
}
