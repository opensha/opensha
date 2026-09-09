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
import org.opensha.commons.data.sampling.scoring.CenteredDiscrepancy;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyConfig;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore.ProjectionResult;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScorer;
import org.opensha.commons.data.sampling.optimization.PointSetHillClimber.Result;

public class PointSetObjectiveTest {

	private static final double TOL = 2e-11;

	@Test
	public void testGenericSessionEvaluatesAViewAndLeavesPointSetUnchanged() {
		PermutedPointSet points = PermutedPointSet.independentDimensions(new ArrayPointSet(new double[][] {
				{ 0.1, 0.2 }, { 0.8, 0.9 }
		}));
		PointSetObjective objective = pointSet -> pointSet.get(0, 0)*pointSet.get(0, 1)
				+pointSet.get(1, 0)*pointSet.get(1, 1);
		PointSetObjective.SwapSession session = objective.prepare(points);
		double initial = objective.evaluate(points);
		double delta = session.evaluateSwap(0, 0, 1);
		assertEquals(initial, objective.evaluate(points), 0d);
		assertEquals(0.25d-initial, delta, TOL);
		session.discardSwap();
		assertEquals(initial, session.getCurrentValue(), 0d);

		session.evaluateSwap(0, 0, 1);
		session.applySwap();
		assertEquals(0.25d, session.getCurrentValue(), TOL);
		assertEquals(session.getCurrentValue(), objective.evaluate(points), TOL);
	}

	@Test
	public void testScorerObjectivesSelectFastAndFallbackSessions() {
		PermutedPointSet points = buildPointSet(24, 72134L);
		ProjectionDiscrepancyScorer quantized = ProjectionDiscrepancyScorer.quantized(8);
		PointSetObjective pairwise = quantized.objective();
		PointSetObjective.SwapSession fast = pairwise.prepare(points);
		assertTrue(fast instanceof QuantizedProjectionSwapSession);
		assertEquals(quantized.score(points).getNormalizedScore(), fast.getCurrentValue(), TOL);

		ProjectionDiscrepancyConfig higherOrder = ProjectionDiscrepancyConfig.builder().maxOrder(3).build();
		PointSetObjective.SwapSession fallback = quantized.objective(higherOrder).prepare(points);
		assertFalse(fallback instanceof QuantizedProjectionSwapSession);
		assertEquals(quantized.score(points, higherOrder).getNormalizedScore(), fallback.getCurrentValue(), TOL);
	}

	@Test
	public void testExactAndCenteredObjectivesCanBeOptimized() {
		PermutedPointSet exactPoints = PermutedPointSet.independentDimensions(
				new ArrayPointSet(new double[][] { { 0.1, 0.1 }, { 0.2, 0.8 }, { 0.8, 0.2 }, { 0.9, 0.9 } }));
		PointSetObjective exact = ProjectionDiscrepancyScorer.exact().objective(
				ProjectionDiscrepancyConfig.builder().maxOrder(2).build());
		Result exactResult = PointSetHillClimber.optimize(exactPoints, exact, 100L,
				new Random(32874L));
		assertTrue(exactResult.getFinalValue() <= exactResult.getInitialValue());
		assertEquals(exact.evaluate(exactPoints), exactResult.getFinalValue(), TOL);

		PermutedPointSet centeredPoints = PermutedPointSet.independentDimensions(
				new ArrayPointSet(new double[][] { { 0.1, 0.1 }, { 0.2, 0.8 }, { 0.8, 0.2 }, { 0.9, 0.9 } }));
		PointSetObjective centered = CenteredDiscrepancy.objective();
		Result centeredResult = PointSetHillClimber.optimize(centeredPoints, centered, 100L,
				new Random(32874L));
		assertTrue(centeredResult.getFinalValue() <= centeredResult.getInitialValue());
		assertEquals(centered.evaluate(centeredPoints), centeredResult.getFinalValue(), TOL);
	}

