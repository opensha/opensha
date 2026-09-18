package org.opensha.commons.data.sampling.generator;

import java.util.random.RandomGenerator;

final class PointSetGeneratorUtils {

	private PointSetGeneratorUtils() {}

	static void validateShape(int numPoints, int dimensions) {
		if (numPoints < 1)
			throw new IllegalArgumentException("Number of points must be positive, have " + numPoints);
		if (dimensions < 1)
			throw new IllegalArgumentException("Number of dimensions must be positive, have " + dimensions);
	}

	static RandomGenerator requireRandom(RandomGenerator random) {
		if (random == null)
			throw new NullPointerException("Random generator cannot be null");
		return random;
	}

	static void shuffle(double[] values, RandomGenerator random) {
		for (int i=values.length-1; i>0; i--) {
			int j = random.nextInt(i+1);
			double value = values[i];
			values[i] = values[j];
			values[j] = value;
		}
	}
}
