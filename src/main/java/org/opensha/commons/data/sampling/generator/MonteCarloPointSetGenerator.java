package org.opensha.commons.data.sampling.generator;

import java.util.random.RandomGenerator;

import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.PointSet;

/**
 * Independent Monte Carlo sampling of every point coordinate. Successive calls advance the supplied random generator;
 * instances are stateful and not thread-safe.
 */
public final class MonteCarloPointSetGenerator implements PointSetGenerator {

	private final RandomGenerator random;

	public MonteCarloPointSetGenerator(RandomGenerator random) {
		this.random = PointSetGeneratorUtils.requireRandom(random);
	}

	@Override
	public PointSet generate(int numPoints, int dimensions) {
		PointSetGeneratorUtils.validateShape(numPoints, dimensions);
		double[][] points = new double[numPoints][dimensions];
		for (int p=0; p<numPoints; p++)
			for (int d=0; d<dimensions; d++)
				points[p][d] = random.nextDouble();
		return new ArrayPointSet(points);
	}

	@Override
	public String toString() {
		return "Monte Carlo";
	}
}
