package org.opensha.commons.data;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

public class WeightedListTest {
	
	private static final double almost_zero = 0.000001d;

	WeightedList<String> testList;
	WeightedList<String> emptyList;
	WeightedList<String> randomList;
	
	private static String getLabel(int i) {
		return "val "+i;
	}
	
	@Before
	public void setUp() {
		emptyList = new WeightedList<String>();
		
		testList = new WeightedList<String>();
		testList.add(getLabel(0), 0.25);
		testList.add(getLabel(1), 0.25);
		testList.add(getLabel(2), 0.25);
		testList.add(getLabel(3), 0.25);
		
		randomList = new WeightedList<String>();
		for (int i=0; i<10; i++) {
			randomList.add(getLabel(i), Math.random());
		}
	}
	
	@Test
	public void testSetConstant() {
		// make sure it does nothing on an empty list
		emptyList.setWeightsToConstant(0.1);
		assertEquals("shouldn't have added anything", 0, emptyList.size());
		
		int listSize = testList.size();
		testList.setWeightsToConstant(0.1);
		assertEquals("shouldn't have added anything", listSize, testList.size());
		for (int i=0; i<testList.size(); i++) {
			assertEquals("set to constant didn't work", 0.1, testList.getWeight(i), almost_zero);
		}
		
		testList.setWeightsToConstant(0.333);
		for (int i=0; i<testList.size(); i++) {
			assertEquals("set to constant didn't work", 0.333, testList.getWeight(i), almost_zero);
		}
		
		testList.setWeightsToConstant(0);
		assertEquals("shouldn't have added anything", listSize, testList.size());
		for (int i=0; i<testList.size(); i++) {
			assertEquals("set to constant didn't work", 0, testList.getWeight(i), almost_zero);
		}
	}
	
	@Test
	public void testSetEqual() {
		assertFalse("areWeightsEqual returns true when they're not equal", randomList.areWeightsEqual());
		
		randomList.setWeightsEqual();
		
		double expected = 1d / (double)randomList.size();
		for (int i=0; i<randomList.size(); i++) {
			assertEquals("set equal didn't work", expected, randomList.getWeight(i), almost_zero);
		}
		
		assertTrue("areWeightsEqual returns false when they're equal", randomList.areWeightsEqual());
		assertTrue("set equal didn't normalize", randomList.isNormalized());
	}
	
	@Test(expected=java.lang.IllegalArgumentException.class)
	public void testSetNullObjects() {
		new WeightedList<String>(null, new ArrayList<Double>());
	}
	
	@Test(expected=java.lang.IllegalArgumentException.class)
	public void testSetNullWeights() {
		new WeightedList<String>(new ArrayList<String>(), null);
	}
	
	@Test(expected=java.lang.IllegalArgumentException.class)
	public void testSetNull() {
		new WeightedList<String>(null, null);
	}
	
