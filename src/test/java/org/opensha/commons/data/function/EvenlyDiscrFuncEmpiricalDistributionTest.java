package org.opensha.commons.data.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscrFuncEmpiricalDistribution.DiscretizationType;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class EvenlyDiscrFuncEmpiricalDistributionTest {

	private static final double TOL = 1e-12;

	private static EvenlyDiscretizedFunc twoPointMassFunc() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d, 2, 1d);
		func.set(0, 1d);
		func.set(1, 3d);
		return func;
	}

	@Test
	public void testPointMass() {
		ContinuousDistribution dist = new EvenlyDiscrFuncEmpiricalDistribution(
				twoPointMassFunc(), DiscretizationType.POINT_MASS);

		assertEquals(0d, dist.getSupportLowerBound(), TOL);
		assertEquals(1d, dist.getSupportUpperBound(), TOL);
		assertEquals(0.25, dist.density(0d), TOL);
		assertEquals(0.75, dist.density(1d), TOL);
		assertEquals(0d, dist.density(0.5), TOL);

		assertEquals(0d, dist.cumulativeProbability(-0.1), TOL);
		assertEquals(0.25, dist.cumulativeProbability(0d), TOL);
		assertEquals(0.25, dist.cumulativeProbability(0.5), TOL);
		assertEquals(1d, dist.cumulativeProbability(1d), TOL);

		assertEquals(0d, dist.inverseCumulativeProbability(0d), TOL);
		assertEquals(0d, dist.inverseCumulativeProbability(0.25), TOL);
		assertEquals(1d, dist.inverseCumulativeProbability(0.2501), TOL);
		assertEquals(1d, dist.inverseCumulativeProbability(1d), TOL);

		assertEquals(0.75, dist.getMean(), TOL);
		assertEquals(0.1875, dist.getVariance(), TOL);
	}

	@Test
	public void testFlatWithinBin() {
		ContinuousDistribution dist = new EvenlyDiscrFuncEmpiricalDistribution(
				twoPointMassFunc(), DiscretizationType.FLAT_WITHIN_BIN);

		assertEquals(-0.5, dist.getSupportLowerBound(), TOL);
		assertEquals(1.5, dist.getSupportUpperBound(), TOL);
		assertEquals(0.25, dist.density(0d), TOL);
		assertEquals(0.75, dist.density(1d), TOL);

		assertEquals(0d, dist.cumulativeProbability(-0.5), TOL);
		assertEquals(0.125, dist.cumulativeProbability(0d), TOL);
		assertEquals(0.25, dist.cumulativeProbability(0.5), TOL);
		assertEquals(0.625, dist.cumulativeProbability(1d), TOL);
		assertEquals(1d, dist.cumulativeProbability(1.5), TOL);

		assertEquals(0d, dist.inverseCumulativeProbability(0.125), TOL);
		assertEquals(0.5, dist.inverseCumulativeProbability(0.25), TOL);
		assertEquals(1d, dist.inverseCumulativeProbability(0.625), TOL);

		assertEquals(0.75, dist.getMean(), TOL);
		assertEquals(0.1875 + 1d/12d, dist.getVariance(), TOL);
	}

	@Test
	public void testSnapToCenterSampler() {
		ContinuousDistribution dist = new EvenlyDiscrFuncEmpiricalDistribution(
				twoPointMassFunc(), DiscretizationType.SNAP_TO_CENTER);

		assertEquals(-0.5, dist.getSupportLowerBound(), TOL);
		assertEquals(1.5, dist.getSupportUpperBound(), TOL);
		assertEquals(0d, dist.inverseCumulativeProbability(0.125), TOL);
		assertEquals(0.5, dist.inverseCumulativeProbability(0.25), TOL);

		ContinuousDistribution.Sampler sampler = dist.createSampler(RandomSource.XO_RO_SHI_RO_128_PP.create(1L));
		for (int i=0; i<100; i++) {
			double sample = sampler.sample();
			assertTrue(sample == 0d || sample == 1d);
		}
	}

	@Test
	public void testInterpolate() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d, 3, 1d);
		func.set(0, 0d);
		func.set(1, 1d);
		func.set(2, 0d);
		ContinuousDistribution dist = new EvenlyDiscrFuncEmpiricalDistribution(func, DiscretizationType.INTERPOLATE);

		assertEquals(0d, dist.getSupportLowerBound(), TOL);
		assertEquals(2d, dist.getSupportUpperBound(), TOL);
		assertEquals(0d, dist.density(0d), TOL);
		assertEquals(0.5, dist.density(0.5), TOL);
		assertEquals(1d, dist.density(1d), TOL);
		assertEquals(0.5, dist.density(1.5), TOL);
		assertEquals(0d, dist.density(2d), TOL);

		assertEquals(0.125, dist.cumulativeProbability(0.5), TOL);
		assertEquals(0.5, dist.cumulativeProbability(1d), TOL);
		assertEquals(0.875, dist.cumulativeProbability(1.5), TOL);

		assertEquals(0.5, dist.inverseCumulativeProbability(0.125), TOL);
		assertEquals(1d, dist.inverseCumulativeProbability(0.5), TOL);
		assertEquals(1.5, dist.inverseCumulativeProbability(0.875), TOL);

		assertEquals(1d, dist.getMean(), TOL);
		assertEquals(1d/6d, dist.getVariance(), TOL);
	}
	@Test
	public void testJsonRoundTrip() throws IOException {
		EvenlyDiscrFuncEmpiricalDistribution dist = new EvenlyDiscrFuncEmpiricalDistribution(
				twoPointMassFunc(), DiscretizationType.FLAT_WITHIN_BIN);
		StringWriter stringWriter = new StringWriter();
		ContinuousDistributionTypeAdapter.get().write(new JsonWriter(stringWriter), dist);

		ContinuousDistribution deser = ContinuousDistributionTypeAdapter.get().read(
				new JsonReader(new StringReader(stringWriter.toString())));

		assertEquals(dist.getSupportLowerBound(), deser.getSupportLowerBound(), TOL);
		assertEquals(dist.getSupportUpperBound(), deser.getSupportUpperBound(), TOL);
		assertEquals(dist.getMean(), deser.getMean(), TOL);
		assertEquals(dist.getVariance(), deser.getVariance(), TOL);
		assertEquals(dist.cumulativeProbability(0d), deser.cumulativeProbability(0d), TOL);
		assertEquals(dist.inverseCumulativeProbability(0.625), deser.inverseCumulativeProbability(0.625), TOL);
	}

}
