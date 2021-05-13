package org.opensha.commons.eq.cat.filters;

import static org.junit.Assert.*;
import static org.opensha.commons.eq.cat.filters.CatalogBrush.calcMinCaret;
import static org.opensha.commons.eq.cat.filters.CatalogBrush.calcMaxCaret;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CatalogBrushTest {

	private static double[] M = { 
		0.7, 0.7, 1.8, 2.7, 2.7, 2.9, 3.6, 4.5, 4.5, 4.5, 6.7, 7.2, 7.2 };
	//  0    1    2    3    4    5    6    7    8    9    10   11   12
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	@Before
	public void setUp() throws Exception {}

	@Test
	public void testCalcMinCaret() {
		// test with carets at extrema
		int cMin = 0;  // 0.7
		int cMax = 13; // 7.2
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 0.1), -1);   // [-1] below bottom
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 0.7), -1);   // [-1] at bottom; at min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 2.7), 3);    //  [3] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 2.8), 5);    //  [5] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 4.5), 7);    //  [7] above min in sequence
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 7.2), 11);   // [11] at top; at max
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 8.0), 11);   // [11] above top
		// test with adjusted carets
		cMin = 2;  // 1.8
		cMax = 11; // 6.7
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 0.1), 0);    //  [0] below bottom
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 0.7), 0);    //  [0] at bottom; below min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 1.8), -1);   // [-1] at min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 2.7), 3);    //  [3] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 2.8), 5);    //  [5] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 4.5), 7);    //  [7] above min in sequence
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 4.6), 10);   // [10] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 6.7), 10);   // [10] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 6.8), 10);   // [10] above min
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 7.2), 10);   // [10] at max; at top
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 8.0), 10);   // [10] above top
		// test carets at internal sequence
		cMax = 10; // 4.5
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 4.6), 7);    //  [7] above max
		assertEquals(calcMinCaret(M, cMin, cMax, M[cMin], 4.5),7 );    //  [7] at max
	}

	@Test
	public void testCalcMaxCaret() {
		// test with carets at extrema
		int cMin = 0;  // 0.7
		int cMax = 13; // 7.2
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 0.1), 2);  //  [2] below bottom
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 0.7), 2);  //  [2] at bottom; at min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 3.0), 6);  //  [6] above min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 4.5), 10); // [10] above min in sequence
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 7.2), -1); // [-1] at top; at max
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 8.0), -1); // [-1] above top
		// test with adjusted carets
		cMin = 2;  // 1.8
		cMax = 11; // 6.7
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 0.1), 3);  //  [3] below bottom
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 0.7), 3);  //  [3] at bottom
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 1.8), 3);  //  [3] at min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 1.9), 3);  //  [3] above min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 2.7), 5);  //  [5] above min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 3.0), 6);  //  [6] above min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 4.5), 10); // [10] above min in sequence
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 4.6), 10); // [10] above min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 6.7), -1); // [-1] at max
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 7.2), 13); // [13] at top
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 8.0), 13); // [13] above top
		// test carets at internal sequence
		cMin = 7; // 4.5
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 4.5), 10); // [10] below min
		assertEquals(calcMaxCaret(M, cMin, cMax, M[cMax-1], 4.5), 10); // [10] at min
	}
	
	// test with carets at extremes

}
