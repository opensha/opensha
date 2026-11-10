package org.opensha.commons.data.sampling.scoring;

import java.util.Arrays;

/**
 * Immutable selection of point-set dimensions to score together. A projection extracts a lower-dimensional view of
 * every point: for example, projection {@code [1,4]} scores the 2D points formed from dimensions 1 and 4 while ignoring
 * all other coordinates. Scoring many low-order projections reveals whether each marginal, pair, or triple fills its
 * part of the hypercube well without relying on a single full-dimensional score.
 * <p>
 * Dimension indexes are unique and stored in ascending order, making projections insensitive to input index order.
 */
public final class PointSetProjection {

	private final int[] dimensions;

	public PointSetProjection(int... dimensions) {
		if (dimensions == null)
			throw new NullPointerException("Dimensions cannot be null");
		if (dimensions.length == 0)
			throw new IllegalArgumentException("A projection must contain at least one dimension");
		this.dimensions = dimensions.clone();
		Arrays.sort(this.dimensions);
		for (int i=0; i<this.dimensions.length; i++) {
			if (this.dimensions[i] < 0)
				throw new IllegalArgumentException("Dimension indexes must be nonnegative, have " + this.dimensions[i]);
			if (i > 0 && this.dimensions[i] == this.dimensions[i-1])
				throw new IllegalArgumentException("Duplicate dimension index: " + this.dimensions[i]);
		}
	}

	/** @return number of dimensions in this projection */
	public int order() {
		return dimensions.length;
	}

	/** @return dimension index at the given projection position */
	public int dimension(int index) {
		return dimensions[index];
	}

	/** @return copy of the dimension indexes */
	public int[] getDimensions() {
		return dimensions.clone();
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(dimensions);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof PointSetProjection
				&& Arrays.equals(dimensions, ((PointSetProjection)obj).dimensions);
	}

	@Override
	public String toString() {
		return Arrays.toString(dimensions);
	}
}
