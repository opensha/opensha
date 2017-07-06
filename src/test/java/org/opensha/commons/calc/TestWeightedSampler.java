package org.opensha.commons.calc;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestWeightedSampler {
	
	private static ArrayList<Integer> testItems;
	private static ArrayList<Double> testRates;
	
	private static ArrayList<Integer> reversedTestItems;
	private static ArrayList<Double> reversedTestRates;
	
	private static ArrayList<Integer> randomizedTestItems;
	private static ArrayList<Double> randomizedTestRates;

	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testItems = new ArrayList<Integer>();
		testRates = new ArrayList<Double>();
		
		testItems.add(0);
		testRates.add(5d);
		testItems.add(1);
		testRates.add(2d);
		testItems.add(2);
		testRates.add(2d);
		testItems.add(3);
		testRates.add(1d);
		
		reversedTestItems = (ArrayList<Integer>) testItems.clone();
		Collections.reverse(reversedTestItems);
		reversedTestRates = (ArrayList<Double>) testRates.clone();
		Collections.reverse(reversedTestRates);
		
		randomizedTestItems = (ArrayList<Integer>) testItems.clone();
		Collections.shuffle(randomizedTestItems);
		randomizedTestRates = new ArrayList<Double>();
		for (int item : randomizedTestItems)
			randomizedTestRates.add(testRates.get(testItems.indexOf(item)));
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullItems() {
		new WeightedSampler<Integer>(null, testRates);
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullRates() {
		new WeightedSampler<Integer>(testItems, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyItems() {
		new WeightedSampler<Integer>(new ArrayList<Integer>(), testRates);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyRates() {
		new WeightedSampler<Integer>(testItems, new ArrayList<Double>());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testInconsistantSizes() {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Double> rates = new ArrayList<Double>();
		items.add(0);
		rates.add(0.5);
		rates.add(0.5);
		new WeightedSampler<Integer>(items, rates);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testAllZeroRates() {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Double> rates = new ArrayList<Double>();
		items.add(0);
		items.add(1);
		rates.add(0d);
		rates.add(0d);
		new WeightedSampler<Integer>(items, rates);
	}
	
	@Test
	public void testOneZeroRate() {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Double> rates = new ArrayList<Double>();
		items.add(0);
		items.add(1);
		rates.add(0d);
		rates.add(1d);
		new WeightedSampler<Integer>(items, rates);
		// no exception expected
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testBelowZeroRates() {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Double> rates = new ArrayList<Double>();
		items.add(0);
		items.add(1);
		rates.add(0d);
		rates.add(-1d);
		new WeightedSampler<Integer>(items, rates);
	}
	
//	@Test
//	public void testNormalization() {
//		PoissonSampler<Integer> s = new PoissonSampler<Integer>(testItems, testRates);
//		
//		double sum = 0d;
//		for (PoissonSampler<Integer>.Item item : s.items) {
//			sum += item.rate;
//		}
//		
//		assertEquals("normalized rates don't sum to 1", 1d, sum, 1e-10);
//	}
//	
//	public void doTestCumRates(PoissonSampler<Integer> s) {
//		double cumRate = 0d;
//		double prevCumRate = -1;
//		for (PoissonSampler<Integer>.Item item : s.items) {
//			cumRate += item.rate;
//			assertEquals("cumulative rate is wrong", cumRate, item.cumRate, 1e-10);
//			assertTrue("cumulative rates should always increase or stay the same", item.cumRate >= prevCumRate);
//			prevCumRate = item.cumRate;
//		}
//	}
//	
//	@Test
//	public void testCumRates() {
//		doTestCumRates(new PoissonSampler<Integer>(testItems, testRates));
//		doTestCumRates(new PoissonSampler<Integer>(reversedTestItems, reversedTestRates));
//		doTestCumRates(new PoissonSampler<Integer>(randomizedTestItems, randomizedTestRates));
//	}
//	
//	private void doTestSorting(PoissonSampler<Integer> s) {
//		double prev = Double.MAX_VALUE;
//		for (PoissonSampler<Integer>.Item item : s.items) {
//			assertTrue("rates should be sorted from greatest to least", item.rate <= prev);
//			prev = item.rate;
//		}
//	}
//	
//	@Test
//	public void testSorting() {
//		doTestSorting(new PoissonSampler<Integer>(testItems, testRates));
//		doTestSorting(new PoissonSampler<Integer>(reversedTestItems, reversedTestRates));
//		doTestSorting(new PoissonSampler<Integer>(randomizedTestItems, randomizedTestRates));
//	}
	
	@Test
	public void testSampleOneNonZero() {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Double> rates = new ArrayList<Double>();
		while (items.size() < 1000) {
			items.add(items.size());
			rates.add(0d);
		}
		int nonZeroIndex = new Random().nextInt(rates.size());
		double randomRate = 0;
		while (randomRate == 0)
			randomRate = Math.random();
		rates.set(nonZeroIndex, randomRate);
		
		WeightedSampler<Integer> s = new WeightedSampler<Integer>(items, rates);
		
		List<Integer> series = s.generateSeries(10000);
		for (int val : series)
			assertTrue("non zero one wasnt selected!!!!", val == nonZeroIndex);
	}
	
//	@Test
//	public void testGetForRate() {
//		PoissonSampler<Integer> s = new PoissonSampler<Integer>(testItems, testRates);
//		
//		for (int i=0; i<100000; i++) {
//			double rate = Math.random();
//			PoissonSampler<Integer>.Item item = s.getForCumRate(rate);
//			assertTrue("get for rate incorrect. rate: "+rate+", returned item cum rate: "+item.cumRate,
//					rate <= s.getForCumRate(rate).cumRate);
//			int indexOfPrev = s.items.indexOf(item)-1;
//			if (indexOfPrev >= 0) {
//				PoissonSampler<Integer>.Item prevItem = s.items.get(indexOfPrev);
//				assertTrue("get for rate incorrect...rate should be greater than the" +
//						"cumulative rate of the previous item. rate: "+rate+", prev cum rate: "+prevItem.cumRate,
//						rate >= prevItem.cumRate);
//			}
//		}
//		
//		// test rate of zero
//		assertTrue("get for rate of 0 is incorrect", s.getForCumRate(0d) == s.items.get(0));
//		
//		// test rate of one
//		assertTrue("get for rate of 1 is incorrect", s.getForCumRate(1d) == s.items.get(s.items.size()-1));
//	}

}
