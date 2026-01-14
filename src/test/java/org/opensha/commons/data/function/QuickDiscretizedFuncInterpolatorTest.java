package org.opensha.commons.data.function;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;
import org.opensha.commons.exceptions.InvalidRangeException;

/**
 * JUnit4 tests for {@link DiscretizedFuncInterpolator}.
 *
 * Compares against {@link DiscretizedFunc#getInterpolatedY(double, boolean, boolean)}.
 */
public class QuickDiscretizedFuncInterpolatorTest {

	private static final double TEST_TOL = 1e-10;
	private static final double FUNC_TOL = 1e-12;

	@Test
	public void testFactoryType_evenlyVsArbitrary() {
		EvenlyDiscretizedFunc ef = buildEvenlyFunc(0d, 1d, 11, false);
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(ef, false, false);
		assertTrue(qi instanceof DiscretizedFuncInterpolator.PrecomputedEvenlyDiscretized);

		DiscretizedFuncInterpolator qiLogX = DiscretizedFuncInterpolator.getOptimized(ef, true, false);
		assertTrue(qiLogX instanceof DiscretizedFuncInterpolator.PrecomputedArbitrarilyDiscretized);

		DiscretizedFunc af = buildArbitraryFunc(false);
		DiscretizedFuncInterpolator qiA = DiscretizedFuncInterpolator.getOptimized(af, false, false);
		assertTrue(qiA instanceof DiscretizedFuncInterpolator.PrecomputedArbitrarilyDiscretized);
	}

	@Test
	public void testMatchesReference_evenly_linearXY() {
		EvenlyDiscretizedFunc f = buildEvenlyFunc(0d, 0.5d, 41, false);
		checkMany(f, false, false, 50_000, new Random(1));
	}

	@Test
	public void testMatchesReference_evenly_logY() {
		// ensure positive y values
		EvenlyDiscretizedFunc f = buildEvenlyFunc(0d, 0.5d, 41, true);
		checkMany(f, false, true, 50_000, new Random(2));
	}

	@Test
	public void testMatchesReference_arbitrary_linearXY() {
		DiscretizedFunc f = buildArbitraryFunc(false);
		checkMany(f, false, false, 50_000, new Random(3));
	}

	@Test
	public void testMatchesReference_arbitrary_logX() {
		// must keep x > 0 for logX
		DiscretizedFunc f = buildArbitraryFuncPositiveX(false);
		checkMany(f, true, false, 50_000, new Random(4));
	}

	@Test
	public void testMatchesReference_arbitrary_logY() {
		DiscretizedFunc f = buildArbitraryFunc(true);
		checkMany(f, false, true, 50_000, new Random(5));
	}

	@Test
	public void testMatchesReference_arbitrary_logX_logY() {
		DiscretizedFunc f = buildArbitraryFuncPositiveX(true);
		checkMany(f, true, true, 50_000, new Random(6));
	}

	@Test
	public void testExactGridPoints_evenly() {
		EvenlyDiscretizedFunc f = buildEvenlyFunc(-2d, 0.25d, 33, false);
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, false, false);

