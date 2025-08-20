package org.opensha.commons.util.interp;

import static org.junit.Assert.*;

import java.util.function.IntToDoubleFunction;

import org.junit.Test;

public class DistanceInterpolatorTest {

	private static final double EPS = 1e-12;

	@Test
	public void testSingleton() {
		DistanceInterpolator a = DistanceInterpolator.get();
		DistanceInterpolator b = DistanceInterpolator.get();
		assertSame(a, b);
	}

	@Test
	public void testSizeMatchesConstants() {
		int numLinear = (int) Math.ceil(1.0 / DistanceInterpolator.LINEAR_SPACING);
		double logMin = Math.log10(DistanceInterpolator.LINEAR_LOG_TRANSITION);
		double logMax = Math.log10(DistanceInterpolator.OVERALL_MAX_DIST);
		int numLog = (int) Math.ceil((logMax - logMin) / DistanceInterpolator.LOG_SPACING) + 1;
		int expected = numLinear + numLog;

		assertEquals(expected, DistanceInterpolator.get().size());
	}

	@Test
	public void testDistancesMonotonicAndSeam() {
		DistanceInterpolator di = DistanceInterpolator.get();
		int n = di.size();

		// non-decreasing distances
		double prev = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			double d = di.getDistance(i);
			assertTrue("Distances must be non-decreasing", d + EPS >= prev);
			prev = d;
		}

