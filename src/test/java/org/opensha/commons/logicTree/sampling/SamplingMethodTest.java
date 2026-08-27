package org.opensha.commons.logicTree.sampling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;

public class SamplingMethodTest {

	@Test
	public void testGenerateSeedAndRandomOverloadsMatch() {
		for (SamplingMethod method : SamplingMethod.values()) {
			if (method == SamplingMethod.EXTERNAL)
				continue;
			long seed = 87342L;
			assertPointSetsEqual(method.generate(32, 4, seed),
					method.generate(32, 4, new Random(seed)));
		}
	}

	@Test
	public void testPrepareDecoratesSameGeneratedCoordinates() {
		List<SamplingDimension> dimensions = List.of(
				CategoricalSamplingDimension.forWeights(1d, 2d),
				ContinuousSamplingDimension.INSTANCE,
				InactiveSamplingDimension.INSTANCE);
		long seed = 98234L;
		PointSet generated = SamplingMethod.OWEN_SCRAMBLED_SOBOL.generate(32, dimensions.size(), seed);
		PointSet prepared = SamplingMethod.OWEN_SCRAMBLED_SOBOL.prepare(32, dimensions, seed);
		assertPointSetsEqual(generated, prepared);
		for (int d=0; d<dimensions.size(); d++)
			assertSame(dimensions.get(d), prepared.getDimension(d));
	}

	@Test
	public void testPrepareSeedAndRandomOverloadsMatch() {
		List<SamplingDimension> dimensions = List.of(
				ContinuousSamplingDimension.INSTANCE, ContinuousSamplingDimension.INSTANCE);
		long seed = 192834L;
		PointSet seeded = SamplingMethod.PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE.prepare(16, dimensions, seed);
		PointSet randomized = SamplingMethod.PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE.prepare(
				16, dimensions, new Random(seed));
		assertPointSetsEqual(seeded, randomized);
	}

	@Test
	public void testOnlyOwenSobolRandomizesDimensionAssignments() {
		for (SamplingMethod method : SamplingMethod.values())
			assertEquals(method == SamplingMethod.OWEN_SCRAMBLED_SOBOL,
					method.randomizesDimensionAssignments());
	}

	@Test(expected=IllegalStateException.class)
	public void testExternalCannotGenerate() {
		SamplingMethod.EXTERNAL.generate(2, 2, 1L);
	}

	private static void assertPointSetsEqual(PointSet first, PointSet second) {
		assertEquals(first.size(), second.size());
		assertEquals(first.dimensions(), second.dimensions());
		for (int p=0; p<first.size(); p++)
			assertTrue(Arrays.equals(first.getPoint(p), second.getPoint(p)));
	}
}
