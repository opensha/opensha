package org.opensha.commons.data.sampling;

import java.util.Arrays;

/**
 * Immutable group of dimensions that must be permuted together. For example, independent coordinate swapping uses one
 * group per dimension, while tree-to-tree branch reordering uses one group containing every dimension in a tree.
 */
public final class DimensionSwapGroup {

	private final int[] dimensions;

	public DimensionSwapGroup(int... dimensions) {
		if (dimensions == null)
			throw new NullPointerException("Dimensions cannot be null");
		if (dimensions.length == 0)
			throw new IllegalArgumentException("A swap group must contain at least one dimension");
		this.dimensions = dimensions.clone();
		Arrays.sort(this.dimensions);
		for (int i=0; i<this.dimensions.length; i++) {
			if (this.dimensions[i] < 0)
				throw new IllegalArgumentException("Dimension indexes must be nonnegative, have " + this.dimensions[i]);
			if (i > 0 && this.dimensions[i] == this.dimensions[i-1])
				throw new IllegalArgumentException("Duplicate dimension index: " + this.dimensions[i]);
		}
	}

	public int size() {
		return dimensions.length;
	}

	public int dimension(int index) {
		return dimensions[index];
	}

	public int[] getDimensions() {
		return dimensions.clone();
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(dimensions);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof DimensionSwapGroup
				&& Arrays.equals(dimensions, ((DimensionSwapGroup)obj).dimensions);
	}

	@Override
	public String toString() {
		return Arrays.toString(dimensions);
	}
}
