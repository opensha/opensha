package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;
import static org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.*;

import java.util.Arrays;

import org.junit.Test;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.ReceiverDistribution;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.StiffnessAggregation;

public class AggregationMethodTest {

	@Test
	public void greaterSumMedianUsesSumRatherThanMaximum() {
		assertEquals(9, GREATER_SUM_MEDIAN.calculate(new double[] { 2, 3, 4 }), 0);
		assertEquals(-3, GREATER_SUM_MEDIAN.calculate(new double[] { -8, -3, -1 }), 0);
		assertEquals(9, GREATER_SUM_MEDIAN.calculate(new double[] { 2, 3, 4 }, new double[] { 1, 1, 1 }), 0);
	}

	@Test
	public void unitWeightsMatchUnweightedStatistics() {
		for (double[] values : new double[][] { { -3, 0, 2, 8 }, { -9, -3, -1 }, { 2, 3, 4 }, { 5 } }) {
			double[] weights = new double[values.length];
			Arrays.fill(weights, 1);
			for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS)
				assertEquals(method.name(), method.calculate(values.clone()), method.calculate(values.clone(), weights),
						1e-14);
			assertEquals(FLAT_SUM.calculate(values.clone()), FLAT_SUM.calculate(values.clone(), weights), 1e-14);
		}
	}

	@Test
	public void cachedAndDirectStatisticsAgreeForBothWeightConventions() {
		double[] values = { 9, -7, 2, 0, -1 };
		for (double[] weights : new double[][] { null, { .1, .2, .05, .25, .4 } }) {
			var distribution = new ReceiverDistribution(0, values.length, values.clone(), weights,
					weights == null ? values.length : 1, false, 1);
			var cached = new StiffnessAggregation(distribution);
			for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS) {
				double expected = method.calculate(values.clone(), weights);
				assertEquals(method.name(), expected, distribution.calculate(method), 1e-14);
				assertEquals(method.name(), expected, cached.get(method), 1e-14);
			}
		}
		var publicCache = new StiffnessAggregation(values.clone(), values.length);
		for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS)
			assertEquals(method.name(), method.calculate(values.clone()), publicCache.get(method), 1e-14);
	}

	@Test
	public void rescalingWeightsPreservesIntensiveStatisticsAndDiagnosticCount() {
		double[] values = { -4, 0, 2, 9 };
		double[] weights = { .1, .3, .4, .2 };
		double[] scaled = Arrays.stream(weights).map(w -> 7 * w).toArray();
		for (AggregationMethod method : new AggregationMethod[] { MEAN, MEDIAN, MIN, MAX, FRACT_POSITIVE, COUNT,
				GREATER_MEAN_MEDIAN })
			assertEquals(method.name(), method.calculate(values, weights), method.calculate(values, scaled), 1e-14);
		for (AggregationMethod method : new AggregationMethod[] { SUM, FLAT_SUM, NUM_POSITIVE, NUM_NEGATIVE })
			assertEquals(method.name(), 7 * method.calculate(values, weights), method.calculate(values, scaled), 1e-14);
		var reduced = new ReceiverDistribution(0, 123, new double[] { 5 }, new double[] { .75 }, 20, false, 1);
		assertEquals(123, reduced.calculate(COUNT), 0);
		assertEquals(123, new StiffnessAggregation(reduced).get(COUNT), 0);
	}

	@Test
	public void compositeStatisticsApplySourceStageCorrectionExactlyOnce() {
		// Joint weights include receiver weight 4; source weights are .4 and .1.
		var input = new ReceiverDistribution(0, 2, new double[] { 1, 10 }, new double[] { 1.6, .4 }, 2, true, 4);
		assertEquals(1.4, input.calculate(SUM), 1e-14);
		assertEquals(1.4, input.calculate(FLAT_SUM), 1e-14);
		assertEquals(1, input.calculate(MEDIAN), 0);
		assertEquals(1.4, input.calculate(GREATER_SUM_MEDIAN), 1e-14);
		assertEquals(2.8, input.calculate(GREATER_MEAN_MEDIAN), 1e-14);
		assertEquals(1, input.calculate(FRACT_POSITIVE), 0);
		assertEquals(.5, input.calculate(NUM_POSITIVE), 0);
		var cached = new StiffnessAggregation(input);
		for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS)
			assertEquals(method.name(), input.calculate(method), cached.get(method), 1e-14);
	}

	@Test
	public void weightedStatisticsPreserveValueWeightPairing() {
		double[] values = { 9, -4, 2 };
		double[] weights = { .1, .8, .1 };
		double[] originalValues = values.clone();
		double[] originalWeights = weights.clone();
		var distribution = new ReceiverDistribution(0, 3, values, weights, 1, false, 1);
		assertEquals(-4, distribution.calculate(MEDIAN), 0);
		assertEquals(-4, new StiffnessAggregation(distribution).get(MEDIAN), 0);
		assertArrayEquals(originalValues, values, 0);
		assertArrayEquals(originalWeights, weights, 0);
	}

	@Test
	public void weightedValidationAndUnweightedEmptyBehaviorArePreserved() {
		for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS) {
			assertThrows(IllegalStateException.class,
					() -> method.calculate(new double[] { Double.NaN }, new double[] { 1 }));
			for (double weight : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY })
				assertThrows(IllegalArgumentException.class,
						() -> method.calculate(new double[] { 1 }, new double[] { weight }));
			assertThrows(IllegalArgumentException.class,
					() -> method.calculate(new double[] { 1, 2 }, new double[] { 1 }));
		}
		assertEquals(0, SUM.calculate(new double[0]), 0);
		assertEquals(0, COUNT.calculate(new double[0]), 0);
		assertTrue(Double.isNaN(MEAN.calculate(new double[0])));
		assertThrows(IllegalArgumentException.class, () -> SUM.calculate(new double[0], new double[0]));
	}
}