	@Test
	public void testRandomSwapDeltasAgainstReferenceScorer() {
		int bins = 9;
		PermutedPointSet points = buildPointSet(48, 42873L);
		ProjectionDiscrepancyConfig config = ProjectionDiscrepancyConfig.builder()
				.maxOrder(2).orderWeight(1, 0.7).orderWeight(2, 1.3).build();
		ProjectionDiscrepancyScorer reference = ProjectionDiscrepancyScorer.quantized(bins);
		QuantizedProjectionSwapSession incremental =
				new QuantizedProjectionSwapSession(points, bins, config);

		assertScoresEqual(reference.score(points, config), incremental.getCurrentScore());
		Random random = new Random(918273L);
		for (int i=0; i<750; i++) {
			int group = random.nextInt(points.swapGroupCount());
			int point1 = random.nextInt(points.size());
			int point2;
			do {
				point2 = random.nextInt(points.size());
			} while (point2 == point1);

			double before = incremental.getCurrentValue();
			double delta = incremental.evaluateSwap(group, point1, point2);
			assertTrue(incremental.hasPendingSwap());
			// Evaluating a proposal must not alter either the point set or its current score.
			assertEquals(before, reference.score(points, config).getNormalizedScore(), TOL);

			if (i % 4 == 0) {
				incremental.discardSwap();
				assertEquals(before, incremental.getCurrentValue(), 0d);
			} else {
				incremental.applySwap();
				assertEquals(before+delta, incremental.getCurrentValue(), TOL);
			}
			assertFalse(incremental.hasPendingSwap());
			ProjectionDiscrepancyScore expected = reference.score(points, config);
			assertEquals(expected.getNormalizedScore(), incremental.getCurrentValue(), TOL);
			if (i % 25 == 0)
				assertScoresEqual(expected, incremental.getCurrentScore());
		}
		assertEquals(reference.score(points, config).getNormalizedScore(), incremental.recalculate(), TOL);
	}

	@Test
	public void testGroupedDimensionsRetainTheirJointScore() {
		int bins = 7;
		PermutedPointSet points = buildPointSet(32, 92834L);
		ProjectionDiscrepancyConfig pairOnly = ProjectionDiscrepancyConfig.builder()
				.projections(new PointSetProjection(1, 2)).build();
		QuantizedProjectionSwapSession incremental =
				new QuantizedProjectionSwapSession(points, bins, pairOnly);
		double initial = incremental.getCurrentValue();
		for (int i=0; i<20; i++) {
			assertEquals(0d, incremental.evaluateSwap(1, i, i+1), 0d);
			incremental.applySwap();
			assertEquals(initial, incremental.getCurrentValue(), 0d);
		}
		assertEquals(initial, ProjectionDiscrepancyScorer.quantized(bins).score(points, pairOnly).getNormalizedScore(), TOL);
	}

	@Test
	public void testTransactionAndExternalModificationChecks() {
		PermutedPointSet points = buildPointSet(12, 1234L);
		QuantizedProjectionSwapSession incremental = new QuantizedProjectionSwapSession(points, 5);
		expectIllegalState(incremental::applySwap);
		expectIllegalState(incremental::discardSwap);
		incremental.evaluateSwap(0, 0, 1);
		expectIllegalState(() -> incremental.evaluateSwap(0, 1, 2));
		expectIllegalState(incremental::recalculate);
		incremental.discardSwap();

		points.swap(0, 0, 1);
		expectIllegalState(incremental::getCurrentValue);
		expectIllegalState(() -> incremental.evaluateSwap(0, 1, 2));
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
		QuantizedProjectionSwapSession incremental = new QuantizedProjectionSwapSession(points, bins);
		Result result = PointSetHillClimber.optimize(incremental, 1_000_000L,
				new Random(28374L));
		assertEquals(1_000_000L, result.getIterations());
		assertTrue(result.getAcceptedSwaps() > 0L);
		assertTrue(result.getAcceptedSwaps() <= result.getIterations());
		assertEquals(result.getInitialValue()-result.getFinalValue(), result.getValueReduction(), TOL);
		assertTrue(result.getFinalValue() < result.getInitialValue());
		assertEquals(String.format(Locale.US,
				"PointSetHillClimber.Result[iterations=1000000, accepted=%d, value=%.5f -> %.5f]",
				result.getAcceptedSwaps(), result.getInitialValue(), result.getFinalValue()), result.toString());
		assertFalse(incremental.hasPendingSwap());
		ProjectionDiscrepancyScore reference = ProjectionDiscrepancyScorer.quantized(bins).score(points);
		assertEquals(reference.getNormalizedScore(), result.getFinalValue(), TOL);
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

	private static void assertScoresEqual(ProjectionDiscrepancyScore expected, ProjectionDiscrepancyScore actual) {
		assertEquals(expected.getNormalizedScore(), actual.getNormalizedScore(), TOL);
		assertEquals(expected.getOrderMeanScores().keySet(), actual.getOrderMeanScores().keySet());
		for (int order : expected.getOrderMeanScores().keySet())
			assertEquals(expected.getOrderMeanScore(order), actual.getOrderMeanScore(order), TOL);
		assertEquals(expected.getProjectionResults().size(), actual.getProjectionResults().size());
		for (int i=0; i<expected.getProjectionResults().size(); i++) {
			ProjectionResult expectedProjection = expected.getProjectionResults().get(i);
			ProjectionResult actualProjection = actual.getProjectionResults().get(i);
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
