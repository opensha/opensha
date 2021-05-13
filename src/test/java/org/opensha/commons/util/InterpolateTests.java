package org.opensha.commons.util;

import static org.junit.Assert.*;
import static org.opensha.commons.util.Interpolate.*;

import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class InterpolateTests {

	@Test
	public void testFindXY() {
		double x1, y1, x2, y2;

		// positive slope
		x1 = 0;
		y1 = 0;
		x2 = 2;
		y2 = 3;
		assertEquals(1.5, findY(x1, y1, x2, y2, 1), 0);
		assertEquals(6, findY(x1, y1, x2, y2, 4), 0);
		assertEquals(-1.5, findY(x1, y1, x2, y2, -1), 0);
		assertEquals(1, findX(x1, y1, x2, y2, 1.5), 0);
		assertEquals(3, findX(x1, y1, x2, y2, 4.5), 0);
		assertEquals(-4, findX(x1, y1, x2, y2, -6), 0);

		// negative slope
		x1 = 0;
		y1 = 3;
		x2 = 2;
		y2 = 0;
		assertEquals(1.5, findY(x1, y1, x2, y2, 1), 0);
		assertEquals(-3, findY(x1, y1, x2, y2, 4), 0);
		assertEquals(4.5, findY(x1, y1, x2, y2, -1), 0);
		assertEquals(1, findX(x1, y1, x2, y2, 1.5), 0);
		assertEquals(-1, findX(x1, y1, x2, y2, 4.5), 0);
		assertEquals(6, findX(x1, y1, x2, y2, -6), 0);

		// NaN - findY passes thru to findX so only need to test one
		assertTrue(Double.isNaN(findX(Double.NaN, 0, 2, 2, 1)));
		assertTrue(Double.isNaN(findX(0, Double.NaN, 2, 2, 1)));
		assertTrue(Double.isNaN(findX(0, 0, Double.NaN, 2, 1)));
		assertTrue(Double.isNaN(findX(0, 0, 2, Double.NaN, 1)));
		assertTrue(Double.isNaN(findX(0, 0, 2, 2, Double.NaN)));

	}

	@Test
	public final void testFindXY_array() {
		// findY(xs[], ys[], x[]) passes thru to findY(xs[], ys[], x)
		// so we just test that
		double[] xs, ys;
		xs = new double[] { 0, 2, 4, 6 };
		ys = new double[] { 0, 2, 1, 3 };
		double[] xVals = { -1, 0, 1, 2, 3, 4, 5, 6, 7 };   // input
		double[] yOuts = { -1, 0, 1, 2, 1.5, 1, 2, 3, 4 }; // expected
		assertArrayEquals(yOuts, findY(xs, ys, xVals), 0);
	}
	
	@Test
	public final void testFindLogLogXY_array() {
		double[] xs, ys;
		xs = new double[] { 1e0, 1e2, 1e4, 1e5, 1e6 };
		ys = new double[] { 1e0, 1e4, 1e2, 1e3, 1e1 };
		double[] xVals = { 1e-1, 1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7 }; // input
		double[] yOuts = { 1e-2, 1e0, 1e2, 1e4, 1e3, 1e2, 1e3, 1e1, 1e-1}; // expected
		assertArrayEquals(yOuts, findLogLogY(xs, ys, xVals), 0.00000000001);
	}
}
