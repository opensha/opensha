package org.opensha.commons.data.sampling;

/** A no-copy view that reorders all dimensions of another point set. */
public final class DimensionPermutedPointSet implements PointSet {

	private final PointSet source;
	private final int[] sourceDimensions;

	/**
	 * @param source source point set
	 * @param sourceDimensions source dimension supplying each destination dimension; must be a complete permutation
	 */
	public DimensionPermutedPointSet(PointSet source, int... sourceDimensions) {
		if (source == null)
			throw new NullPointerException("Source point set cannot be null");
		if (sourceDimensions == null)
			throw new NullPointerException("Source dimensions cannot be null");
		if (sourceDimensions.length != source.dimensions())
			throw new IllegalArgumentException("Supplied " + sourceDimensions.length
					+ " source dimensions for a point set with " + source.dimensions() + " dimensions");
		this.source = source;
		this.sourceDimensions = sourceDimensions.clone();
		boolean[] used = new boolean[source.dimensions()];
		for (int d=0; d<this.sourceDimensions.length; d++) {
			int sourceDimension = this.sourceDimensions[d];
			if (sourceDimension < 0 || sourceDimension >= source.dimensions())
				throw new IndexOutOfBoundsException("Source dimension out of range: " + sourceDimension);
			if (used[sourceDimension])
				throw new IllegalArgumentException("Source dimension " + sourceDimension + " is repeated");
			used[sourceDimension] = true;
		}
	}

	@Override public int size() { return source.size(); }
	@Override public int dimensions() { return sourceDimensions.length; }
	@Override public double get(int pointIndex, int dimensionIndex) {
		return source.get(pointIndex, sourceDimensions[dimensionIndex]);
	}
	@Override public SamplingDimension getDimension(int dimensionIndex) {
		return source.getDimension(sourceDimensions[dimensionIndex]);
	}

	public PointSet getSource() { return source; }
	public int getSourceDimensionIndex(int dimensionIndex) { return sourceDimensions[dimensionIndex]; }
	public int[] getSourceDimensionIndexes() { return sourceDimensions.clone(); }
}
