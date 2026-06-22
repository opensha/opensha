package org.opensha.commons.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class AffineTransformedContinuousDistributionTest {
	
	private static final double TOL = 1e-10;
	
	@Test
	public void testPositiveScaleAndOffset() {
		ContinuousDistribution source = UniformContinuousDistribution.of(1d, 3d);
		AffineTransformedContinuousDistribution dist =
				new AffineTransformedContinuousDistribution(source, 2d, -1d);
		
		assertEquals(1d, dist.getSupportLowerBound(), TOL);
		assertEquals(5d, dist.getSupportUpperBound(), TOL);
		assertEquals(0.25, dist.density(3d), TOL);
		
		assertEquals(0d, dist.cumulativeProbability(1d), TOL);
		assertEquals(0.5, dist.cumulativeProbability(3d), TOL);
		assertEquals(1d, dist.cumulativeProbability(5d), TOL);
		
		assertEquals(1d, dist.inverseCumulativeProbability(0d), TOL);
		assertEquals(3d, dist.inverseCumulativeProbability(0.5), TOL);
		assertEquals(5d, dist.inverseCumulativeProbability(1d), TOL);
		
		assertEquals(3d, dist.getMean(), TOL);
		assertEquals(4d/3d, dist.getVariance(), TOL);
	}
	
	@Test
	public void testNegativeScaleAndOffset() {
		ContinuousDistribution source = UniformContinuousDistribution.of(1d, 3d);
		AffineTransformedContinuousDistribution dist =
				new AffineTransformedContinuousDistribution(source, -2d, 10d);
		
		assertEquals(4d, dist.getSupportLowerBound(), TOL);
		assertEquals(8d, dist.getSupportUpperBound(), TOL);
		assertEquals(0.25, dist.density(6d), TOL);
		
		assertEquals(0d, dist.cumulativeProbability(4d), TOL);
		assertEquals(0.25, dist.cumulativeProbability(5d), TOL);
		assertEquals(0.5, dist.cumulativeProbability(6d), TOL);
		assertEquals(0.75, dist.cumulativeProbability(7d), TOL);
		assertEquals(1d, dist.cumulativeProbability(8d), TOL);
		
		assertEquals(4d, dist.inverseCumulativeProbability(0d), TOL);
		assertEquals(5d, dist.inverseCumulativeProbability(0.25), TOL);
		assertEquals(6d, dist.inverseCumulativeProbability(0.5), TOL);
		assertEquals(7d, dist.inverseCumulativeProbability(0.75), TOL);
		assertEquals(8d, dist.inverseCumulativeProbability(1d), TOL);
		
		assertEquals(6d, dist.getMean(), TOL);
		assertEquals(4d/3d, dist.getVariance(), TOL);
	}
	
	@Test
	public void testShiftFactoryAndSampler() {
		AffineTransformedContinuousDistribution dist =
				AffineTransformedContinuousDistribution.shifted(UniformContinuousDistribution.of(0d, 1d), 3d);
		
		assertEquals(3d, dist.getSupportLowerBound(), TOL);
		assertEquals(4d, dist.getSupportUpperBound(), TOL);
		
		ContinuousDistribution.Sampler sampler = dist.createSampler(RandomSource.XO_RO_SHI_RO_128_PP.create(1L));
		for (int i=0; i<100; i++) {
			double sample = sampler.sample();
			assertTrue(sample >= dist.getSupportLowerBound());
			assertTrue(sample <= dist.getSupportUpperBound());
		}
	}
	
	@Test
	public void testJsonRoundTrip() throws IOException {
		AffineTransformedContinuousDistribution dist =
				new AffineTransformedContinuousDistribution(UniformContinuousDistribution.of(1d, 3d), -2d, 10d);
		StringWriter stringWriter = new StringWriter();
		ContinuousDistributionTypeAdapter.get().write(new JsonWriter(stringWriter), dist);
		
		ContinuousDistribution deser = ContinuousDistributionTypeAdapter.get().read(
				new JsonReader(new StringReader(stringWriter.toString())));
		
		assertTrue(deser instanceof AffineTransformedContinuousDistribution);
		assertEquals(dist.getSupportLowerBound(), deser.getSupportLowerBound(), TOL);
		assertEquals(dist.getSupportUpperBound(), deser.getSupportUpperBound(), TOL);
		assertEquals(dist.getMean(), deser.getMean(), TOL);
		assertEquals(dist.getVariance(), deser.getVariance(), TOL);
		assertEquals(dist.cumulativeProbability(5d), deser.cumulativeProbability(5d), TOL);
		assertEquals(dist.inverseCumulativeProbability(0.75), deser.inverseCumulativeProbability(0.75), TOL);
	}

}
