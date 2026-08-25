package org.opensha.commons.data.sampling;

/**
 * A finite set of points in the {@code [0,1)^d} unit hypercube. Point indexes identify samples and dimension indexes
 * identify coordinates within each sample.
 */
public interface PointSet {

	/** @return number of points in this set */
	int size();

	/** @return number of dimensions in each point */
	int dimensions();

	/**
	 * @param pointIndex point index
	 * @param dimensionIndex dimension index
	 * @return coordinate in {@code [0,1)}
	 */
	double get(int pointIndex, int dimensionIndex);

	/**
	 * Returns the interpretation of a dimension. Generic point sets are continuous unless decorated with more specific
	 * metadata.
	 *
	 * @param dimensionIndex dimension index
	 * @return dimension definition
	 */
	default SamplingDimension getDimension(int dimensionIndex) {
		if (dimensionIndex < 0 || dimensionIndex >= dimensions())
			throw new IndexOutOfBoundsException("Dimension index out of range: " + dimensionIndex);
		return ContinuousSamplingDimension.INSTANCE;
	}
}
