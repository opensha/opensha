package org.opensha.commons.data.sampling.scoring;

import org.opensha.commons.data.sampling.PointSet;

/** Prepared finite-state kernels and state assignments shared by quantized scorer implementations. */
final class DiscretizedPointSetData {

	final int numPoints;
	final DiscretizedDiscrepancyKernel[] kernels;
	final int[][] states;

	private DiscretizedPointSetData(int numPoints, DiscretizedDiscrepancyKernel[] kernels, int[][] states) {
		this.numPoints = numPoints;
		this.kernels = kernels;
		this.states = states;
	}

	static DiscretizedPointSetData build(PointSet pointSet, int continuousBins) {
		DiscretizedDiscrepancyKernel[] kernels = new DiscretizedDiscrepancyKernel[pointSet.dimensions()];
		int[][] states = new int[pointSet.dimensions()][pointSet.size()];
		for (int d=0; d<pointSet.dimensions(); d++) {
			if (!pointSet.getDimension(d).isActive())
				continue;
			kernels[d] = pointSet.getDimension(d).getDiscretizedKernel(continuousBins);
			if (kernels[d] == null)
				throw new NullPointerException("Discretized kernel for dimension " + d + " is null");
			if (kernels[d].stateCount() < 2)
				throw new IllegalStateException("Discretized kernel for dimension " + d
						+ " must contain at least 2 states, have " + kernels[d].stateCount());
			for (int p=0; p<pointSet.size(); p++) {
				int state = kernels[d].state(pointSet.get(p, d));
				if (state < 0 || state >= kernels[d].stateCount())
					throw new IllegalStateException("Discretized kernel for dimension " + d + " returned state "
							+ state + " outside [0," + kernels[d].stateCount() + ")");
				states[d][p] = state;
			}
		}
		return new DiscretizedPointSetData(pointSet.size(), kernels, states);
	}
}
