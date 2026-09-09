package org.opensha.commons.data.sampling.scoring;

import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.optimization.PointSetObjective;

/**
 * Calculates the squared centered discrepancy of continuous points in a unit hypercube. Centered discrepancy is a
 * standard space-filling measure that treats the center and both boundaries of each dimension symmetrically. Lower
 * values indicate a point set whose empirical distribution more closely resembles the continuous uniform target.
 * <p>
 * This implementation follows the usual convention, also used by SciPy's {@code qmc.discrepancy(method="CD")}, of
 * returning the squared quantity without taking a square root. Dimension metadata is checked deliberately: categorical
 * and inactive dimensions must be omitted with an explicit projection rather than interpreted as continuous geometry.
 */
public final class CenteredDiscrepancy {

	private static final double NEGATIVE_ROUNDOFF_TOLERANCE = 1e-12;
	private static final double ONE_DIMENSIONAL_TARGET_GRAND_MEAN = 13d/12d;

	private CenteredDiscrepancy() {}

	/**
	 * Calculates centered discrepancy across every dimension.
	 *
	 * @param pointSet continuous point set
	 * @return squared centered discrepancy
	 */
	public static double score(PointSet pointSet) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		int[] dimensions = new int[pointSet.dimensions()];
		for (int d=0; d<dimensions.length; d++)
			dimensions[d] = d;
		return scoreValidated(pointSet, new PointSetProjection(dimensions));
	}

	/**
	 * Calculates centered discrepancy after retaining only the selected dimensions.
	 *
	 * @param pointSet point set supplying coordinates
	 * @param projection continuous dimensions to retain
	 * @return squared centered discrepancy of the projected points
	 */
	public static double score(PointSet pointSet, PointSetProjection projection) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		ProjectionDiscrepancyUtils.validateProjection(projection, pointSet.dimensions());
		return scoreValidated(pointSet, projection);
	}

	/** @return optimization objective calculating centered discrepancy across every dimension */
	public static PointSetObjective objective() {
		return CenteredDiscrepancy::score;
	}

	/** @return optimization objective calculating centered discrepancy over the selected dimensions */
	public static PointSetObjective objective(PointSetProjection projection) {
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		return pointSet -> score(pointSet, projection);
	}

	private static double scoreValidated(PointSet pointSet, PointSetProjection projection) {
		for (int j=0; j<projection.order(); j++) {
			int dimension = projection.dimension(j);
			if (pointSet.getDimension(dimension) != ContinuousSamplingDimension.INSTANCE)
				throw new IllegalArgumentException("Centered discrepancy requires continuous dimensions; dimension "
						+dimension+" is "+pointSet.getDimension(dimension));
		}

		int numPoints = pointSet.size();
		double targetSum = 0d;
		double pairSum = 0d;
		for (int p1=0; p1<numPoints; p1++) {
			double targetProduct = 1d;
			double diagonalProduct = 1d;
			for (int j=0; j<projection.order(); j++) {
				double value = pointSet.get(p1, projection.dimension(j));
				targetProduct *= targetMean(value);
				diagonalProduct *= pairKernel(value, value);
			}
			targetSum += targetProduct;
			pairSum += diagonalProduct;
			// The kernel is symmetric, so evaluate each off-diagonal pair once and count both matrix entries.
			for (int p2=0; p2<p1; p2++) {
				double pairProduct = 1d;
				for (int j=0; j<projection.order(); j++) {
					int dimension = projection.dimension(j);
					pairProduct *= pairKernel(pointSet.get(p1, dimension), pointSet.get(p2, dimension));
				}
				pairSum += 2d*pairProduct;
			}
		}

		double targetGrandMean = Math.pow(ONE_DIMENSIONAL_TARGET_GRAND_MEAN, projection.order());
		double targetTerm = 2d*targetSum/numPoints;
		double pairTerm = pairSum/((double)numPoints*numPoints);
		double discrepancy = targetGrandMean-targetTerm+pairTerm;
		if (!Double.isFinite(discrepancy))
			throw new IllegalStateException("Calculated non-finite centered discrepancy for projection "+projection);
		if (discrepancy < 0d) {
			double scale = Math.max(1d, Math.abs(targetGrandMean)+Math.abs(targetTerm)+Math.abs(pairTerm));
			if (discrepancy >= -NEGATIVE_ROUNDOFF_TOLERANCE*scale)
				return 0d;
			throw new IllegalStateException("Calculated materially negative centered discrepancy "+discrepancy
					+" for projection "+projection);
		}
		return discrepancy;
	}

	/* Mean kernel similarity between a fixed coordinate and an ideal uniform coordinate. */
	private static double targetMean(double value) {
		double centered = value-0.5d;
		return 1d+0.5d*Math.abs(centered)-0.5d*centered*centered;
	}

	/* One-dimensional centered-discrepancy kernel for two observed coordinates. */
	private static double pairKernel(double value1, double value2) {
		return 1d+0.5d*Math.abs(value1-0.5d)+0.5d*Math.abs(value2-0.5d)
				-0.5d*Math.abs(value1-value2);
	}
}
