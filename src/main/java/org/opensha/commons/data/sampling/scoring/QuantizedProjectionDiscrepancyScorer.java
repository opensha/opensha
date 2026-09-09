package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension.DiscretizedKernel;
import org.opensha.commons.data.sampling.optimization.PointSetObjective;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore.ProjectionResult;

/**
 * Low-memory implementation of quantized point-set scoring. It maps each coordinate to a finite kernel state and then
 * evaluates the same sample-pair formula as {@link ExactProjectionDiscrepancyScorer}, retaining {@code O(N*d)} state indexes rather
 * than dense multidimensional state tables. It supports arbitrary projection order but remains {@code O(P*N^2*k)}.
 * <p>
 * Both observations and ideal targets are represented in the same discrete state space, so quantization error is not
 * itself counted as discrepancy and IID-random normalized scores retain expectation 1. This direct implementation also
 * serves as the independent reference for stateful incremental scorers.
 */
final class QuantizedProjectionDiscrepancyScorer implements ProjectionDiscrepancyScorer {

	private final int continuousBins;

	QuantizedProjectionDiscrepancyScorer(int continuousBins) {
		if (continuousBins < 2)
			throw new IllegalArgumentException("Continuous quantization requires at least 2 bins, have " + continuousBins);
		this.continuousBins = continuousBins;
	}

	int getContinuousBins() {
		return continuousBins;
	}

	@Override
	public ProjectionDiscrepancyScore score(PointSet pointSet, ProjectionDiscrepancyConfig config) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = ProjectionDiscrepancyUtils.resolveProjections(pointSet, config);
		DiscretizedScoringData prepared = DiscretizedScoringData.build(pointSet, continuousBins);
		List<ProjectionResult> scores = new ArrayList<>(projections.size());
		for (PointSetProjection projection : projections)
			scores.add(scoreProjection(prepared, projection));
		return ProjectionDiscrepancyUtils.aggregate(scores, config);
	}

	@Override
	public PointSetObjective objective(ProjectionDiscrepancyConfig config) {
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		return PointSetObjective.quantizedProjectionDiscrepancy(continuousBins, config);
	}

	@Override
	public ProjectionResult scoreProjection(PointSet pointSet, PointSetProjection projection) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		ProjectionDiscrepancyUtils.resolveProjections(pointSet,
				ProjectionDiscrepancyConfig.builder().projections(projection).build());
		return scoreProjection(DiscretizedScoringData.build(pointSet, continuousBins), projection);
	}

	static ProjectionResult scoreProjection(DiscretizedScoringData prepared, PointSetProjection projection) {
		int n = prepared.numPoints;
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			DiscretizedKernel kernel = prepared.kernels[projection.dimension(i)];
			targetGrandMean *= kernel.targetGrandMean();
			targetDiagonalMean *= kernel.targetDiagonalMean();
		}

		double targetSum = 0d;
		for (int p=0; p<n; p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++) {
				int dimension = projection.dimension(i);
				product *= prepared.kernels[dimension].targetMean(prepared.states[dimension][p]);
			}
			targetSum += product;
		}

		double pairSum = 0d;
		for (int p1=0; p1<n; p1++) {
			for (int p2=0; p2<=p1; p2++) {
				double product = 1d;
				for (int i=0; i<projection.order(); i++) {
					int dimension = projection.dimension(i);
					product *= prepared.kernels[dimension].value(
							prepared.states[dimension][p1], prepared.states[dimension][p2]);
				}
				pairSum += p1 == p2 ? product : 2d*product;
			}
		}
		return ProjectionDiscrepancyUtils.projectionScore(projection, n, targetGrandMean, targetDiagonalMean,
				targetSum, pairSum);
	}

	/** Prepared finite-state kernels and state assignments used by this scorer. */
	private static final class DiscretizedScoringData {

		final int numPoints;
		final DiscretizedKernel[] kernels;
		final int[][] states;

		private DiscretizedScoringData(int numPoints, DiscretizedKernel[] kernels, int[][] states) {
			this.numPoints = numPoints;
			this.kernels = kernels;
			this.states = states;
		}

		static DiscretizedScoringData build(PointSet pointSet, int continuousBins) {
			DiscretizedKernel[] kernels =
					new DiscretizedKernel[pointSet.dimensions()];
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
						throw new IllegalStateException("Discretized kernel for dimension " + d
								+ " returned state " + state + " outside [0," + kernels[d].stateCount() + ")");
					states[d][p] = state;
				}
			}
			return new DiscretizedScoringData(pointSet.size(), kernels, states);
		}
	}
}
