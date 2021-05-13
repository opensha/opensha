package org.opensha.commons.util;

import static org.junit.Assert.*;
import static org.opensha.commons.util.DataUtils.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.data.function.DefaultXY_DataSet;

import com.google.common.primitives.Doubles;

@SuppressWarnings("javadoc")
public class DataUtilsTest {

	private double[] dd = {-10, Double.NaN, 0.0, 10};
	
	@Test (expected = NullPointerException.class)
	public final void testValidateArrayNPE() { validate(-10, 10, null); }

	@Test (expected = IllegalArgumentException.class)
	public final void testValidateArray1() { validate(-9, 10, dd); }

	@Test (expected = IllegalArgumentException.class)
	public final void testValidateArray2() { validate(-10, 9, dd); }
	
	
	@Test (expected = IllegalArgumentException.class)
	public final void testValidate0() { validate(-10, 10, 11); }

	@Test (expected = IllegalArgumentException.class)
	public final void testValidate1() { validate(-10, 10, -11); }
	
	@Test (expected = IllegalArgumentException.class)
	public final void testValidate2() { validate(-10, Double.NaN, -11); }

	@Test (expected = IllegalArgumentException.class)
	public final void testValidate3() { validate(Double.NaN, 10, 11); }

	@Test (expected = IllegalArgumentException.class)
	public final void testValidate7() { validate(10, -10, 0); }

	@Test
	public final void testValidate() throws IllegalArgumentException {
		// should be able to get through these
		validate(-1, 1, Double.NaN);
		validate(Double.NaN, Double.NaN, 0);
		validate(Double.NaN, Double.NaN, Double.NaN);
	}
	
	@Test (expected = NullPointerException.class)
	public final void testArraySelectNPE1() { arraySelect(null, new int[0]); }
	
	@Test (expected = NullPointerException.class)
	public final void testArraySelectNPE2() { arraySelect(new int[0], null); }
	
	@Test (expected = IllegalArgumentException.class)
	public final void testArraySelectIAE1() { arraySelect(new Object(), new int[0]); }

	@Test (expected = IllegalArgumentException.class)
	public final void testArraySelectIAE2() { arraySelect(new double[0], new int[0]); }

	@Test (expected = IndexOutOfBoundsException.class)
	public final void testArraySelectIOOB1() { arraySelect(new double[10], new int[] {-2}); }
	
	@Test (expected = IndexOutOfBoundsException.class)
	public final void testArraySelectIOOB2() { arraySelect(new double[10], new int[] {10}); }
	
	@Test
	public final void testArraySelect() {
		int[] result = new int[] { -10, 5, 17, 2010 };
		int[] array = new int[] { -4, -10, 9, -20, 5, 17, 3000, 2010 };
		int[] idx = new int[] { 1, 4, 5, 7};
		assertTrue(Arrays.equals(
			(int[]) DataUtils.arraySelect(array, idx), result));
	}

	// length = 20
	private double[] nnDat = {2,3,3,8,8,8,9,4,10,17,18,18,5,7,12,20,11,3,2,6};
	private double[] nnDat_null = null;
	private double[] nnDat_empty = {};
	private double[] nnDat_repeat = {2,2,3,3,3,3,3,3,4,4};
	private double[] nnDat_repeatSmall = {3,3,3,3};
	