		// seam behavior: 0.95 should exist, and 1.0 should be present as the first log distance
		boolean saw095 = false, saw10 = false;
		for (int i = 0; i < n; i++) {
			double d = di.getDistance(i);
			if (Math.abs(d - 0.95) < 1e-9) saw095 = true;
			if (Math.abs(d - 1.0)  < 1e-12) saw10 = true;
		}
		assertTrue("Expected 0.95 in linear grid", saw095);
		assertTrue("Expected 1.0 at log seam", saw10);
	}

	@Test
	public void testLogDistances() {
		DistanceInterpolator di = DistanceInterpolator.get();
		int n = di.size();

		for (int i = 0; i < n; i++) {
			double d = di.getDistance(i);
			double ld = di.getLogDistance(i);
			if (d == 0.0) {
				assertTrue("log10(0) should be -Inf", Double.isInfinite(ld) && ld < 0);
			} else {
				assertTrue("log distance must be finite for d>0", Double.isFinite(ld));
				assertEquals(Math.log10(d), ld, 1e-12);
			}
		}
	}

	@Test
	public void testEqualsDiscreteDistanceTolerance() {
		DistanceInterpolator di = DistanceInterpolator.get();

		// pick a safe non-zero index
		int idx = 10;
		double d = di.getDistance(idx);
		double tol = DistanceInterpolator.DIST_TOL * 0.9;

		assertTrue(di.equalsDiscreteDistance(idx, d));
		assertTrue(di.equalsDiscreteDistance(idx, d + tol));
		assertTrue(di.equalsDiscreteDistance(idx, d - tol));

		double tooFar = DistanceInterpolator.DIST_TOL * 1.1;
		assertFalse(di.equalsDiscreteDistance(idx, d + tooFar));
		assertFalse(di.equalsDiscreteDistance(idx, d - tooFar));
	}

	@Test
	public void testQuickInterpolatorDiscreteEdge() {
		DistanceInterpolator di = DistanceInterpolator.get();

		for (int i = 0; i < di.size(); i++) {
			double edge = di.getDistance(i);

			DistanceInterpolator.QuickInterpolator qi =
					di.getQuickInterpolator(edge, /*interpLogX=*/false);

			assertTrue(qi.isDiscrete());
			assertEquals(edge, qi.getTargetDistance(), 0.0);
			assertEquals(i, qi.getIndex1());
			assertEquals(i, qi.getIndex2());
			assertEquals(edge, qi.getDistance1(), 0.0);
			assertEquals(edge, qi.getDistance2(), 0.0);

			// f(i)=i -> exact index at the edge
			double valFunc = qi.interpolate((IntToDoubleFunction) k -> (double) k);
			assertEquals(i, valFunc, 0.0);

			// y1/y2 overload should return y1 for discrete case
			assertEquals(123.0, qi.interpolate(123.0, 999.0), 0.0);
		}
	}

	@Test
	public void testQuickInterpolatorMidLinearBin() {
		DistanceInterpolator di = DistanceInterpolator.get();

		// Midpoint of the first linear bin [0.00, 0.05]
		double d0 = di.getDistance(0);
		double d1 = di.getDistance(1);
		double mid = 0.5 * (d0 + d1);

		DistanceInterpolator.QuickInterpolator qi =
				di.getQuickInterpolator(mid, /*interpLogX=*/false);

		assertFalse(qi.isDiscrete());
		assertEquals(0, qi.getIndex1());
		assertEquals(1, qi.getIndex2());
		assertEquals(d0, qi.getDistance1(), 0.0);
		assertEquals(d1, qi.getDistance2(), 0.0);
		assertEquals(mid, qi.getTargetDistance(), 0.0);

		// f(i)=i should interpolate to 0.5 at the midpoint
		double valFunc = qi.interpolate((IntToDoubleFunction) k -> (double) k);
		assertEquals(0.5, valFunc, 1e-12);

		// direct overload should match binDeltaInterp
		double valDirect = qi.interpolate(0.0, 1.0);
		assertEquals(0.5, valDirect, 1e-12);
	}

	@Test
	public void testQuickInterpolatorMidLogBin() {
		DistanceInterpolator di = DistanceInterpolator.get();
		int n = di.size();

		// find index where distance == 1.0 (start of log region)
		int idx1 = -1;
		for (int i = 0; i < n; i++) {
			if (Math.abs(di.getDistance(i) - 1.0) < 1e-12) { idx1 = i; break; }
		}
		assertTrue("Expected to find 1.0 in the grid", idx1 >= 0 && idx1 + 1 < n);

		double d1 = di.getDistance(idx1);
		double d2 = di.getDistance(idx1 + 1);

		// Midpoint in log-x is geometric mean in linear-x
		double midLog = Math.sqrt(d1 * d2);

		DistanceInterpolator.QuickInterpolator qi =
				di.getQuickInterpolator(midLog, /*interpLogX=*/true);

		assertFalse(qi.isDiscrete());
		assertEquals(idx1, qi.getIndex1());
		assertEquals(idx1 + 1, qi.getIndex2());
		assertEquals(d1, qi.getDistance1(), 1e-15);
		assertEquals(d2, qi.getDistance2(), 1e-15);

		// With f(i)=i, midpoint in log-x -> ~idx1 + 0.5
		double valFunc = qi.interpolate((IntToDoubleFunction) k -> (double) k);
		assertEquals(idx1 + 0.5, valFunc, 1e-2);

		// And interpolate(y1,y2) should match the same fraction
		double valDirect = qi.interpolate(0.0, 1.0);
		assertEquals(0.5, valDirect, 1e-2);
	}

	@Test
	public void testBeyondMaxDistanceCapsAndIsDiscrete() {
		DistanceInterpolator di = DistanceInterpolator.get();
		double beyond = DistanceInterpolator.OVERALL_MAX_DIST + 42.0;

		DistanceInterpolator.QuickInterpolator qi =
				di.getQuickInterpolator(beyond, /*interpLogX=*/true);

		int last = di.size() - 1;
		assertTrue(qi.isDiscrete());
		assertEquals(last, qi.getIndex1());
		assertEquals(last, qi.getIndex2());
		assertEquals(DistanceInterpolator.OVERALL_MAX_DIST, qi.getTargetDistance(), 0.0);

		// f(i)=i -> last index
		double val = qi.interpolate((IntToDoubleFunction) k -> (double) k);
		assertEquals(last, val, 0.0);
	}

	@Test(expected = IllegalStateException.class)
	public void testGetDistanceBadIndexNegative() {
		DistanceInterpolator.get().getDistance(-1);
	}

	@Test(expected = IllegalStateException.class)
	public void testGetDistanceBadIndexTooLarge() {
		DistanceInterpolator di = DistanceInterpolator.get();
		di.getDistance(di.size());
	}

	@Test(expected = IllegalStateException.class)
	public void testGetLogDistanceBadIndexNegative() {
		DistanceInterpolator.get().getLogDistance(-1);
	}

	@Test(expected = IllegalStateException.class)
	public void testGetLogDistanceBadIndexTooLarge() {
		DistanceInterpolator di = DistanceInterpolator.get();
		di.getLogDistance(di.size());
	}

	@Test
	public void testNoSignFlipNearZero() {
		DistanceInterpolator di = DistanceInterpolator.get();

		// first linear bin midpoint
		double d0 = di.getDistance(0);
		double d1 = di.getDistance(1);
		double mid = 0.5 * (d0 + d1);

		DistanceInterpolator.QuickInterpolator qi =
				di.getQuickInterpolator(mid, /*interpLogX=*/false);

		// tiny positives on both ends remain non-negative
		IntToDoubleFunction tinyPos = i -> (i == qi.getIndex1() ? 1e-12 : 2e-12);
		double val = qi.interpolate(tinyPos);
		assertTrue(val >= 0.0);
		assertTrue(val <= 2e-12 + 1e-18);

		// tiny negatives on both ends remain non-positive
		IntToDoubleFunction tinyNeg = i -> (i == qi.getIndex1() ? -1e-12 : -2e-12);
		double valNeg = qi.interpolate(tinyNeg);
		assertTrue(valNeg <= 0.0);
		assertTrue(valNeg >= -2e-12 - 1e-18);
	}
}
