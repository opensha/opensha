package org.opensha.commons.data.sampling;

import org.opensha.commons.data.sampling.scoring.DiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;

/**
 * Marker for a retained coordinate that does not affect the sampled result. Inactive dimensions keep stable external
 * dimension indexes but are omitted from scoring and optimization.
 */
public final class InactiveSamplingDimension implements SamplingDimension {

	public static final InactiveSamplingDimension INSTANCE = new InactiveSamplingDimension();

	private InactiveSamplingDimension() {}

	@Override
	public boolean isActive() {
		return false;
	}

	@Override
	public DiscrepancyKernel getDiscrepancyKernel() {
		return ContinuousSamplingDimension.INSTANCE.getDiscrepancyKernel();
	}

	@Override
	public DiscretizedDiscrepancyKernel getDiscretizedKernel(int preferredBins) {
		return ContinuousSamplingDimension.INSTANCE.getDiscretizedKernel(preferredBins);
	}
	
	@Override
	public String toString() {
		return "InactiveSamplingDimension";
	}
}