	@Test (expected = NullPointerException.class)
	public final void testNNhistNPE() { nearestNeighborHist(nnDat_null, 0, 2); }
	@Test (expected = IllegalArgumentException.class)
	public final void testNNhistIAE() { nearestNeighborHist(nnDat_empty, 0, 2); }
	@Test (expected = IllegalArgumentException.class)
	public final void testNNhistIAE2() { nearestNeighborHist(nnDat, 0, -1); }
	@Test (expected = IllegalArgumentException.class)
	public final void testNNhistIAE3() { nearestNeighborHist(nnDat, 21, -1); }
	@Test
	public final void testNNhistNull() {
		assertNull(nearestNeighborHist(nnDat, 19, 3));
		assertNull(nearestNeighborHist(nnDat, 18, 4));
	}
	@Test
	public final void testNNhist() {
		double delta = 0.0001;
		
		DefaultXY_DataSet xy = nearestNeighborHist(nnDat, 1, 3);
		assertTrue(xy.size() == 6);
		assertEquals(1.5, xy.getY(0), delta);
		assertEquals(3.0, xy.getY(1), delta);
		assertEquals(1.0, xy.getY(2), delta);
		assertEquals(3.0, xy.getY(3), delta);
		assertEquals(1.0, xy.getY(4), delta);
		assertEquals(0.4286, xy.getY(5), delta);
		assertEquals(2.0, xy.getX(0), delta);
		assertEquals(3.0, xy.getX(1), delta);
		assertEquals(6.0, xy.getX(2), delta);
		assertEquals(8.0, xy.getX(3), delta);
		assertEquals(10.0, xy.getX(4), delta);
		assertEquals(17.0, xy.getX(5), delta);
		
		xy = nearestNeighborHist(nnDat, 1, 4);
		assertTrue(xy.size() == 5);
		assertEquals(2.0, xy.getY(0), delta);
		assertEquals(1.3333, xy.getY(1), delta);
		assertEquals(2.0, xy.getY(2), delta);
		assertEquals(1.0, xy.getY(3), delta);
		assertEquals(0.5, xy.getY(4), delta);
		assertEquals(2.5, xy.getX(0), delta);
		assertEquals(4.5, xy.getX(1), delta);
		assertEquals(8.0, xy.getX(2), delta);
		assertEquals(10.5, xy.getX(3), delta);
		assertEquals(18.0, xy.getX(4), delta);
		
		// testing repeat values that would have infinite bin values; results
		// in shorter output data set
		xy = nearestNeighborHist(nnDat_repeat, 1, 4);
		assertTrue(xy.size() == 1);
		
		// testing small array of repeat values that would have infinite bin
		// values and results in an empty output data set
		xy = nearestNeighborHist(nnDat_repeatSmall, 3, 4);
		assertNull(xy);
	}
	
	
	// new set of utils methods
	
	private double[] utility = {1,2,3,4,5};	

	@Test
	public final void testScale() {
		double[] dat = Arrays.copyOf(utility, utility.length);
		DataUtils.scale(5, dat);
		assertArrayEquals(new double[] {5,10,15,20,25}, dat, 0.0);
	}
	
	@Test(expected=NullPointerException.class)
	public final void testTransformNPE1() {
		double[] testVals = null;
		DataUtils.scale(5, testVals);
	}

	@Test(expected=NullPointerException.class)
	public final void testTransformNPE2() {
		List<Double> testVals = null;
		DataUtils.scale(5, testVals);
	}

	@Test
	public final void testAdd() {
		double[] dat = Arrays.copyOf(utility, utility.length);
		DataUtils.add(5, dat);
		assertArrayEquals(new double[] {6,7,8,9,10}, dat, 0.0);
	}

	@Test
	public final void testAbs() {
		double[] dat = Arrays.copyOf(utility, utility.length);
		DataUtils.scale(-1, dat);
		DataUtils.abs(dat);
		assertArrayEquals(new double[] {1,2,3,4,5}, dat, 0.0);
	}
	
	
	
