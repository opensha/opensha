package org.opensha.commons.data.sampling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;

public class PointSetTest {

	private static final double TOL = 1e-15;

	@Test
	public void testArrayPointSetAndDefensiveCopy() {
		double[][] values = { { 0d, 0.25 }, { 0.5, Math.nextDown(1d) } };
		ArrayPointSet pointSet = new ArrayPointSet(values);
		assertEquals(2, pointSet.size());
		assertEquals(2, pointSet.dimensions());
		assertEquals(0.25, pointSet.get(0, 1), 0d);
		values[0][1] = 0.75;
		assertEquals(0.25, pointSet.get(0, 1), 0d);
		assertSame(ContinuousSamplingDimension.INSTANCE, pointSet.getDimension(0));
	}

	@Test
	public void testPointAndDimensionCopies() {
		PointSet points = new ArrayPointSet(new double[][] { { 0.1, 0.2 }, { 0.3, 0.4 } });
		double[] point = points.getPoint(1);
		double[] dimension = points.getDimensionValues(0);
		assertEquals(0.4, point[1], 0d);
		assertEquals(0.3, dimension[1], 0d);
		point[1] = 0.9;
		dimension[1] = 0.9;
		assertEquals(0.4, points.get(1, 1), 0d);
		assertEquals(0.3, points.get(1, 0), 0d);
	}

