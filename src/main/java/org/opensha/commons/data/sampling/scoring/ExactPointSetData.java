package org.opensha.commons.data.sampling.scoring;

import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;

/** Coordinates and exact kernel quantities prepared once before any quadratic projection scoring. */
final class ExactPointSetData {

	final int numPoints;
	final PreparedDimension[] dimensions;

	private ExactPointSetData(int numPoints, PreparedDimension[] dimensions) {
		this.numPoints = numPoints;
		this.dimensions = dimensions;
	}

	static ExactPointSetData build(PointSet pointSet) {
		PreparedDimension[] dimensions = new PreparedDimension[pointSet.dimensions()];
		for (int d=0; d<dimensions.length; d++) {
			SamplingDimension dimension = pointSet.getDimension(d);
			if (!dimension.isActive())
				continue;
			DiscrepancyKernel kernel = dimension.getDiscrepancyKernel();
			if (kernel == null)
				throw new NullPointerException("Discrepancy kernel for dimension " + d + " is null");
			double[] values = new double[pointSet.size()];
			double[] targetMeans = new double[pointSet.size()];
			double[] diagonalValues = new double[pointSet.size()];
			int[] categoricalStates = dimension instanceof CategoricalSamplingDimension ? new int[pointSet.size()] : null;
			for (int p=0; p<pointSet.size(); p++) {
				double value = pointSet.get(p, d);
				values[p] = value;
				if (categoricalStates == null) {
					targetMeans[p] = requireFinite(kernel.targetMean(value), "target mean", d, p);
					diagonalValues[p] = requireFinite(kernel.value(value, value), "diagonal kernel value", d, p);
				} else {
					CategoricalSamplingDimension categorical = (CategoricalSamplingDimension)dimension;
					int state = categorical.categoryIndex(value);
					categoricalStates[p] = state;
					targetMeans[p] = categorical.categoryProbability(state);
					diagonalValues[p] = 1d;
				}
			}
			dimensions[d] = new PreparedDimension(kernel, values, targetMeans, diagonalValues, categoricalStates,
					categoricalStates == null ? 0 : ((CategoricalSamplingDimension)dimension).categoryCount(),
					requireFinite(kernel.targetGrandMean(), "target grand mean", d, -1),
					requireFinite(kernel.targetDiagonalMean(), "target diagonal mean", d, -1));
		}
		return new ExactPointSetData(pointSet.size(), dimensions);
	}

	private static double requireFinite(double value, String quantity, int dimension, int point) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Non-finite " + quantity + " for dimension " + dimension
					+ (point < 0 ? "" : ", point " + point) + ": " + value);
		return value;
	}

	static final class PreparedDimension {
		final DiscrepancyKernel kernel;
		final double[] values;
		final double[] targetMeans;
		final double[] diagonalValues;
		final int[] categoricalStates;
		final int categoricalStateCount;
		final double targetGrandMean;
		final double targetDiagonalMean;

		PreparedDimension(DiscrepancyKernel kernel, double[] values, double[] targetMeans, double[] diagonalValues,
				int[] categoricalStates, int categoricalStateCount, double targetGrandMean, double targetDiagonalMean) {
			this.kernel = kernel;
			this.values = values;
			this.targetMeans = targetMeans;
			this.diagonalValues = diagonalValues;
			this.categoricalStates = categoricalStates;
			this.categoricalStateCount = categoricalStateCount;
			this.targetGrandMean = targetGrandMean;
			this.targetDiagonalMean = targetDiagonalMean;
		}
	}
}
