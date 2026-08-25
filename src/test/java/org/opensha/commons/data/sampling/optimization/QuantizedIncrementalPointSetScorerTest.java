package org.opensha.commons.data.sampling.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.Test;
import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.DimensionSwapGroup;
import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
import org.opensha.commons.data.sampling.PermutedPointSet;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.data.sampling.scoring.PointSetProjection;
import org.opensha.commons.data.sampling.scoring.PointSetScore;
import org.opensha.commons.data.sampling.scoring.PointSetScoringConfig;
import org.opensha.commons.data.sampling.scoring.ProjectionScore;
import org.opensha.commons.data.sampling.scoring.QuantizedPointSetScorer;

public class QuantizedIncrementalPointSetScorerTest {

	private static final double TOL = 2e-11;

	@Test
	public void testRandomSwapDeltasAgainstReferenceScorer() {
		int bins = 9;
		PermutedPointSet points = buildPointSet(48, 42873L);
		PointSetScoringConfig config = PointSetScoringConfig.builder()
				.maxOrder(2).orderWeight(1, 0.7).orderWeight(2, 1.3).build();
		QuantizedPointSetScorer reference = new QuantizedPointSetScorer(bins);
		QuantizedIncrementalPointSetScorer incremental =
				new QuantizedIncrementalPointSetScorer(points, bins, config);

		assertScoresEqual(reference.score(points, config), incremental.getCurrentScore());
		Random random = new Random(918273L);
		for (int i=0; i<750; i++) {
			int group = random.nextInt(points.swapGroupCount());
			int point1 = random.nextInt(points.size());
			int point2;
			do {
				point2 = random.nextInt(points.size());
			} while (point2 == point1);

			double before = incremental.getCurrentNormalizedScore();
			double delta = incremental.evaluateSwap(group, point1, point2);
			assertTrue(incremental.hasPendingSwap());
			// Evaluating a proposal must not alter either the point set or its current score.
			assertEquals(before, reference.score(points, config).getNormalizedScore(), TOL);

			if (i % 4 == 0) {
				incremental.discardSwap();
				assertEquals(before, incremental.getCurrentNormalizedScore(), 0d);
			} else {
				incremental.applySwap();
				assertEquals(before+delta, incremental.getCurrentNormalizedScore(), TOL);
			}
			assertFalse(incremental.hasPendingSwap());
			PointSetScore expected = reference.score(points, config);
			assertEquals(expected.getNormalizedScore(), incremental.getCurrentNormalizedScore(), TOL);
			if (i % 25 == 0)
				assertScoresEqual(expected, incremental.getCurrentScore());
		}
		assertScoresEqual(reference.score(points, config), incremental.recalculate());
	}

	@Test
	public void testGroupedDimensionsRetainTheirJointScore() {
		int bins = 7;
		PermutedPointSet points = buildPointSet(32, 92834L);
		PointSetScoringConfig pairOnly = PointSetScoringConfig.builder()
				.projections(new PointSetProjection(1, 2)).build();
		QuantizedIncrementalPointSetScorer incremental =
				new QuantizedIncrementalPointSetScorer(points, bins, pairOnly);
		double initial = incremental.getCurrentNormalizedScore();
		for (int i=0; i<20; i++) {
			assertEquals(0d, incremental.evaluateSwap(1, i, i+1), 0d);
			incremental.applySwap();
			assertEquals(initial, incremental.getCurrentNormalizedScore(), 0d);
		}
		assertEquals(initial, new QuantizedPointSetScorer(bins).score(points, pairOnly).getNormalizedScore(), TOL);
	}

