package org.opensha.commons.data.function;

import static org.junit.Assert.*;

import org.junit.Test;

public abstract class AbstractDiscretizedFuncTest extends
		AbstractXY_DataSetTest {
	
	/* redefined here to require them to be DiscretizedFunc instnaces */
	
	abstract DiscretizedFunc newEmptyDataSet();
	
	@Override
	DiscretizedFunc newPopulatedDataSet(int num, boolean randomX, boolean randomY) {
		return (DiscretizedFunc)super.newPopulatedDataSet(num, randomX, randomY);
	}
	
	@Override
	DiscretizedFunc newQuickTestDataSet() {
		return (DiscretizedFunc)super.newQuickTestDataSet();
	}
	
	@Test
	public void testPopulatedGetByX() {
		int num = 100;
		DiscretizedFunc xy = newPopulatedDataSet(num, isArbitrarilyDiscretized(), true);
		
		for (int i=0; i<num; i++) {
			double x = xy.getX(i);
			double yByIndex = xy.getY(i);
			double yByX = xy.getY(x);
			assertEquals("getY different by index vs by x", yByIndex, yByX, test_tol);
		}
	}

	@Test
	public void testPopulatedGetByXOutOfBounds() {
		int num = 100;
		DiscretizedFunc xy = newPopulatedDataSet(num, isArbitrarilyDiscretized(), true);
		
		for (int i=0; i<num; i++) {
			double x = xy.getX(i);
			double yByIndex = xy.getY(i);
			double yByX = xy.getY(x);
			assertEquals("getY different by index vs by x", yByIndex, yByX, test_tol);
		}
	}
	
	@Test
	public void testSetTolerance() {
		if (isArbitrarilyDiscretized())
			return; // see ticket #341
		int num = 10;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		for (int testI=0; testI<10; testI++) {
			double val = Math.random() * 0.49;
			func.setTolerance(val);
			assertEquals("setTolerance didn't change it!", val, func.getTolerance(), test_tol);
			
			for (int i=0; i<num; i++) {
				try {
					double x = func.getX(i);
					double y = func.getY(i);
					
					val -= 1e-10; // to avoid double precision errors
					
					// test getting within tolerance
					assertEquals("getY(x) is off when x is within tolerance but different", y, func.getY(x+val), test_tol);
					assertEquals("getY(x) is off when x is within tolerance but different", y, func.getY(x-val), test_tol);
					
					// test get x ind within tolerance
					assertEquals("getXIndex problem...", i, func.getXIndex(func.getX(i)));
					assertEquals("getXIndex should work within tolerance", i, func.getXIndex(func.getX(i)+val));
					assertEquals("getXIndex should work within tolerance", i, func.getXIndex(func.getX(i)-val));
					
					// test setting within tolerance
					double testYVal = randomNotEqualTo(y);
					func.set(x+val, testYVal);
					assertEquals("y wasn't set even though x within tolerance", testYVal, func.getY(i), test_tol);
					testYVal = randomNotEqualTo(testYVal);
					func.set(x-val, testYVal);
					assertEquals("y wasn't set even though x within tolerance", testYVal, func.getY(i), test_tol);
				} catch (RuntimeException e) {
					throw e;
//					fail("Error getting/setting within tolerance: "+e);
				}
			}
		}
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetFirstInterpolatedXBelowMin() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getFirstInterpolatedX(func.getMinY()-0.1);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetFirstInterpolatedXBelowMax() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getFirstInterpolatedX(func.getMaxY()+0.1);
	}
	
	@Test
	public void testGetFirstInterpolatedX() {
		int num = 5;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 9, 16]
		
		// simplest case, actual y value
		for (int i=0; i<num; i++)
			assertEquals("getFirstInterpolatedX should match x exactly at points", func.getX(i),
					func.getFirstInterpolatedX(func.getY(i)), test_tol);
		
		// test middle vals
		for (int i=1; i<num; i++) {
			double midX = (func.getX(i) + func.getX(i-1)) * 0.5;
			double midY = (func.getY(i) + func.getY(i-1)) * 0.5;
			assertEquals("getFirstInterpolatedX is wrong at mid point. midX="+midX+", midY="+midY, midX,
					func.getFirstInterpolatedX(midY), test_tol);
		}
		
		// test just over vals
		for (int i=1; i<num; i++) {
			double midX = func.getX(i)*0.1 + func.getX(i-1)*0.9;
			double midY = func.getY(i)*0.1 + func.getY(i-1)*0.9;
			assertEquals("getFirstInterpolatedX is wrong at mid point. midX="+midX+", midY="+midY, midX,
					func.getFirstInterpolatedX(midY), test_tol);
		}
		
		func.set(3, 1d);
		func.set(4, 4d);
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 1, 4]
		
		// now make sure that the first one is returned!
		assertEquals("getFirstInterpolatedX should match x exactly at points", 0,
				func.getFirstInterpolatedX(0), test_tol);
		assertEquals("getFirstInterpolatedX should match x exactly at points", 0.5,
				func.getFirstInterpolatedX(0.5), test_tol);
		assertEquals("getFirstInterpolatedX should match x exactly at points", 1,
				func.getFirstInterpolatedX(1), test_tol);
		assertEquals("getFirstInterpolatedX should match x exactly at points", 2,
				func.getFirstInterpolatedX(4), test_tol);
		assertEquals("getFirstInterpolatedX should match x exactly at points", 1.5,
				func.getFirstInterpolatedX(2.5), test_tol);
		assertTrue("getFirstInterpolatedX should match x exactly at points", func.getFirstInterpolatedX(3.9) < 2);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetFirstInterpolatedXBelowMin_inLogXLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getFirstInterpolatedX_inLogXLogYDomain(func.getMinY()-0.1);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetFirstInterpolatedXBelowMax_inLogXLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getFirstInterpolatedX_inLogXLogYDomain(func.getMaxY()+0.1);
	}
	
	@Test
	public void testGetFirstInterpolatedX_inLogXLogYDomain() {
		int num = 6;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 9, 16]
		
		// simplest case, actual y value
		for (int i=0; i<num; i++) {
			if (i == 0 || i == 1)
				assertTrue("should be NaN", Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(func.getY(i))));
			else
				assertEquals("getFirstInterpolatedX should match x exactly at points", func.getX(i),
					func.getFirstInterpolatedX_inLogXLogYDomain(func.getY(i)), test_tol);
		}
		
		// test middle vals
		for (int i=1; i<num; i++) {
			double midX = Math.exp((Math.log(func.getX(i)) + Math.log(func.getX(i-1))) * 0.5);
			double midY = Math.exp((Math.log(func.getY(i)) + Math.log(func.getY(i-1))) * 0.5);
			if (i == 1)
				assertTrue("should be NaN!", Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(midY)));
			else
				assertEquals("getFirstInterpolatedX is wrong at mid point. midX="+midX+", midY="+midY, midX,
					func.getFirstInterpolatedX_inLogXLogYDomain(midY), test_tol);
		}
		
		// test just over vals
		for (int i=1; i<num; i++) {
			double testY = func.getY(i)*0.1 + func.getY(i-1)*0.9;
			double x1 = func.getX(i-1);
			double x2 = func.getX(i);
			if (i == 1)
				assertTrue("should be NaN!", Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(testY)));
			else {
				double val = func.getFirstInterpolatedX_inLogXLogYDomain(testY);
				assertTrue("getFirstInterpolatedX is wrong at just over point.", val > x1 && val < x2);
			}
		}
		
		func.set(3, 1d);
		func.set(4, 4d);
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 1, 4]
		
		// now make sure that the first one is returned!
		assertTrue("getFirstInterpolatedX should match x exactly at points",
				Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(0)));
		assertTrue("getFirstInterpolatedX should match x exactly at points",
				Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(0.5)));
		assertTrue("getFirstInterpolatedX should match x exactly at points",
				Double.isNaN(func.getFirstInterpolatedX_inLogXLogYDomain(1)));
		assertEquals("getFirstInterpolatedX should match x exactly at points", 2,
				func.getFirstInterpolatedX_inLogXLogYDomain(4), test_tol);
		assertTrue("getFirstInterpolatedX should match x exactly at points", func.getFirstInterpolatedX(2.5) > 1);
		assertTrue("getFirstInterpolatedX should match x exactly at points", func.getFirstInterpolatedX(2.5) < 2);
		assertTrue("getFirstInterpolatedX should match x exactly at points", func.getFirstInterpolatedX(3.9) < 2);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYBelowMin() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY(func.getMinX()-0.1);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYAboveMax() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY(func.getMaxX()+0.1);
	}
	
	@Test
	public void testGetInterpolatedY() {
		int num = 6;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 9, 16]
		
		// test discrete values
		for (int i=0; i<num; i++) {
			assertEquals("getInterpolatedY should match y exactly at points", func.getY(i),
					func.getInterpolatedY(func.getX(i)), test_tol);
		}
		
		// test inbetween values
		for (int i=1; i<num; i++) {
			double midX = (func.getX(i) + func.getX(i-1))*0.5;
			double midY = (func.getY(i) + func.getY(i-1))*0.5;
			assertEquals("getInterpolatedY should match y exactly at points", midY,
					func.getInterpolatedY(midX), test_tol);
		}
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYBelowMin_inLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY_inLogYDomain(func.getMinX()-0.1);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYAboveMax_inLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY_inLogYDomain(func.getMaxX()+0.1);
	}
	
	@Test
	public void testGetInterpolatedY_inLogYDomain() {
		int num = 5;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 9, 16]
		
		// test discrete values
		for (int i=2; i<num; i++) {
			assertEquals("getInterpolatedY should match y exactly at points", func.getY(i),
					func.getInterpolatedY_inLogYDomain(func.getX(i)), test_tol);
		}
		
		// test inbetween values
		for (int i=2; i<num; i++) {
//			double midX = Math.exp((Math.log(func.getX(i)) + Math.log(func.getX(i-1)))*0.5);
			double midX = (func.getX(i) + func.getX(i-1))*0.5;
//			double midY = (func.getY(i) + func.getY(i-1))*0.5;
			double midY = Math.exp((Math.log(func.getY(i)) + Math.log(func.getY(i-1)))*0.5);
			assertEquals("getInterpolatedY should match y exactly at points", midY,
					func.getInterpolatedY_inLogYDomain(midX), test_tol);
		}
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYBelowMin_inLogXLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY_inLogXLogYDomain(func.getMinX()-0.1);
	}
	
	@Test (expected=RuntimeException.class)
	public void testGetInterpolatedYAboveMax_inLogXLogYDomain() {
		DiscretizedFunc func = newPopulatedDataSet(10, false, true);
		func.getInterpolatedY_inLogXLogYDomain(func.getMaxX()+0.1);
	}
	
	@Test
	public void testGetInterpolatedY_inLogXLogYDomain() {
		int num = 5;
		DiscretizedFunc func = newPopulatedDataSet(num, false, false);
		
		// x data: [0, 1, 2, 3, 4]
		// y data: [0, 1, 4, 9, 16]
		
		// test discrete values
		for (int i=2; i<num; i++) {
			assertEquals("getInterpolatedY should match y exactly at points", func.getY(i),
					func.getInterpolatedY_inLogXLogYDomain(func.getX(i)), test_tol);
		}
		
		// test inbetween values
		for (int i=2; i<num; i++) {
			double midX = Math.exp((Math.log(func.getX(i)) + Math.log(func.getX(i-1)))*0.5);
//			double midX = (func.getX(i) + func.getX(i-1))*0.5;
//			double midY = (func.getY(i) + func.getY(i-1))*0.5;
			double midY = Math.exp((Math.log(func.getY(i)) + Math.log(func.getY(i-1)))*0.5);
			assertEquals("getInterpolatedY should match y exactly at points", midY,
					func.getInterpolatedY_inLogXLogYDomain(midX), test_tol);
		}
	}
	
	@Test
	public void testGetXIndex() {
		int num = 100;
		DiscretizedFunc func = newPopulatedDataSet(num, isArbitrarilyDiscretized(), false);
		
		// test below
		assertEquals("should be -1 for below min", -1, func.getXIndex(func.getMinX()-1d));
		// test above
		assertEquals("should be -1 for above max", -1, func.getXIndex(func.getMaxX()+1d));
		
		for (int i=0; i<num; i++) {
			assertEquals("getXIndex problem...", i, func.getXIndex(func.getX(i)));
			assertEquals("should be -1 for near misses", -1, func.getXIndex(func.getX(i)+0.2));
			assertEquals("should be -1 for near misses", -1, func.getXIndex(func.getX(i)-0.2));
		}
	}
	
	private static void doTestScale(DiscretizedFunc func, double val) {
		DiscretizedFunc cloned = func.deepClone();
		cloned.scale(val);
		for (int i=0; i<func.size(); i++) {
			double origY = func.getY(i);
			double expected = origY * val;
			assertEquals("scale didn't work (should be "+origY+"*"+val+"="+expected+")", expected, cloned.getY(i), test_tol);
		}
	}
	
	@Test
	public void testScale() {
		DiscretizedFunc func = newPopulatedDataSet(100, false, false);
		
		doTestScale(func, 0);
		doTestScale(func, Math.random());
		
		func = newPopulatedDataSet(100, false, true);
		
		doTestScale(func, 0);
		doTestScale(func, Math.random());
	}

}
