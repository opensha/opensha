package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.sampling.PointSet;

/**
 * Low-memory implementation of quantized point-set scoring. It maps each coordinate to a finite kernel state and then
 * evaluates the same sample-pair formula as {@link ExactPointSetScorer}, retaining {@code O(N*d)} state indexes rather
 * than dense multidimensional state tables. It supports arbitrary projection order but remains {@code O(P*N^2*k)}.
 * <p>
 * Both observations and ideal targets are represented in the same discrete state space, so quantization error is not
 * itself counted as discrepancy and IID-random normalized scores retain expectation 1. This direct implementation also
 * serves as the independent reference for stateful incremental scorers.
 */
public final class QuantizedPointSetScorer implements PointSetScorer {

	private final int continuousBins;

	public QuantizedPointSetScorer(int continuousBins) {
		if (continuousBins < 2)
			throw new IllegalArgumentException("Continuous quantization requires at least 2 bins, have " + continuousBins);
		this.continuousBins = continuousBins;
	}

	public int getContinuousBins() {
		return continuousBins;
	}

	@Override
	public PointSetScore score(PointSet pointSet, PointSetScoringConfig config) {
		PointSetScoringUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = PointSetScoringUtils.resolveProjections(pointSet, config);
		DiscretizedPointSetData prepared = DiscretizedPointSetData.build(pointSet, continuousBins);
		List<ProjectionScore> scores = new ArrayList<>(projections.size());
		for (PointSetProjection projection : projections)
			scores.add(scoreProjection(prepared, projection));
		return PointSetScoringUtils.aggregate(scores, config);
	}

	ProjectionScore scoreProjection(PointSet pointSet, PointSetProjection projection) {
		PointSetScoringUtils.validatePointSet(pointSet);
		PointSetScoringUtils.resolveProjections(pointSet,
				PointSetScoringConfig.builder().projections(projection).build());
		return scoreProjection(DiscretizedPointSetData.build(pointSet, continuousBins), projection);
	}

	static ProjectionScore scoreProjection(DiscretizedPointSetData prepared, PointSetProjection projection) {
		int n = prepared.numPoints;
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			DiscretizedDiscrepancyKernel kernel = prepared.kernels[projection.dimension(i)];
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
		return PointSetScoringUtils.projectionScore(projection, n, targetGrandMean, targetDiagonalMean,
				targetSum, pairSum);
	}
}
