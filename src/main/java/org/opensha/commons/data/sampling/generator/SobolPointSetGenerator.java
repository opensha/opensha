package org.opensha.commons.data.sampling.generator;

import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.PointSet;

/**
 * Deterministic 52-bit Sobol generator using the Joe-Kuo D(6) direction numbers through dimension 21,201. The default
 * sequence starts at index zero, whose coordinates are all zero.
 */
public final class SobolPointSetGenerator implements PointSetGenerator {

	/** Number of binary digits retained in each coordinate. */
	public static final int BITS = SobolDirectionNumbers.BITS;
	/** Maximum dimensionality supported by the bundled Joe-Kuo table. */
	public static final int MAX_DIMENSIONS = SobolDirectionNumbers.MAX_DIMENSIONS;

	private final long startIndex;
	private final int firstDimension;

	public SobolPointSetGenerator() {
		this(0L, 0);
	}

	/**
	 * @param startIndex zero-based index of the first Sobol point
	 */
	public SobolPointSetGenerator(long startIndex) {
		this(startIndex, 0);
	}

	/**
	 * @param startIndex zero-based index of the first Sobol point
	 * @param firstDimension zero-based absolute Sobol dimension represented by output column zero
	 */
	public SobolPointSetGenerator(long startIndex, int firstDimension) {
		if (startIndex < 0L || startIndex >= SobolPointSetData.MAX_INDEX_EXCLUSIVE)
			throw new IllegalArgumentException("Sobol starting index must be in [0,"
					+ SobolPointSetData.MAX_INDEX_EXCLUSIVE + "), have " + startIndex);
		this.startIndex = startIndex;
		if (firstDimension < 0 || firstDimension >= MAX_DIMENSIONS)
			throw new IllegalArgumentException("First Sobol dimension must be in [0," + MAX_DIMENSIONS
					+ "), have " + firstDimension);
		this.firstDimension = firstDimension;
	}

	public long getStartIndex() {
		return startIndex;
	}

	public int getFirstDimension() {
		return firstDimension;
	}

	@Override
	public PointSet generate(int numPoints, int dimensions) {
		return new ArrayPointSet(SobolPointSetData.generate(
				numPoints, dimensions, startIndex, firstDimension, null));
	}

	@Override
	public String toString() {
		return "Sobol";
	}
}
