package org.opensha.commons.data.sampling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

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
