package org.opensha.commons.data.sampling.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.random.SobolSequenceGenerator;
import org.junit.Test;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.scoring.ExactPointSetScorer;

public class PointSetGeneratorTest {

	private static final double TOL = 0d;

	@Test
	public void testGeneratorNames() {
		assertEquals("Monte Carlo", new MonteCarloPointSetGenerator(new Random(1L)).toString());
		assertEquals("Latin Hypercube", new LatinHypercubePointSetGenerator(new Random(1L)).toString());
		assertEquals("Sobol", new SobolPointSetGenerator().toString());
		assertEquals("Owen-Scrambled Sobol",
				new OwenScrambledSobolPointSetGenerator(new Random(1L)).toString());
	}

	@Test
	public void testMonteCarloUsesSuppliedRandomGenerator() {
		long seed = 87234L;
		PointSet points = new MonteCarloPointSetGenerator(new Random(seed)).generate(4, 3);
		Random expected = new Random(seed);
		for (int p=0; p<points.size(); p++)
			for (int d=0; d<points.dimensions(); d++)
				assertEquals(expected.nextDouble(), points.get(p, d), TOL);
	}

	@Test
	public void testLatinHypercubeHasOneCoordinatePerStratum() {
		int size = 127;
		PointSet points = new LatinHypercubePointSetGenerator(new Random(19283L)).generate(size, 7);
		for (int d=0; d<points.dimensions(); d++) {
			boolean[] occupied = new boolean[size];
			for (int p=0; p<size; p++) {
				int stratum = (int)(points.get(p, d)*size);
				assertTrue(stratum >= 0 && stratum < size);
				assertTrue("Duplicate stratum " + stratum + " in dimension " + d, !occupied[stratum]);
				occupied[stratum] = true;
			}
			for (boolean present : occupied)
				assertTrue(present);
		}
	}

	@Test
	public void testRandomizedGeneratorsAreReproducible() {
		assertPointSetsEqual(
				new LatinHypercubePointSetGenerator(new Random(1234L)).generate(20, 4),
				new LatinHypercubePointSetGenerator(new Random(1234L)).generate(20, 4));
		assertPointSetsEqual(
				new OwenScrambledSobolPointSetGenerator(new Random(5678L)).generate(20, 4),
				new OwenScrambledSobolPointSetGenerator(new Random(5678L)).generate(20, 4));
	}

	@Test
	public void testKnownTwoDimensionalSobolPoints() {
		double[][] expected = {
				{ 0d, 0d },
				{ 0.5, 0.5 },
				{ 0.75, 0.25 },
				{ 0.25, 0.75 },
				{ 0.375, 0.375 },
				{ 0.875, 0.875 },
				{ 0.625, 0.125 },
				{ 0.125, 0.625 }
		};
		PointSet points = new SobolPointSetGenerator().generate(expected.length, 2);
		for (int p=0; p<expected.length; p++)
			for (int d=0; d<2; d++)
				assertEquals(expected[p][d], points.get(p, d), TOL);
	}

	@Test
	public void testSobolMatchesCommonsMathReference() {
		int size = 128;
		int dimensions = 12;
		PointSet points = new SobolPointSetGenerator().generate(size, dimensions);
		SobolSequenceGenerator reference = new SobolSequenceGenerator(dimensions);
		for (int p=0; p<size; p++) {
			double[] expected = reference.nextVector();
			for (int d=0; d<dimensions; d++)
				assertEquals("Point " + p + ", dimension " + d, expected[d], points.get(p, d), TOL);
		}
	}

	@Test
	public void testSobolStartingIndexMatchesSequenceSlice() {
		PointSet all = new SobolPointSetGenerator().generate(80, 5);
		PointSet slice = new SobolPointSetGenerator(37L).generate(29, 5);
		for (int p=0; p<slice.size(); p++)
			for (int d=0; d<slice.dimensions(); d++)
				assertEquals(all.get(p+37, d), slice.get(p, d), TOL);
	}

	@Test
	public void testScrambledSobolStartingIndexMatchesSequenceSlice() {
		PointSet all = new OwenScrambledSobolPointSetGenerator(new Random(18723L)).generate(80, 5);
		PointSet slice = new OwenScrambledSobolPointSetGenerator(new Random(18723L), 37L).generate(29, 5);
		for (int p=0; p<slice.size(); p++)
			for (int d=0; d<slice.dimensions(); d++)
				assertEquals(all.get(p+37, d), slice.get(p, d), TOL);
	}

	@Test
	public void testOwenScramblePreservesPowerOfTwoMarginalStrata() {
		int size = 256;
		PointSet points = new OwenScrambledSobolPointSetGenerator(new Random(78342L)).generate(size, 10);
		for (int d=0; d<points.dimensions(); d++) {
			boolean[] occupied = new boolean[size];
			for (int p=0; p<size; p++) {
				int stratum = (int)(points.get(p, d)*size);
				assertTrue("Duplicate scrambled stratum " + stratum + " in dimension " + d,
						!occupied[stratum]);
				occupied[stratum] = true;
			}
		}
	}

	@Test
	public void testIndependentOwenScramblesDiffer() {
		OwenScrambledSobolPointSetGenerator generator =
				new OwenScrambledSobolPointSetGenerator(new Random(29834L));
		PointSet first = generator.generate(16, 3);
		PointSet second = generator.generate(16, 3);
		assertNotEquals(first.get(0, 0), second.get(0, 0), TOL);
	}

	@Test
	public void testLowDiscrepancyGeneratorsBeatFixedMonteCarloBaseline() {
		int size = 256;
		int dimensions = 4;
		ExactPointSetScorer scorer = new ExactPointSetScorer();
		double monteCarlo = scorer.score(
				new MonteCarloPointSetGenerator(new Random(234L)).generate(size, dimensions)).getNormalizedScore();
		double lhs = scorer.score(
				new LatinHypercubePointSetGenerator(new Random(234L)).generate(size, dimensions)).getNormalizedScore();
		double sobol = scorer.score(new SobolPointSetGenerator().generate(size, dimensions)).getNormalizedScore();
		double scrambled = scorer.score(
				new OwenScrambledSobolPointSetGenerator(new Random(234L)).generate(size, dimensions))
				.getNormalizedScore();
		assertTrue(lhs < monteCarlo);
		assertTrue(sobol < monteCarlo);
		assertTrue(scrambled < monteCarlo);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testTooManySobolDimensionsRejected() {
		new SobolPointSetGenerator().generate(1, SobolDirectionNumbers.MAX_DIMENSIONS+1);
	}

	private static void assertPointSetsEqual(PointSet first, PointSet second) {
		assertEquals(first.size(), second.size());
		assertEquals(first.dimensions(), second.dimensions());
		for (int p=0; p<first.size(); p++) {
			double[] firstPoint = new double[first.dimensions()];
			double[] secondPoint = new double[second.dimensions()];
			for (int d=0; d<first.dimensions(); d++) {
				firstPoint[d] = first.get(p, d);
				secondPoint[d] = second.get(p, d);
			}
			assertTrue(Arrays.equals(firstPoint, secondPoint));
		}
	}
}
