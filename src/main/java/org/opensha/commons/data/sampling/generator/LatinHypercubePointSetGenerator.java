package org.opensha.commons.data.sampling.generator;

import java.util.random.RandomGenerator;

import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.PointSet;

/**
 * Random Latin hypercube sampling. Each dimension contains exactly one independently jittered coordinate in every
 * equal-probability stratum, and those coordinates are independently permuted among points. Successive calls advance
 * the supplied random generator; instances are stateful and not thread-safe.
 */
public final class LatinHypercubePointSetGenerator implements PointSetGenerator {

	private final RandomGenerator random;

	public LatinHypercubePointSetGenerator(RandomGenerator random) {
		this.random = PointSetGeneratorUtils.requireRandom(random);
	}

	@Override
	public PointSet generate(int numPoints, int dimensions) {
		PointSetGeneratorUtils.validateShape(numPoints, dimensions);
		double[][] points = new double[numPoints][dimensions];
		double[] dimensionValues = new double[numPoints];
		for (int d=0; d<dimensions; d++) {
			for (int stratum=0; stratum<numPoints; stratum++) {
				double value = (stratum+random.nextDouble())/numPoints;
				// At very large N, rounding the uppermost jittered stratum must not produce the excluded endpoint 1.
				dimensionValues[stratum] = Math.min(value, Math.nextDown(1d));
			}
			PointSetGeneratorUtils.shuffle(dimensionValues, random);
			for (int p=0; p<numPoints; p++)
				points[p][d] = dimensionValues[p];
		}
		return new ArrayPointSet(points);
	}

	@Override
	public String toString() {
		return "Latin Hypercube";
	}
}