		for (int i = 0; i < f.size(); i++) {
			double x = f.getX(i);
			double yRef = f.getInterpolatedY(x, false, false);
			double y = qi.findY(x);
			assertEquals(yRef, y, TEST_TOL);
		}
	}

	@Test
	public void testExactGridPoints_arbitrary() {
		DiscretizedFunc f = buildArbitraryFunc(false);
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, false, false);

		for (int i = 0; i < f.size(); i++) {
			double x = f.getX(i);
			double yRef = f.getInterpolatedY(x, false, false);
			double y = qi.findY(x);
			assertEquals(yRef, y, TEST_TOL);
		}
	}

	@Test
	public void testSnapToEndpointsWithinTolerance() {
		EvenlyDiscretizedFunc f = buildEvenlyFunc(0d, 1d, 11, false);
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, false, false);

		double minX = f.getX(0);
		double maxX = f.getX(f.size() - 1);
		double tol = f.getTolerance();

		// just below min within tol -> y(0)
		double x1 = minX - 0.5 * tol;
		assertEquals(f.getY(0), qi.findY(x1), 0d);
		assertEquals(f.getY(0), f.getInterpolatedY(x1, false, false), 0d);

		// just above max within tol -> y(last)
		double x2 = maxX + 0.5 * tol;
		assertEquals(f.getY(f.size() - 1), qi.findY(x2), 0d);
		assertEquals(f.getY(f.size() - 1), f.getInterpolatedY(x2, false, false), 0d);
	}

	@Test
	public void testThrowsOutsideTolerance() {
		EvenlyDiscretizedFunc f = buildEvenlyFunc(0d, 1d, 11, false);
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, false, false);

		double minX = f.getX(0);
		double maxX = f.getX(f.size() - 1);
		double tol = f.getTolerance();

		try {
			qi.findY(minX - 2d * tol);
			fail("Expected InvalidRangeException");
		} catch (InvalidRangeException expected) {}

		try {
			qi.findY(maxX + 2d * tol);
			fail("Expected InvalidRangeException");
		} catch (InvalidRangeException expected) {}

		// and reference method should match behavior
		try {
			f.getInterpolatedY(minX - 2d * tol, false, false);
			fail("Expected InvalidRangeException");
		} catch (InvalidRangeException expected) {}

		try {
			f.getInterpolatedY(maxX + 2d * tol, false, false);
			fail("Expected InvalidRangeException");
		} catch (InvalidRangeException expected) {}
	}

	@Test
	public void testReflectsMomentConstructed_doesNotUpdate() {
		EvenlyDiscretizedFunc f = buildEvenlyFunc(0d, 1d, 11, false);
		double xQuery = 3.4;

		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, false, false);
		double yBefore = qi.findY(xQuery);

		// mutate underlying function after construction
		int i = f.getXIndexBefore(xQuery);
		f.set(i, f.getY(i) + 100d);
		f.set(i + 1, f.getY(i + 1) + 200d);

		double yAfter = qi.findY(xQuery);

		// should not change (uses cached yDom and slopes)
		assertEquals(yBefore, yAfter, 0d);

		// reference interpolation SHOULD change (uses live function)
		double yRefAfter = f.getInterpolatedY(xQuery, false, false);
		assertNotEquals(yBefore, yRefAfter, 0d);
	}

	/* ---------------- helpers ---------------- */

	private static void checkMany(DiscretizedFunc f, boolean logX, boolean logY, int num, Random r) {
		DiscretizedFuncInterpolator qi = DiscretizedFuncInterpolator.getOptimized(f, logX, logY);

		double minX = f.getX(0);
		double maxX = f.getX(f.size() - 1);
		double tol = f.getTolerance();

		// sample strictly interior to avoid endpoint snapping dominating
		double lo = minX + 10d * tol;
		double hi = maxX - 10d * tol;

		for (int k = 0; k < num; k++) {
			double u = r.nextDouble();
			double x = lo + u * (hi - lo);

			double yRef = f.getInterpolatedY(x, logX, logY);
			double y = qi.findY(x);

			// Use exact equality for NaN/Inf matches
			if (Double.isNaN(yRef))
				assertTrue(Double.isNaN(y));
			else if (Double.isInfinite(yRef))
				assertTrue(Double.isInfinite(y) && Math.signum(yRef) == Math.signum(y));
			else
				assertEquals(yRef, y, TEST_TOL);
		}
	}

	private static EvenlyDiscretizedFunc buildEvenlyFunc(double x0, double dx, int n, boolean positiveY) {
		EvenlyDiscretizedFunc f = new EvenlyDiscretizedFunc(x0, n, dx);
		f.setTolerance(FUNC_TOL);

		for (int i = 0; i < n; i++) {
			double x = f.getX(i);

			// A non-linear-ish function so we're actually testing interpolation, not exactness.
			double y = Math.sin(0.7 * x) + 0.1 * x * x;

			if (positiveY) y = y * y + 1.0; // strictly positive
			f.set(i, y);
		}

		return f;
	}

	private static DiscretizedFunc buildArbitraryFunc(boolean positiveY) {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.setTolerance(FUNC_TOL);

		// monotonic increasing x, includes some uneven spacing, includes <=0 x sometimes
		double[] xs = new double[] { -3.0, -2.2, -1.7, -0.9, -0.1, 0.3, 1.8, 2.1, 3.7, 6.0, 10.0 };
		for (double x : xs) {
			double y = Math.cos(0.4 * x) + 0.05 * x * x - 0.2 * x;
			if (positiveY) y = y * y + 0.5;
			f.set(x, y);
		}

		return f;
	}

	private static DiscretizedFunc buildArbitraryFuncPositiveX(boolean positiveY) {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.setTolerance(FUNC_TOL);

		double[] xs = new double[] { 0.05, 0.09, 0.2, 0.55, 1.1, 2.3, 3.0, 4.8, 7.5, 12.0 };
		for (double x : xs) {
			double y = Math.cos(0.4 * x) + 0.05 * x * x - 0.2 * x;
			if (positiveY) y = y * y + 0.5;
			f.set(x, y);
		}

		return f;
	}
}
