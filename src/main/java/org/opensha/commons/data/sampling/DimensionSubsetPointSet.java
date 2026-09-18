package org.opensha.commons.data.sampling;

/** A no-copy view selecting an ordered subset of dimensions from another point set. */
public final class DimensionSubsetPointSet implements PointSet {

	private final PointSet source;
	private final int[] sourceDimensions;

	public DimensionSubsetPointSet(PointSet source, int... sourceDimensions) {
		if (source == null)
			throw new NullPointerException("Source point set cannot be null");
		if (sourceDimensions == null)
			throw new NullPointerException("Source dimensions cannot be null");
		if (sourceDimensions.length == 0)
			throw new IllegalArgumentException("At least one dimension must be selected");
		this.source = source;
		this.sourceDimensions = sourceDimensions.clone();
		for (int i=0; i<this.sourceDimensions.length; i++) {
			int dimension = this.sourceDimensions[i];
			if (dimension < 0 || dimension >= source.dimensions())
				throw new IndexOutOfBoundsException("Source dimension out of range: " + dimension);
			if (i > 0 && dimension <= this.sourceDimensions[i-1])
				throw new IllegalArgumentException("Source dimensions must be strictly increasing");
		}
	}

	public static DimensionSubsetPointSet range(PointSet source, int fromInclusive, int toExclusive) {
		if (source == null)
			throw new NullPointerException("Source point set cannot be null");
		if (fromInclusive < 0 || toExclusive > source.dimensions() || fromInclusive >= toExclusive)
			throw new IndexOutOfBoundsException("Invalid dimension range [" + fromInclusive + "," + toExclusive
					+ ") for " + source.dimensions() + " dimensions");
		int[] dimensions = new int[toExclusive-fromInclusive];
		for (int i=0; i<dimensions.length; i++)
			dimensions[i] = fromInclusive+i;
		return new DimensionSubsetPointSet(source, dimensions);
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