	@Test(expected=NullPointerException.class)
	public final void testDiffNPE1() {
		DataUtils.diff(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public final void testDiffIAE() {
		DataUtils.diff(new double[0]);
	}

	@Test
	public final void testDiff() {
		double[] dat = Arrays.copyOf(utility, utility.length);
		double[] diff = DataUtils.diff(dat);
		
		// test size of returned array
		assertTrue(diff.length == (dat.length - 1));
		
		// test all values = 1
		assertTrue(Doubles.min(diff) == 1 && Doubles.max(diff) == 1);
		
		double[] d1 = {1,2,3,3,4,7};
		double[] diff1 = DataUtils.diff(d1);
		assertArrayEquals(new double[] {1,1,0,1,3}, diff1, 0);
		
		double[] d2 = {-3,8,0,3,4,2,5,5,1};
		double[] diff2 = DataUtils.diff(d2);
		assertArrayEquals(new double[] {11,-8,3,1,-2,3,0,-4}, diff2, 0);
		
	}
	
	@Test
	public final void testFlip() {
		double[] actual = {-2, -1, 0, 1, 2};
		double[] expect = Arrays.copyOf(actual, actual.length);
		Collections.reverse(Doubles.asList(expect));
		assertArrayEquals(expect, DataUtils.flip(actual), 0);
	}
	
	@Test
	public final void testMin() {
		double[] array = {-1, -4, 5, 10,};
		double[] arrayNaN = {-1, Double.NaN, 5, 10,};
		assertEquals(-4.0, DataUtils.min(array), 0.0);
		assertTrue(Double.isNaN(DataUtils.min(arrayNaN)));
	}	

	@Test
	public final void testMax() {
		double[] array = {-1, -4, 5, 10};
		double[] arrayNaN = {-1, Double.NaN, 5, 10};
		assertEquals(10.0, DataUtils.max(array), 0.0);
		assertTrue(Double.isNaN(DataUtils.max(arrayNaN)));
	}
	
	@Test(expected=NullPointerException.class)
	public final void testSumNPE() {
		double[] data = null;
		DataUtils.sum(data);
	}

	@Test
	public final void testSum() {
		double[] array = {0,1,2,3,4};
		double[] arrayNaN = {0,1,Double.NaN,3,4};
		assertEquals(10.0, DataUtils.sum(array), 0.0);
		assertEquals(0.0, DataUtils.sum(new double[0]), 0.0);
		assertTrue(Double.isNaN(DataUtils.sum(arrayNaN)));
	}
	
	@Test
	public final void testAsWeights() {
		double[] array = {0,1,2,3,4};
		double[] expect = {0, 0.1, 0.2, 0.3, 0.4};
		assertArrayEquals(expect, DataUtils.asWeights(array), 0.0000000000000001);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public final void testAsWeightsIAE1() { 
		DataUtils.asWeights(new double[] {0,1,Double.NaN,3,4}); 
	}

	@Test (expected = IllegalArgumentException.class)
	public final void testAsWeightsIAE2() { 
		DataUtils.asWeights(new double[] {0,1,-3,4}); 
	}

	@Test (expected = IllegalArgumentException.class)
	public final void testAsWeightsIAE3() { 
		DataUtils.asWeights(new double[] {0,1,Double.POSITIVE_INFINITY,3,4}); 
	}

	@Test (expected = IllegalArgumentException.class)
	public final void testAsWeightsIAE4() { 
		DataUtils.asWeights(new double[] {0,0}); 
	}

	@Test
	public final void testIsMonotonic() {
		
		double[] d1 = {-2, -1, 0, 1, 2};
		double[] d2 = {-2, -1, 0, 0, 1, 2};
		double[] d3 = {0, 1, 2, 2, 1, 0};
		
		double[] d4 = Arrays.copyOf(d1, d1.length);
		Collections.reverse(Doubles.asList(d4));
		double[] d5 = Arrays.copyOf(d2, d2.length);
		Collections.reverse(Doubles.asList(d5));
		double[] d6 = DataUtils.add(2, DataUtils.flip(d3));
		
		
		assertTrue(DataUtils.isMonotonic(true, false, d1));
		assertTrue(DataUtils.isMonotonic(true, true, d1));
		assertTrue(DataUtils.isMonotonic(true, true, d2));
		assertFalse(DataUtils.isMonotonic(true, false, d2));
		assertFalse(DataUtils.isMonotonic(true, false, d3));
		assertFalse(DataUtils.isMonotonic(true, true, d3));
		
		assertTrue(DataUtils.isMonotonic(false, false, d4));
		assertTrue(DataUtils.isMonotonic(false, true, d4));
		assertTrue(DataUtils.isMonotonic(false, true, d5));
		assertFalse(DataUtils.isMonotonic(false, false, d5));
		assertFalse(DataUtils.isMonotonic(false, false, d6));
		assertFalse(DataUtils.isMonotonic(false, true, d6));
	}
	
	@Test
	public final void testIsPositive() {
		
		double[] d0 = {1, 2, 3};
		double[] d1 = {-2, -1, 0, 1, 2};
		double[] d2 = {0, 1, 2, -2, -1, 0};
		double[] d3 = {1, 1, 2, 2, Double.NEGATIVE_INFINITY, 4};
		double[] d4 = {1, 1, 2, 2, Double.NaN, 4};
		
		assertTrue(DataUtils.isPositive(d0));
		assertFalse(DataUtils.isPositive(d1));
		assertFalse(DataUtils.isPositive(d2));
		assertFalse(DataUtils.isPositive(d3));
		assertFalse(DataUtils.isPositive(d4));
	}
	
	@Test
	public final void testPercentDiff() {
		assertEquals(2.0, DataUtils.getPercentDiff(98, 100), 0.0);
		assertEquals(2.0, DataUtils.getPercentDiff(102, 100), 0.0);
		assertEquals(Double.POSITIVE_INFINITY,DataUtils.getPercentDiff(1, 0), 0.0);
		assertEquals(0.0, DataUtils.getPercentDiff(0, 0), 0.0);
		assertEquals(Double.NaN, DataUtils.getPercentDiff(Double.NaN, 0), 0.0);
		assertEquals(Double.NaN, DataUtils.getPercentDiff(0, Double.NaN), 0.0);
	}
	
	private static final double SEQ_TOL = 0.000000000001;
	
	@Test
	public final void testCreateSequence() {
		double[] expectUp = {1, 1.7, 2.4, 3.1, 3.8, 4.5, 5};
		double[] resultUp = DataUtils.buildSequence(1, 5, 0.7, true);
		assertArrayEquals(expectUp, resultUp, SEQ_TOL);
		
		double[] expectDn = {5, 4.3, 3.6, 2.9, 2.2, 1.5, 1};
		double[] resultDn = DataUtils.buildSequence(1, 5, 0.7, false);
		assertArrayEquals(expectDn, resultDn, SEQ_TOL);
		
		double[] logExpUp = DataUtils.buildLogSequence(1, 1000, 10, true);
		double[] logResUp = {1, 10, 100, 1000};
		assertArrayEquals(logExpUp, logResUp, SEQ_TOL);
		
		double[] logExpDn = DataUtils.buildLogSequence(1, 1000, 10, false);
		double[] logResDn = {1000, 100, 10, 1};
		assertArrayEquals(logExpDn, logResDn, SEQ_TOL);
		
		
	}
	
	// the following tests a small number of argument options that should
	// generate an IAE. These chould suffice for coverage as the myriad of
	// combinations are coalesced into a single int value that will be out of
	// range
	@Test(expected=IllegalArgumentException.class)
	public final void testCreateSequenceIAE0() {
		DataUtils.buildSequence(1, Double.NaN, 0.7, true);
	}
	@Test(expected=IllegalArgumentException.class)
	public final void testCreateSequenceIAE1() {
		DataUtils.buildSequence(1, Double.POSITIVE_INFINITY, 0.7, true);
	}
	@Test(expected=IllegalArgumentException.class)
	public final void testCreateSequenceIAE2() {
		DataUtils.buildSequence(Double.NEGATIVE_INFINITY, 5, 0.7, true);
	}
	@Test(expected=IllegalArgumentException.class)
	public final void testCreateSequenceIAE3() { //min > max
		DataUtils.buildSequence(5, 1, 0.7, true);
	}
	@Test(expected=IllegalArgumentException.class)
	public final void testCreateSequenceIAE4() { //min = max
		DataUtils.buildSequence(1,1,1, true);
	}
	
	
	
	
//	public static void main(String[] args) {
//		// Function speed test
//		// prob needs memory increase
//
//		int size = 100000000;
//		Stopwatch sw = new Stopwatch();
//		sw.start();
//		double[] d1 = new double[size];
//		double[] d2 = new double[size];
//		for (int i = 0; i < size; i++) {
//			d1[i] = Math.random();
//			d2[i] = Math.random();
//		}
//		sw.stop();
//		System.out.println(sw.elapsedMillis());
//
//		sw.reset().start();
//		scale2(12.57, d2);
//		sw.stop();
//		System.out.println(sw.elapsedMillis());
//		sw.reset().start();
//		DataUtils.scale(12.57, d2);
//		sw.stop();
//		System.out.println(sw.elapsedMillis());
//		System.out.println(d1[3]);
//	}
//
//	public static double[] scale2(double scale, double... array) {
//		checkNotNull(array);
//		checkArgument(array.length > 0);
//		for (int i = 0; i < array.length; i++) {
//			array[i] = array[i] * scale;
//		}
//		return array;
//	}


}
