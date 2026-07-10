package org.opensha.commons.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class WeightedContinuousDistributionTest {
	
	private static final double TOL = 1e-10;
	
	private static WeightedContinuousDistribution twoUniforms() {
		WeightedList<ContinuousDistribution> dists = new WeightedList<>(List.of(
				new WeightedValue<>(UniformContinuousDistribution.of(0d, 1d), 0.2d),
				new WeightedValue<>(UniformContinuousDistribution.of(1d, 3d), 0.6d)));
		return new WeightedContinuousDistribution(dists);
	}
	
	@Test
	public void testMixtureProperties() {
		WeightedContinuousDistribution dist = twoUniforms();
		
		assertEquals(0d, dist.getSupportLowerBound(), TOL);
		assertEquals(3d, dist.getSupportUpperBound(), TOL);
		assertEquals(0.25, dist.density(0.5), TOL);
		assertEquals(0.375, dist.density(2d), TOL);
		
		assertEquals(0d, dist.cumulativeProbability(0d), TOL);
		assertEquals(0.125, dist.cumulativeProbability(0.5), TOL);
		assertEquals(0.25, dist.cumulativeProbability(1d), TOL);
		assertEquals(0.625, dist.cumulativeProbability(2d), TOL);
		assertEquals(1d, dist.cumulativeProbability(3d), TOL);
		
		assertEquals(0.5, dist.inverseCumulativeProbability(0.125), TOL);
		assertEquals(1d, dist.inverseCumulativeProbability(0.25), TOL);
		assertEquals(2d, dist.inverseCumulativeProbability(0.625), TOL);
		
		assertEquals(1.625, dist.getMean(), TOL);
		assertEquals(0.6927083333333335, dist.getVariance(), TOL);
	}
	
	@Test
	public void testNormalizesWeights() {
		WeightedContinuousDistribution dist = twoUniforms();
		assertEquals(0.25, dist.getDistributions().getWeight(0), TOL);
		assertEquals(0.75, dist.getDistributions().getWeight(1), TOL);
	}
	
	@Test
	public void testIgnoresZeroWeightDistributions() {
		WeightedList<ContinuousDistribution> dists = new WeightedList<>(List.of(
				new WeightedValue<>(UniformContinuousDistribution.of(0d, 1d), 0d),
				new WeightedValue<>(UniformContinuousDistribution.of(1d, 2d), 1d)));
		WeightedContinuousDistribution dist = new WeightedContinuousDistribution(dists);
		
		assertEquals(1, dist.getDistributions().size());
		assertEquals(1d, dist.getSupportLowerBound(), TOL);
		assertEquals(2d, dist.getSupportUpperBound(), TOL);
		assertEquals(1.5, dist.getMean(), TOL);
	}
	
	@Test
	public void testSampler() {
		WeightedContinuousDistribution dist = twoUniforms();
		ContinuousDistribution.Sampler sampler = dist.createSampler(RandomSource.XO_RO_SHI_RO_128_PP.create(1L));
		for (int i=0; i<100; i++) {
			double sample = sampler.sample();
			assertTrue(sample >= dist.getSupportLowerBound());
			assertTrue(sample <= dist.getSupportUpperBound());
		}
	}
	
	@Test
	public void testJsonRoundTrip() throws IOException {
		WeightedContinuousDistribution dist = twoUniforms();
		StringWriter stringWriter = new StringWriter();
		ContinuousDistributionTypeAdapter.get().write(new JsonWriter(stringWriter), dist);
		
		ContinuousDistribution deser = ContinuousDistributionTypeAdapter.get().read(
				new JsonReader(new StringReader(stringWriter.toString())));
		
		assertTrue(deser instanceof WeightedContinuousDistribution);
		assertEquals(dist.getSupportLowerBound(), deser.getSupportLowerBound(), TOL);
		assertEquals(dist.getSupportUpperBound(), deser.getSupportUpperBound(), TOL);
		assertEquals(dist.getMean(), deser.getMean(), TOL);
		assertEquals(dist.getVariance(), deser.getVariance(), TOL);
		assertEquals(dist.cumulativeProbability(2d), deser.cumulativeProbability(2d), TOL);
		assertEquals(dist.inverseCumulativeProbability(0.625), deser.inverseCumulativeProbability(0.625), TOL);
	}

}
