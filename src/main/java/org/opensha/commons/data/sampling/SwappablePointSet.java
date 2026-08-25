package org.opensha.commons.data.sampling;

/**
 * A point-set view whose configured dimension groups can be permuted among point indexes. Implementations must apply a
 * swap atomically to every dimension in the selected group.
 */
public interface SwappablePointSet extends PointSet {

	int swapGroupCount();

	DimensionSwapGroup getSwapGroup(int groupIndex);

	/**
	 * Monotonically increasing count of applied, nontrivial swaps. Stateful scorers use this to detect changes made
	 * outside their own transactional operations.
	 */
	long modificationCount();

	/**
	 * Swaps one group's assignments between two logical points.
	 *
	 * @param groupIndex swap-group index
	 * @param point1 first logical point
	 * @param point2 second logical point
	 */
	void swap(int groupIndex, int point1, int point2);
}
