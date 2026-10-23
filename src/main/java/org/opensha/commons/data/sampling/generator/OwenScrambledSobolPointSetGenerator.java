package org.opensha.commons.data.sampling.generator;

import java.util.random.RandomGenerator;

import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.PointSet;

/**
 * Sobol generator with nested Owen scrambling. For each dimension and binary digit, a deterministic hash chooses
 * whether to exchange zero and one based on that dimension's random seed, the digit depth, and the already-scrambled
 * higher-order prefix. This implements the full nested construction without storing an exponentially large tree of
 * digit permutations.
 * <p>
 * A generator is stateful: every call draws one master scrambling seed from the supplied random generator and
 * therefore produces an independently scrambled realization. Each coordinate's seed is derived from that master seed
 * and its absolute dimension index, allowing any dimension block to be regenerated independently. It is not
 * thread-safe.
 *
 * @see <a href="https://jcgt.org/published/0009/04/01/paper.pdf">Practical Hash-based Owen Scrambling</a>
 */
public final class OwenScrambledSobolPointSetGenerator implements PointSetGenerator {

	private static final long DEPTH_MIX = 0x9e3779b97f4a7c15L;
	private static final long DIMENSION_MIX = 0xd1b54a32d192ed03L;

	private final RandomGenerator random;
	private final long startIndex;
	private final int firstDimension;

	public OwenScrambledSobolPointSetGenerator(RandomGenerator random) {
		this(random, 0L, 0);
	}

	public OwenScrambledSobolPointSetGenerator(RandomGenerator random, long startIndex) {
		this(random, startIndex, 0);
	}

	/**
	 * @param random source of one master scrambling seed per generated realization
	 * @param startIndex zero-based index of the first Sobol point
	 * @param firstDimension zero-based absolute Sobol dimension represented by output column zero
	 */
	public OwenScrambledSobolPointSetGenerator(RandomGenerator random, long startIndex, int firstDimension) {
		this.random = PointSetGeneratorUtils.requireRandom(random);
		if (startIndex < 0L || startIndex >= SobolPointSetData.MAX_INDEX_EXCLUSIVE)
			throw new IllegalArgumentException("Sobol starting index must be in [0,"
					+ SobolPointSetData.MAX_INDEX_EXCLUSIVE + "), have " + startIndex);
		this.startIndex = startIndex;
		if (firstDimension < 0 || firstDimension >= SobolPointSetGenerator.MAX_DIMENSIONS)
			throw new IllegalArgumentException("First Sobol dimension must be in [0,"
					+ SobolPointSetGenerator.MAX_DIMENSIONS + "), have " + firstDimension);
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
		PointSetGeneratorUtils.validateShape(numPoints, dimensions);
		long masterSeed = random.nextLong();
		return new ArrayPointSet(SobolPointSetData.generate(numPoints, dimensions, startIndex, firstDimension,
				(coordinate, dimension) -> scramble(coordinate, dimensionSeed(masterSeed, dimension))));
	}

	@Override
	public String toString() {
		return "Owen-Scrambled Sobol";
	}

	private static long scramble(long coordinate, long seed) {
		long scrambled = coordinate;
		for (int bit=SobolDirectionNumbers.BITS-1; bit>=0; bit--) {
			// A node in Owen's permutation tree is identified by its depth and the output prefix leading to it.
			long higherPrefix = scrambled & (-1L << (bit+1));
			long hash = mix64(seed ^ higherPrefix ^ DEPTH_MIX*(SobolDirectionNumbers.BITS-bit));
			if ((hash & 1L) != 0L)
				scrambled ^= 1L << bit;
		}
		return scrambled;
	}

	private static long dimensionSeed(long masterSeed, int dimension) {
		return mix64(masterSeed + DIMENSION_MIX*(dimension+1L));
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30))*0xbf58476d1ce4e5b9L;
		value = (value ^ (value >>> 27))*0x94d049bb133111ebL;
		return value ^ (value >>> 31);
	}
}
