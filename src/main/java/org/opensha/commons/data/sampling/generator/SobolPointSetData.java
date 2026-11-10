package org.opensha.commons.data.sampling.generator;

/** Raw 52-bit Sobol generation shared by deterministic and scrambled point-set generators. */
final class SobolPointSetData {

	static final long MAX_INDEX_EXCLUSIVE = 1L << SobolDirectionNumbers.BITS;
	static final double TO_UNIT_INTERVAL = 0x1.0p-52;

	private SobolPointSetData() {}

	@FunctionalInterface
	interface CoordinateScrambler {
		long scramble(long coordinate, int dimension);
	}

	static double[][] generate(int numPoints, int dimensions, long startIndex, CoordinateScrambler scrambler) {
		PointSetGeneratorUtils.validateShape(numPoints, dimensions);
		if (startIndex < 0L)
			throw new IllegalArgumentException("Sobol starting index cannot be negative, have " + startIndex);
		long endExclusive;
		try {
			endExclusive = Math.addExact(startIndex, numPoints);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("Sobol index range overflows long", e);
		}
		if (endExclusive > MAX_INDEX_EXCLUSIVE)
			throw new IllegalArgumentException("Sobol index range must end by " + MAX_INDEX_EXCLUSIVE
					+ ", have " + endExclusive);

		long[][] directions = SobolDirectionNumbers.forDimensions(dimensions);
		long[] state = stateAt(startIndex, directions);
		double[][] points = new double[numPoints][dimensions];
		for (int p=0; p<numPoints; p++) {
			for (int d=0; d<dimensions; d++) {
				long coordinate = scrambler == null ? state[d] : scrambler.scramble(state[d], d);
				if ((coordinate & -MAX_INDEX_EXCLUSIVE) != 0L)
					throw new IllegalStateException("Scrambler returned a coordinate outside 52 bits");
				points[p][d] = coordinate*TO_UNIT_INTERVAL;
			}
			long nextIndex = startIndex+p+1L;
			if (p+1 < numPoints) {
				int direction = Long.numberOfTrailingZeros(nextIndex);
				for (int d=0; d<dimensions; d++)
					state[d] ^= directions[d][direction];
			}
		}
		return points;
	}

	private static long[] stateAt(long index, long[][] directions) {
		long grayCode = index ^ (index >>> 1);
		long[] state = new long[directions.length];
		while (grayCode != 0L) {
			int bit = Long.numberOfTrailingZeros(grayCode);
			for (int d=0; d<directions.length; d++)
				state[d] ^= directions[d][bit];
			grayCode &= grayCode-1L;
		}
		return state;
	}
}
