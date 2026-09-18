package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.scoring.ExactProjectionDiscrepancyScorer.ExactScoringData;
import org.opensha.commons.data.sampling.scoring.ExactProjectionDiscrepancyScorer.ExactScoringData.PreparedDimension;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore.ProjectionResult;

/**
 * Straightforward exact scorer retained as a readable statement of the product-kernel discrepancy calculation and as
 * a correctness reference for optimized implementations. It follows the formula directly, without quantization,
 * symmetry reduction, categorical shortcuts, shared projection products, or parallelism.
 * <p>
 * Its cost for {@code P} projections of order {@code k} is {@code O(P*N^2*k)}. Use {@link ExactProjectionDiscrepancyScorer} for
 * production scoring of nontrivial point sets.
 */
final class ReferenceProjectionDiscrepancyScorer implements ProjectionDiscrepancyScorer {

	@Override
	public ProjectionDiscrepancyScore score(PointSet pointSet, ProjectionDiscrepancyConfig config) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		List<PointSetProjection> projections = ProjectionDiscrepancyUtils.resolveProjections(pointSet, config);
		ExactScoringData prepared = ExactScoringData.build(pointSet);
		List<ProjectionResult> scores = new ArrayList<>(projections.size());
		for (PointSetProjection projection : projections)
			scores.add(scoreProjection(prepared, projection));
		return ProjectionDiscrepancyUtils.aggregate(scores, config);
	}

	/** Scores one projection using the direct reference calculation. */
	public ProjectionResult scoreProjection(PointSet pointSet, PointSetProjection projection) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		ProjectionDiscrepancyUtils.resolveProjections(pointSet,
				ProjectionDiscrepancyConfig.builder().projections(projection).build());
		return scoreProjection(ExactScoringData.build(pointSet), projection);
	}

	private static ProjectionResult scoreProjection(ExactScoringData prepared, PointSetProjection projection) {
		PreparedDimension[] dimensions = new PreparedDimension[projection.order()];
		double targetGrandMean = 1d;
		double targetDiagonalMean = 1d;
		for (int i=0; i<dimensions.length; i++) {
			dimensions[i] = prepared.dimensions[projection.dimension(i)];
			targetGrandMean *= dimensions[i].targetGrandMean;
			targetDiagonalMean *= dimensions[i].targetDiagonalMean;
		}

		double targetSum = 0d;
		for (int point=0; point<prepared.numPoints; point++) {
			double product = 1d;
			for (PreparedDimension dimension : dimensions)
				product *= dimension.targetMeans[point];
			targetSum += product;
		}

		double pairSum = 0d;
		for (int point1=0; point1<prepared.numPoints; point1++) {
			for (int point2=0; point2<prepared.numPoints; point2++) {
				double product = 1d;
				for (PreparedDimension dimension : dimensions)
					product *= dimension.pairValue(point1, point2);
				pairSum += product;
			}
		}

		return ProjectionDiscrepancyUtils.projectionScore(projection, prepared.numPoints, targetGrandMean,
				targetDiagonalMean, targetSum, pairSum);
	}
}