	@Test
	public void testTransactionAndExternalModificationChecks() {
		PermutedPointSet points = buildPointSet(12, 1234L);
		QuantizedIncrementalPointSetScorer incremental = new QuantizedIncrementalPointSetScorer(points, 5);
		expectIllegalState(incremental::applySwap);
		expectIllegalState(incremental::discardSwap);
		incremental.evaluateSwap(0, 0, 1);
		expectIllegalState(() -> incremental.evaluateSwap(0, 1, 2));
		expectIllegalState(incremental::recalculate);
		incremental.discardSwap();

		points.swap(0, 0, 1);
		expectIllegalState(incremental::getCurrentNormalizedScore);
		expectIllegalState(() -> incremental.evaluateSwap(0, 1, 2));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testHigherOrderConfigRejected() {
		PointSetScoringConfig config = PointSetScoringConfig.builder().maxOrder(3).build();
		new QuantizedIncrementalPointSetScorer(buildPointSet(12, 4321L), 5, config);
	}

	@Test
	public void testIndependentGroupsExcludeInactiveDimensions() {
		PointSet source = new DimensionedPointSet(new ArrayPointSet(new double[][] {
				{ 0.1, 0.2, 0.3 }, { 0.4, 0.5, 0.6 }
		}), List.of(ContinuousSamplingDimension.INSTANCE, InactiveSamplingDimension.INSTANCE,
				ContinuousSamplingDimension.INSTANCE));
		PermutedPointSet points = PermutedPointSet.independentDimensions(source);
		assertEquals(2, points.swapGroupCount());
		assertEquals(0, points.getSwapGroup(0).dimension(0));
		assertEquals(2, points.getSwapGroup(1).dimension(0));
	}

	@Test
	public void testHillClimber() {
		int bins = 8;
		PermutedPointSet points = buildPointSet(48, 83742L);
		QuantizedIncrementalPointSetScorer incremental = new QuantizedIncrementalPointSetScorer(points, bins);
		PointSetOptimizationResult result = PointSetHillClimber.optimize(incremental, 1_000_000L,
				new Random(28374L));
		assertEquals(1_000_000L, result.getIterations());
		assertTrue(result.getAcceptedSwaps() > 0L);
		assertTrue(result.getAcceptedSwaps() <= result.getIterations());
		assertEquals(result.getInitialScore()-result.getFinalScore(), result.getScoreReduction(), TOL);
		assertTrue(result.getFinalScore() < result.getInitialScore());
		assertEquals(String.format(Locale.US,
				"PointSetOptimizationResult[iterations=1000000, accepted=%d, score=%.5f -> %.5f]",
				result.getAcceptedSwaps(), result.getInitialScore(), result.getFinalScore()), result.toString());
		assertFalse(incremental.hasPendingSwap());
		PointSetScore reference = new QuantizedPointSetScorer(bins).score(points);
		assertEquals(reference.getNormalizedScore(), result.getFinalScore(), TOL);
	}

	private static PermutedPointSet buildPointSet(int size, long seed) {
		Random random = new Random(seed);
		double[][] values = new double[size][5];
		for (int p=0; p<size; p++)
			for (int d=0; d<values[p].length; d++)
				values[p][d] = random.nextDouble();
		SamplingDimension[] dimensions = {
				ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.15, 0.35, 0.5),
				ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.6, 0.25, 0.15),
				ContinuousSamplingDimension.INSTANCE
		};
		PointSet decorated = new DimensionedPointSet(new ArrayPointSet(values), List.of(dimensions));
		// Dimension 4 is deliberately fixed; dimensions 1 and 2 move together.
		return new PermutedPointSet(decorated, new DimensionSwapGroup(0),
				new DimensionSwapGroup(1, 2), new DimensionSwapGroup(3));
	}

	private static void assertScoresEqual(PointSetScore expected, PointSetScore actual) {
		assertEquals(expected.getNormalizedScore(), actual.getNormalizedScore(), TOL);
		assertEquals(expected.getOrderMeanScores().keySet(), actual.getOrderMeanScores().keySet());
		for (int order : expected.getOrderMeanScores().keySet())
			assertEquals(expected.getOrderMeanScore(order), actual.getOrderMeanScore(order), TOL);
		assertEquals(expected.getProjectionScores().size(), actual.getProjectionScores().size());
		for (int i=0; i<expected.getProjectionScores().size(); i++) {
			ProjectionScore expectedProjection = expected.getProjectionScores().get(i);
			ProjectionScore actualProjection = actual.getProjectionScores().get(i);
			assertEquals(expectedProjection.getProjection(), actualProjection.getProjection());
			assertEquals(expectedProjection.getRawScore(), actualProjection.getRawScore(), TOL);
			assertEquals(expectedProjection.getExpectedRandomScore(), actualProjection.getExpectedRandomScore(), TOL);
		}
	}

	private static void expectIllegalState(Runnable runnable) {
		try {
			runnable.run();
			fail("Expected IllegalStateException");
		} catch (IllegalStateException expected) {
			// expected
		}
	}
}