	@Test
	public void testDimensionSubsetDelegatesCoordinatesAndTypes() {
		PointSet source = new DimensionedPointSet(
				new ArrayPointSet(new double[][] { { 0.1, 0.2, 0.3 }, { 0.4, 0.5, 0.6 } }),
				List.of(ContinuousSamplingDimension.INSTANCE, InactiveSamplingDimension.INSTANCE,
						CategoricalSamplingDimension.forWeights(1d, 2d)));
		DimensionSubsetPointSet subset = DimensionSubsetPointSet.range(source, 1, 3);
		assertEquals(2, subset.dimensions());
		assertEquals(0.5, subset.get(1, 0), 0d);
		assertSame(InactiveSamplingDimension.INSTANCE, subset.getDimension(0));
		assertSame(source.getDimension(2), subset.getDimension(1));
		assertEquals(2, subset.getSourceDimensionIndex(1));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testEmptyPointSetRejected() {
		new ArrayPointSet(new double[0][]);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testZeroDimensionsRejected() {
		new ArrayPointSet(new double[][] { {} });
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRaggedPointSetRejected() {
		new ArrayPointSet(new double[][] { { 0.2 }, { 0.3, 0.4 } });
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNonFiniteCoordinateRejected() {
		new ArrayPointSet(new double[][] { { Double.NaN } });
	}

	@Test(expected=IllegalArgumentException.class)
	public void testOneCoordinateRejected() {
		new ArrayPointSet(new double[][] { { 1d } });
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNegativeCoordinateRejected() {
		new ArrayPointSet(new double[][] { { -0.1 } });
	}

	@Test
	public void testCategoricalWeightsAndBoundaries() {
		CategoricalSamplingDimension dimension = CategoricalSamplingDimension.forWeights(2d, 3d, 5d);
		assertEquals(3, dimension.categoryCount());
		assertEquals(0.2, dimension.categoryProbability(0), TOL);
		assertEquals(0.3, dimension.categoryProbability(1), TOL);
		assertEquals(0.5, dimension.categoryProbability(2), TOL);
		assertEquals(0d, dimension.categoryLowerBound(0), 0d);
		assertEquals(0.2, dimension.categoryUpperBound(0), TOL);
		assertEquals(0.2, dimension.categoryLowerBound(1), TOL);
		assertEquals(1d, dimension.categoryUpperBound(2), 0d);

		assertEquals(0, dimension.categoryIndex(0d));
		assertEquals(0, dimension.categoryIndex(Math.nextDown(0.2)));
		assertEquals(1, dimension.categoryIndex(0.2));
		assertEquals(1, dimension.categoryIndex(Math.nextDown(0.5)));
		assertEquals(2, dimension.categoryIndex(0.5));
		assertEquals(2, dimension.categoryIndex(Math.nextDown(1d)));

		CategoricalSamplingDimension fromBounds =
				CategoricalSamplingDimension.forUpperBounds(0.2, 0.5, 1d);
		for (int i=0; i<3; i++)
			assertEquals(dimension.categoryProbability(i), fromBounds.categoryProbability(i), TOL);
	}

	@Test
	public void testContinuousKernelDiscretization() {
		DiscretizedDiscrepancyKernel kernel =
				ContinuousSamplingDimension.INSTANCE.getDiscretizedKernel(2);
		assertEquals(2, kernel.stateCount());
		assertEquals(0, kernel.state(0d));
		assertEquals(0, kernel.state(Math.nextDown(0.5)));
		assertEquals(1, kernel.state(0.5));
		assertEquals(1, kernel.state(Math.nextDown(1d)));
		assertEquals(0.25, kernel.representativeValue(0), 0d);
		assertEquals(0.75, kernel.representativeValue(1), 0d);
		assertEquals(0.75, kernel.value(0, 0), TOL);
		assertEquals(0.25, kernel.value(0, 1), TOL);
		assertEquals(0.5, kernel.targetMean(0), TOL);
		assertEquals(0.25, kernel.targetMean(1), TOL);
		assertEquals(0.375, kernel.targetGrandMean(), TOL);
		assertEquals(0.5, kernel.targetDiagonalMean(), TOL);
	}

	@Test
	public void testCategoricalDiscretizationUsesExactCategories() {
		CategoricalSamplingDimension dimension = CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5);
		DiscretizedDiscrepancyKernel kernel = dimension.getDiscretizedKernel(100);
		assertEquals(3, kernel.stateCount());
		assertEquals(0, kernel.state(0.1));
		assertEquals(1, kernel.state(0.2));
		assertEquals(2, kernel.state(0.7));
		assertEquals(0.3, kernel.targetMean(1), TOL);
		assertEquals(0.38, kernel.targetGrandMean(), TOL);
		assertEquals(1d, kernel.targetDiagonalMean(), 0d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNonPositiveCategoricalWeightRejected() {
		CategoricalSamplingDimension.forWeights(1d, 0d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNonIncreasingCategoryBoundRejected() {
		CategoricalSamplingDimension.forUpperBounds(0.5, 0.5, 1d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCategoryBoundsMustEndAtOne() {
		CategoricalSamplingDimension.forUpperBounds(0.2, 0.5, 0.9);
	}

	@Test
	public void testDimensionDecoratorDoesNotCopyCoordinates() {
		MutablePointSet delegate = new MutablePointSet(new double[][] { { 0.1, 0.6 } });
		CategoricalSamplingDimension categorical = CategoricalSamplingDimension.forWeights(1d, 1d);
		List<SamplingDimension> dimensions = new ArrayList<>();
		dimensions.add(ContinuousSamplingDimension.INSTANCE);
		dimensions.add(categorical);
		DimensionedPointSet decorated = new DimensionedPointSet(delegate, dimensions);

		assertSame(delegate, decorated.getDelegate());
		assertSame(categorical, decorated.getDimension(1));
		assertEquals(0.6, decorated.get(0, 1), 0d);
		delegate.values[0][1] = 0.8;
		assertEquals(0.8, decorated.get(0, 1), 0d);

		// Metadata is copied even though coordinates are not.
		dimensions.set(1, ContinuousSamplingDimension.INSTANCE);
		assertSame(categorical, decorated.getDimension(1));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testDecoratorDimensionCountChecked() {
		new DimensionedPointSet(new ArrayPointSet(new double[][] { { 0.1, 0.2 } }),
				List.of(ContinuousSamplingDimension.INSTANCE));
	}

	@Test
	public void testGroupedPermutationOverlay() {
		double[][] values = {
				{ 0.1, 0.2, 0.3, 0.4 },
				{ 0.5, 0.6, 0.7, 0.8 },
				{ 0.9, 0.15, 0.25, 0.35 }
		};
		ArrayPointSet source = new ArrayPointSet(values);
		PermutedPointSet permuted = new PermutedPointSet(source,
				new DimensionSwapGroup(1, 2), new DimensionSwapGroup(3));
		assertEquals(2, permuted.swapGroupCount());
		assertEquals(0, permuted.getSourcePointIndex(0, 0));

		permuted.swap(0, 0, 2);
		assertEquals(1L, permuted.modificationCount());
		assertEquals(values[2][1], permuted.get(0, 1), 0d);
		assertEquals(values[2][2], permuted.get(0, 2), 0d);
		assertEquals(values[0][1], permuted.get(2, 1), 0d);
		assertEquals(values[0][2], permuted.get(2, 2), 0d);
		// Dimension 0 is unlisted and fixed; dimension 3 belongs to a different group.
		assertEquals(values[0][0], permuted.get(0, 0), 0d);
		assertEquals(values[0][3], permuted.get(0, 3), 0d);

		permuted.swap(1, 0, 1);
		assertEquals(2L, permuted.modificationCount());
		assertEquals(values[1][3], permuted.get(0, 3), 0d);
		assertEquals(values[2][1], permuted.get(0, 1), 0d);
		// Source coordinates are unchanged.
		assertEquals(values[0][1], source.get(0, 1), 0d);
	}

	@Test
	public void testIndependentDimensionPermutationFactory() {
		ArrayPointSet source = new ArrayPointSet(new double[][] { { 0.1, 0.2 }, { 0.7, 0.8 } });
		PermutedPointSet permuted = PermutedPointSet.independentDimensions(source);
		assertEquals(2, permuted.swapGroupCount());
		permuted.swap(0, 0, 1);
		assertEquals(0.7, permuted.get(0, 0), 0d);
		assertEquals(0.2, permuted.get(0, 1), 0d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testOverlappingSwapGroupsRejected() {
		new PermutedPointSet(new ArrayPointSet(new double[][] { { 0.1, 0.2, 0.3 } }),
				new DimensionSwapGroup(0, 1), new DimensionSwapGroup(1, 2));
	}

	private static final class MutablePointSet implements PointSet {
		private final double[][] values;

		MutablePointSet(double[][] values) {
			this.values = values;
		}

		@Override
		public int size() {
			return values.length;
		}

		@Override
		public int dimensions() {
			return values[0].length;
		}

		@Override
		public double get(int pointIndex, int dimensionIndex) {
			return values[pointIndex][dimensionIndex];
		}
	}
}
