package org.opensha.commons.data.sampling;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;

/**
 * Describes how a unit-hypercube dimension should be interpreted and scored.
 */
public interface SamplingDimension {

	/**
	 * @return whether this dimension participates in scoring and optimization
	 */
	default boolean isActive() {
		return true;
	}

	/** @return discrepancy kernel for this dimension's ideal target distribution */
	DiscrepancyKernel getDiscrepancyKernel();

	/**
	 * Builds a finite-state representation for quantized scoring. The requested bin count applies to continuous
	 * dimensions; dimensions with intrinsic finite states, such as categories, can ignore it.
	 *
	 * @param preferredBins requested number of continuous bins
	 * @return finite-state discrepancy kernel
	 */
	DiscretizedDiscrepancyKernel getDiscretizedKernel(int preferredBins);
}
