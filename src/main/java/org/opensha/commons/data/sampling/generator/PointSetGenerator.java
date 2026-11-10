package org.opensha.commons.data.sampling.generator;

import org.opensha.commons.data.sampling.PointSet;

/** Generates finite point sets in the unit hypercube. */
public interface PointSetGenerator {

	/**
	 * @param numPoints number of points to generate
	 * @param dimensions number of coordinates in each point
	 * @return generated point set in {@code [0,1)^dimensions}
	 */
	PointSet generate(int numPoints, int dimensions);
}
