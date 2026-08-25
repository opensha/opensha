package org.opensha.commons.data.sampling;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;

/**
 * Describes how a unit-hypercube dimension should be interpreted and scored.
 */
public interface SamplingDimension {

	/** @return discrepancy kernel for this dimension's ideal target distribution */
	DiscrepancyKernel getDiscrepancyKernel();
}