	@Test(expected=java.lang.IllegalStateException.class)
	public void testSetUnequal() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("sadfsa");
		new WeightedList<String>(strings, new ArrayList<Double>());
	}
	
	@Test(expected=java.lang.IllegalStateException.class)
	public void testForceNormalizationThrow() {
		testList.setForceNormalization(true);
		testList.setWeight(0, 0);
	}
	
	@Test(expected=java.lang.IllegalStateException.class)
	public void testForceNormalizationThrow2() {
		testList.setForceNormalization(true);
		ArrayList<Double> newWeights = new ArrayList<Double>();
		newWeights.add(0d);
		newWeights.add(0d);
		newWeights.add(0d);
		newWeights.add(100d);
		
		testList.setWeights(newWeights);
	}
	
	@Test
	public void testForceNormalizationCorrect() {
		assertFalse("force should start false", testList.isForceNormalization());
		testList.setForceNormalization(true);
		assertTrue("force should be true", testList.isForceNormalization());
		ArrayList<Double> newWeights = new ArrayList<Double>();
		newWeights.add(0d);
		newWeights.add(0.25d);
		newWeights.add(0.5d);
		newWeights.add(0.25d);
		
		testList.setWeights(newWeights);
		
		assertEquals("setWeights on force normalize didn't work even with correct data", testList.getWeight(0), 0d, almost_zero);
		assertEquals("setWeights on force normalize didn't work even with correct data", testList.getWeight(1), 0.25d, almost_zero);
		assertEquals("setWeights on force normalize didn't work even with correct data", testList.getWeight(2), 0.5d, almost_zero);
		assertEquals("setWeights on force normalize didn't work even with correct data", testList.getWeight(3), 0.25d, almost_zero);
	}
	
	@Test
	public void testForcedWontSetBad() {
		testList.setForceNormalization(true);
		try {
			testList.setWeight(0, 10);
			fail("this should have thrown an exception");
		} catch (IllegalStateException e) {}
		assertTrue("should still be normalized", testList.isNormalized());
		
		int origSize = testList.size();
		// this should work
		testList.add("asfsda", 0);
		assertEquals("size should have increased", origSize+1, testList.size());
		assertTrue("should still be normalized", testList.isNormalized());
		
		origSize = testList.size();
		try {
			testList.add("asdfsd", 0.1);
			fail("this should have thrown an exception");
		} catch (IllegalStateException e) {}
		assertEquals("size should not have increased", origSize, testList.size());
		assertTrue("should still be normalized", testList.isNormalized());
	}
	
	@Test
	public void testNormalize() {
		ArrayList<Double> relativeToFirst = new ArrayList<Double>();
		for (int i=0; i<randomList.size(); i++)
			relativeToFirst.add(randomList.getWeight(i)/randomList.getWeight(0));
		randomList.normalize();
		for (int i=0; i<randomList.size(); i++) {
			double rel = randomList.getWeight(i)/randomList.getWeight(0);
			assertEquals("relative weights should not have changed", relativeToFirst.get(i), rel, almost_zero);
		}
		assertTrue("should be normalized", randomList.isNormalized());
		assertEquals("sum should be 1", 1d, randomList.getWeightSum(), almost_zero);
		
		for (int i=0; i<4; i++)
			testList.setWeight(i, 0.1);
		testList.normalize();
		assertTrue("should be normalized", testList.isNormalized());
		assertTrue("should be equal now", testList.areWeightsEqual());
		
		for (int i=0; i<4; i++)
			assertEquals("normalize didn't work correctly", 0.25d, testList.getWeight(i), almost_zero);
	}
	
	@Test
	public void testGet() {
		for (int i=0; i<testList.size(); i++) {
			assertEquals("get doesn't work!", getLabel(i), testList.get(i));
		}
	}
	
	@Test(expected=java.lang.IllegalStateException.class)
	public void testSetObjectsBad() {
		testList.setObjects(new ArrayList<String>());
	}
	
	@Test
	public void testSetObjectsGood() {
		ArrayList<String> objs = new ArrayList<String>();
		for (int i=0; i<testList.size(); i++)
			objs.add("asdf");
		testList.setObjects(objs);
		for (int i=0; i<testList.size(); i++)
			assertEquals("setObjects didn't set", "asdf", testList.get(i));
	}
	
	@Test(expected=java.lang.IllegalArgumentException.class)
	public void testSetObjectsNull() {
		testList.setObjects(null);
	}
	
	@Test
	public void testGetWeightByObject() {
		for (int i=0; i<randomList.size(); i++) {
			double wt = randomList.getWeight(i);
			double wtByObj = randomList.getWeight(randomList.get(i));
			
			assertEquals("getWeight by object not equal to by index!", wt, wtByObj, almost_zero);
		}
	}
	
	@Test(expected=java.util.NoSuchElementException.class)
	public void testGetWeightNull() {
		testList.getWeight(null);
	}
	
	@Test(expected=java.util.NoSuchElementException.class)
	public void testGetWeightNotInList() {
		testList.getWeight("sdafsdafsdhkfhsfad");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSetBadWeightMax() {
		testList.setWeightValueMax(-1);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSetBadWeightMin() {
		testList.setWeightValueMin(1.1);
	}
	
	@Test
	public void testSetWeightMaxBelowValues() {
		testList.setWeightsToConstant(1.0);
		assertEquals("should have started at 1", 1d, testList.getWeightValueMax(), 0.00001);
		try {
			testList.setWeightValueMax(0.5);
			fail("should have thrown exception");
		} catch (Exception e) {}
		assertEquals("should not have changed with bad set", 1d, testList.getWeightValueMax(), 0.00001);
	}
	
	@Test
	public void testSetWeightMinAboveValues() {
		testList.setWeightsToConstant(0.2);
		assertEquals("should have started at 1", 0d, testList.getWeightValueMin(), 0.00001);
		try {
			testList.setWeightValueMin(0.5);
			fail("should have thrown exception");
		} catch (Exception e) {}
		assertEquals("should not have changed with bad set", 0d, testList.getWeightValueMin(), 0.00001);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSetWeightOutsideRange() {
		testList.setWeight(0, 1.1);
	}

}
