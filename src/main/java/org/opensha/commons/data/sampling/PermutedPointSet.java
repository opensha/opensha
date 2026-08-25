package org.opensha.commons.data.sampling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link SwappablePointSet} that stores permutation overlays on an upstream point set. The upstream coordinates are
 * never modified. Each swap group owns one point-index permutation shared by all dimensions in that group, making a
 * grouped swap {@code O(1)} regardless of the number of dimensions moved together.
 * <p>
 * Swap groups must be disjoint. Dimensions omitted from all groups remain fixed at their original point indexes.
 */
public final class PermutedPointSet implements SwappablePointSet {

	private final PointSet source;
	private final List<DimensionSwapGroup> swapGroups;
	private final int[] dimensionGroups;
	private final int[][] permutations;
	private long modificationCount;

	public PermutedPointSet(PointSet source, List<DimensionSwapGroup> swapGroups) {
		if (source == null)
			throw new NullPointerException("Source point set cannot be null");
		if (swapGroups == null)
			throw new NullPointerException("Swap groups cannot be null");
		if (source.size() < 1 || source.dimensions() < 1)
			throw new IllegalArgumentException("Source point set must contain at least one point and dimension");
		this.source = source;
		this.dimensionGroups = new int[source.dimensions()];
		Arrays.fill(dimensionGroups, -1);
		List<DimensionSwapGroup> groupsCopy = new ArrayList<>(swapGroups.size());
		for (int g=0; g<swapGroups.size(); g++) {
			DimensionSwapGroup group = swapGroups.get(g);
			if (group == null)
				throw new NullPointerException("Swap group " + g + " cannot be null");
			for (int i=0; i<group.size(); i++) {
				int dimension = group.dimension(i);
				if (dimension >= source.dimensions())
					throw new IllegalArgumentException("Swap group " + g + " references dimension " + dimension
							+ " but source has " + source.dimensions() + " dimensions");
				if (dimensionGroups[dimension] >= 0)
					throw new IllegalArgumentException("Dimension " + dimension + " belongs to both swap groups "
							+ dimensionGroups[dimension] + " and " + g);
				dimensionGroups[dimension] = g;
			}
			groupsCopy.add(group);
		}
		this.swapGroups = Collections.unmodifiableList(groupsCopy);
		this.permutations = new int[swapGroups.size()][source.size()];
		for (int[] permutation : permutations)
			for (int p=0; p<permutation.length; p++)
				permutation[p] = p;
	}

	public PermutedPointSet(PointSet source, DimensionSwapGroup... swapGroups) {
		this(source, swapGroups == null ? null : List.of(swapGroups));
	}

	/**
	 * Builds a view with one independently swappable group per active dimension.
	 */
	public static PermutedPointSet independentDimensions(PointSet source) {
		if (source == null)
			throw new NullPointerException("Source point set cannot be null");
		List<DimensionSwapGroup> groups = new ArrayList<>(source.dimensions());
		for (int d=0; d<source.dimensions(); d++)
			if (source.getDimension(d).isActive())
				groups.add(new DimensionSwapGroup(d));
		return new PermutedPointSet(source, groups);
	}

	@Override
	public int size() {
		return source.size();
	}

	@Override
	public int dimensions() {
		return source.dimensions();
	}

	@Override
	public double get(int pointIndex, int dimensionIndex) {
		int group = dimensionGroups[dimensionIndex];
		int sourcePoint = group < 0 ? pointIndex : permutations[group][pointIndex];
		return source.get(sourcePoint, dimensionIndex);
	}

	@Override
	public SamplingDimension getDimension(int dimensionIndex) {
		return source.getDimension(dimensionIndex);
	}

	@Override
	public int swapGroupCount() {
		return swapGroups.size();
	}

	@Override
	public DimensionSwapGroup getSwapGroup(int groupIndex) {
		return swapGroups.get(groupIndex);
	}

	@Override
	public void swap(int groupIndex, int point1, int point2) {
		if (point1 == point2)
			return;
		int[] permutation = permutations[groupIndex];
		int sourcePoint = permutation[point1];
		permutation[point1] = permutation[point2];
		permutation[point2] = sourcePoint;
		modificationCount = Math.incrementExact(modificationCount);
	}

	@Override
	public long modificationCount() {
		return modificationCount;
	}

	/**
	 * @return upstream point currently supplying the selected group's values at a logical point
	 */
	public int getSourcePointIndex(int groupIndex, int pointIndex) {
		return permutations[groupIndex][pointIndex];
	}

	public PointSet getSource() {
		return source;
	}

	public List<DimensionSwapGroup> getSwapGroups() {
		return swapGroups;
	}
}
