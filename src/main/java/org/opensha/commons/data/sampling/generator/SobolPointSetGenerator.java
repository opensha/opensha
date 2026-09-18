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

	public SobolPointSetGenerator() {
		this(0L);
	}

	/**
	 * @param startIndex zero-based index of the first Sobol point
	 */
	public SobolPointSetGenerator(long startIndex) {
		if (startIndex < 0L || startIndex >= SobolPointSetData.MAX_INDEX_EXCLUSIVE)
			throw new IllegalArgumentException("Sobol starting index must be in [0,"
					+ SobolPointSetData.MAX_INDEX_EXCLUSIVE + "), have " + startIndex);
		this.startIndex = startIndex;
	}

	public long getStartIndex() {
		return startIndex;
	}

	@Override
	public PointSet generate(int numPoints, int dimensions) {
		return new ArrayPointSet(SobolPointSetData.generate(numPoints, dimensions, startIndex, null));
	}

	@Override
	public String toString() {
		return "Sobol";
	}
}
