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

	/** @return a newly allocated copy of one point's coordinates */
	default double[] getPoint(int pointIndex) {
		if (pointIndex < 0 || pointIndex >= size())
			throw new IndexOutOfBoundsException("Point index out of range: " + pointIndex);
		double[] point = new double[dimensions()];
		for (int d=0; d<point.length; d++)
			point[d] = get(pointIndex, d);
		return point;
	}

	/** @return newly allocated coordinates for one dimension across every point */
	default double[] getDimensionValues(int dimensionIndex) {
		if (dimensionIndex < 0 || dimensionIndex >= dimensions())
			throw new IndexOutOfBoundsException("Dimension index out of range: " + dimensionIndex);
		double[] values = new double[size()];
		for (int p=0; p<values.length; p++)
			values[p] = get(p, dimensionIndex);
		return values;
	}

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
