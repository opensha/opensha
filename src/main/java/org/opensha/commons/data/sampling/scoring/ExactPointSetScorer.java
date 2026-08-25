package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.scoring.ExactPointSetData.PreparedDimension;

/**
 * Exact product-kernel discrepancy scorer for finite point sets. Each projection is compared with its ideal product
 * distribution and divided by its expected score for IID samples from that target.
 * <p>
 * This reference implementation is intentionally unquantized. For {@code P} projections of order {@code k}, its
 * dominant cost is {@code O(P*N^2*k)} and it does not allocate point-pair matrices.
 */
public final class ExactPointSetScorer implements PointSetScorer {

	@Override
	public PointSetScore score(PointSet pointSet, PointSetScoringConfig config) {
		PointSetScoringUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = PointSetScoringUtils.resolveProjections(pointSet, config);
		ExactPointSetData prepared = ExactPointSetData.build(pointSet);

		List<ProjectionScore> scores = new ArrayList<>(projections.size());
		for (PointSetProjection projection : projections)
			scores.add(scoreProjectionPrepared(prepared, projection));
		return PointSetScoringUtils.aggregate(scores, config);
	}

	/**
	 * Scores one projection directly. This is primarily useful for diagnostics and verification; general callers can
	 * select one projection through {@link PointSetScoringConfig} and the {@link PointSetScorer} interface.
	 */
	public ProjectionScore scoreProjection(PointSet pointSet, PointSetProjection projection) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		PointSetScoringUtils.validatePointSet(pointSet);
		PointSetScoringUtils.resolveProjections(pointSet,
				PointSetScoringConfig.builder().projections(projection).build());
		return scoreProjectionPrepared(ExactPointSetData.build(pointSet), projection);
	}

	private ProjectionScore scoreProjectionPrepared(ExactPointSetData prepared, PointSetProjection projection) {
		int n = prepared.numPoints;
		PreparedDimension[] dimensions = new PreparedDimension[projection.order()];
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			dimensions[i] = prepared.dimensions[projection.dimension(i)];
			targetGrandMean *= dimensions[i].targetGrandMean;
			targetDiagonalMean *= dimensions[i].targetDiagonalMean;
		}
		requireFinite(targetGrandMean, "product target grand mean", projection);
		requireFinite(targetDiagonalMean, "product target diagonal mean", projection);

		double targetSum = 0d;
		// For each observed projected point, multiply its per-dimension similarity to the ideal target. Averaging these
		// products gives the sample-to-target term in the squared distribution distance.
		for (int p=0; p<n; p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++)
				product *= dimensions[i].targetMeans[p];
			requireFinite(product, "product target mean", projection);
			targetSum += product;
		}
		requireFinite(targetSum, "target-mean sum", projection);

		// Compare every pair of observed projected points. Multiplying kernels means a pair is similar in the projection
		// only to the extent that it is similar in every included dimension. Use symmetry to halve kernel evaluations
		// without materializing an N x N matrix.
		double pairSum = 0d;
		for (int p1=0; p1<n; p1++) {
			double diagonalProduct = 1d;
			for (int i=0; i<projection.order(); i++)
				diagonalProduct *= dimensions[i].diagonalValues[p1];
			requireFinite(diagonalProduct, "product diagonal kernel value", projection);
			pairSum += diagonalProduct;
			for (int p2=0; p2<p1; p2++) {
				double product = 1d;
				for (int i=0; i<projection.order(); i++) {
					product *= requireFinite(dimensions[i].pairValue(p1, p2), "kernel value", projection);
					if (product == 0d)
						break;
				}
				requireFinite(product, "product kernel value", projection);
				pairSum += 2d*product;
			}
		}
		requireFinite(pairSum, "kernel-pair sum", projection);

		// Shared finalization applies target-target - 2*sample-target + sample-sample and IID normalization.
		return PointSetScoringUtils.projectionScore(projection, n, targetGrandMean, targetDiagonalMean,
				targetSum, pairSum);
	}

	private static double requireFinite(double value, String quantity, PointSetProjection projection) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Non-finite " + quantity + " for projection " + projection + ": " + value);
		return value;
	}

}
