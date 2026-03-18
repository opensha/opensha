package org.opensha.commons.util;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;

public class RandomSeedUtils {
	
	/**
	 * Generates a 64-bit random seed that is a repeatable and psuedo-unique combination of the input seeds.
	 * 
	 * This is based on {@link Arrays#hashCode(int[])}, but modified for longs.
	 * 
	 * @param seeds
	 * @return
	 */
	public static long uniqueSeedCombination(List<Long> seeds) {
		Preconditions.checkState(!seeds.isEmpty());
		
		long result = 1;
		for (long element : seeds)
			result = 31l * result + element;
		return mix64(result);
	}
	
	/**
	 * Generates a 64-bit random seed that is a repeatable and psuedo-unique combination of the input seeds.
	 * 
	 * This is based on {@link Arrays#hashCode(int[])}, but modified for longs.
	 * 
	 * @param seeds
	 * @return
	 */
	public static long uniqueSeedCombination(long... seeds) {
		Preconditions.checkState(seeds != null && seeds.length > 0);
		
		long result = 1;
		for (long element : seeds)
			result = 31l * result + element;
		return mix64(result);
	}
	
	/**
	 * Bit-mixes the given ints to longs for use as input to {@link #uniqueSeedCombination(long...)}
	 * @param ints
	 * @return
	 */
	public static long[] getMixed64(List<Integer> ints) {
		long[] ret = new long[ints.size()];
		for (int i=0; i<ints.size(); i++)
			ret[i] = mix64(ints.get(i));
		return ret;
	}
	
	/**
	 * Bit-mixes the given ints to longs for use as input to {@link #uniqueSeedCombination(long...)}
	 * @param ints
	 * @return
	 */
	public static long[] getMixed64(int... ints) {
		long[] ret = new long[ints.length];
		for (int i=0; i<ints.length; i++)
			ret[i] = mix64(ints[i]);
		return ret;
	}
	
	/**
	 * Applies a 64-bit avalanche mixing function to the given value.
	 * <p>
	 * This method is based on the finalization step of the SplitMix64
	 * generator (see Steele et al., 2014) and is commonly used as a
	 * high-quality bit mixer. It is <em>not</em> a random number generator
	 * itself, but a deterministic transformation that diffuses input bits
	 * so that small changes in the input (even a single bit) produce large,
	 * seemingly unrelated changes in the output.
	 * </p>
	 *
	 * <p>
	 * This is useful when constructing seeds from structured or correlated
	 * inputs (e.g., spatial coordinates, hash combinations, rolling hashes),
	 * where a simple linear combination may retain detectable structure.
	 * Applying this mixer before seeding a PRNG (such as {@link java.util.Random}
	 * or {@link java.util.SplittableRandom}) helps reduce inter-seed correlation
	 * and improves statistical independence between streams.
	 * </p>
	 *
	 * <p>
	 * Properties:
	 * <ul>
	 *   <li>Deterministic and repeatable.</li>
	 *   <li>Full 64-bit avalanche behavior.</li>
	 *   <li>One-to-one mapping over 64-bit values (mod 2^64).</li>
	 * </ul>
	 * </p>
	 *
	 * @param z input value to mix
	 * @return a mixed 64-bit value suitable for use as a high-quality PRNG seed
	 */
	public static long mix64(long z) {
		z ^= (z >>> 33);
		z *= 0xff51afd7ed558ccdL;
		z ^= (z >>> 33);
		z *= 0xc4ceb9fe1a85ec53L;
		z ^= (z >>> 33);
		return z;
	}

}
