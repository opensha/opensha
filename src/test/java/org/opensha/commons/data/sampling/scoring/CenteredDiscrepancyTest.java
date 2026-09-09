package org.opensha.commons.data.sampling.scoring;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.PointSet;

public class CenteredDiscrepancyTest {

	private static final double TOL = 1e-14;

	@Test
	public void testKnownOneDimensionalScores() {
		assertEquals(1d/12d, CenteredDiscrepancy.score(
				new ArrayPointSet(new double[][] {{0.5d}})), TOL);
		assertEquals(1d/48d, CenteredDiscrepancy.score(
				new ArrayPointSet(new double[][] {{0.25d}, {0.75d}})), TOL);
	}

	@Test
	public void testKnownTwoDimensionalScore() {
		assertEquals(25d/144d, CenteredDiscrepancy.score(
				new ArrayPointSet(new double[][] {{0.5d, 0.5d}})), TOL);
	}

	@Test
	public void testProjectionMatchesExplicitSubset() {
		PointSet points = new ArrayPointSet(new double[][] {
			{0.1d, 0.8d, 0.3d}, {0.6d, 0.2d, 0.9d}, {0.4d, 0.5d, 0.7d}
		});
		PointSet subset = new ArrayPointSet(new double[][] {
			{0.1d, 0.3d}, {0.6d, 0.9d}, {0.4d, 0.7d}
		});
		assertEquals(CenteredDiscrepancy.score(subset),
				CenteredDiscrepancy.score(points, new PointSetProjection(0, 2)), TOL);
	}

	@Test
	public void testPointDimensionAndReflectionInvariance() {
		PointSet first = new ArrayPointSet(new double[][] {
			{0.1d, 0.7d}, {0.4d, 0.2d}, {0.8d, 0.9d}
		});
		PointSet rearranged = new ArrayPointSet(new double[][] {
			{0.1d, 0.8d}, {0.3d, 0.1d}, {0.8d, 0.4d}
		});
		assertEquals(CenteredDiscrepancy.score(first), CenteredDiscrepancy.score(rearranged), TOL);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCategoricalDimensionRejected() {
		PointSet points = new DimensionedPointSet(new ArrayPointSet(new double[][] {
			{0.1d, 0.2d}, {0.7d, 0.8d}
		}), java.util.List.of(ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.5d, 0.5d)));
		CenteredDiscrepancy.score(points);
	}

	@Test
	public void testProjectionCanOmitCategoricalDimension() {
		PointSet points = new DimensionedPointSet(new ArrayPointSet(new double[][] {
			{0.25d, 0.2d}, {0.75d, 0.8d}
		}), java.util.List.of(ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.5d, 0.5d)));
		assertEquals(1d/48d,
				CenteredDiscrepancy.score(points, new PointSetProjection(0)), TOL);
	}
}
