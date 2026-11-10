package org.opensha.commons.data.sampling;

import java.util.random.RandomGenerator;

/** Randomly assigns every source coordinate column to one destination dimension. */
public final class RandomDimensionPermutationTransform implements PointSetTransform {

	private final RandomGenerator random;

	public RandomDimensionPermutationTransform(RandomGenerator random) {
		if (random == null)
			throw new NullPointerException("Random generator cannot be null");
		this.random = random;
	}

	@Override
	public PointSet apply(PointSet pointSet) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		int[] permutation = new int[pointSet.dimensions()];
		for (int d=0; d<permutation.length; d++)
			permutation[d] = d;
		for (int d=permutation.length-1; d>0; d--) {
			int swap = random.nextInt(d+1);
			int sourceDimension = permutation[d];
			permutation[d] = permutation[swap];
			permutation[swap] = sourceDimension;
		}
		return new DimensionPermutedPointSet(pointSet, permutation);
	}
}
