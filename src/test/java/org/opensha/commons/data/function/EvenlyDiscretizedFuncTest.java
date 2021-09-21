package org.opensha.commons.data.function;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.common.base.Preconditions;

public class EvenlyDiscretizedFuncTest extends AbstractDiscretizedFuncTest {

	@Override
	DiscretizedFunc newEmptyDataSet() {
		return new EvenlyDiscretizedFunc(0d, 0, 0d);
	}

	@Override
	DiscretizedFunc newPopulatedDataSet(int num, boolean randomX,
			boolean randomY) {
		Preconditions.checkArgument(!randomX, "randomX is not allowed for evenly discretized functions");
		double min = defaultXValue(0);
		double max = defaultXValue(num-1);
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(min, max, num);
		
		for (int i=0; i<num; i++) {
			if (randomY)
				func.set(i, Math.random());
			else
				func.set(i, defaultYValue(i));
		}
		
		return func;
	}

	@Override
	boolean isArbitrarilyDiscretized() {
		return false;
	}
	
	@Test
	public void testGetXIndexAndGetClosestXIndex() {
		
		// added to test evenly discretized funtion binning behavior;
		// implementation of these methods was recently changed to better
		// handle unexpected results stemming from double precision
		// rounding errors
		
		double[] testVals = { 5.02, 6, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 7, 7.54, 6.499999999999999, 6.49999999999999 };
		int num;
		double minX, delta;
		int[] ciExpect, iExpect, ciActual, iActual;
		EvenlyDiscretizedFunc f;
		
		// run two basic data sets, one where target values are falling on bin
		// edges, and another with more arbitrarily spaced bins
		// function 1 : bin edge test
		minX=6.05;
		delta = 0.1;
		num = 10;
		 ciExpect = new int[] {0,0,1,2,3,4,5,6,7,8,9,9,9,5,4};
		 iExpect = new int[] {-1,0,1,2,3,4,5,6,7,8,9,9,9,5,4};
		ciActual = new int[testVals.length];
		iActual = new int[testVals.length];
		f = new EvenlyDiscretizedFunc(minX, num, delta);
		f.setTolerance(1.0);
		for(int i=0;i<testVals.length; i++) {
			double x = testVals[i];
			ciActual[i] = f.getClosestXIndex(x);
			iActual[i] = f.getXIndex(x);
		}
		assertArrayEquals(ciExpect, ciActual);
		assertArrayEquals(iExpect, iActual);

		// function 2 : more arbitrary delta
		minX=5.23;
		 delta = 0.42;
		 num = 6;
			ciExpect = new int[] {0,2,2,2,3,3,3,3,4,4,4,4,5,3,3};
			iExpect = new int[] {0,2,2,2,3,3,3,3,4,4,4,4,5,3,3};
		ciActual = new int[testVals.length];
		iActual = new int[testVals.length];
		f = new EvenlyDiscretizedFunc(minX, num, delta);
		f.setTolerance(1.0);
		for(int i=0;i<testVals.length; i++) {
			double x = testVals[i];
			ciActual[i] = f.getClosestXIndex(x);
			iActual[i] = f.getXIndex(x);
		}
		assertArrayEquals(ciExpect, ciActual);
		assertArrayEquals(iExpect, iActual);

		// function 3: above case again but with lower tolerance
		minX=5.23;
		 delta = 0.42;
		 num = 6;
			iExpect = new int[] {-1,2,2,2,-1,3,3,3,-1,4,4,4,-1,3,3};
		ciActual = new int[testVals.length];
		iActual = new int[testVals.length];
		f = new EvenlyDiscretizedFunc(minX, num, delta);
		f.setTolerance(0.15);
		for(int i=0;i<testVals.length; i++) {
			double x = testVals[i];
			ciActual[i] = f.getClosestXIndex(x);
			iActual[i] = f.getXIndex(x);
		}
		assertArrayEquals(ciExpect, ciActual);
		assertArrayEquals(iExpect, iActual);

		// function 4 : test where delta=0
		f = new EvenlyDiscretizedFunc(5.0, 1, 0.0);
		f.setTolerance(1.0);
		double expect = 0;
		assertTrue(expect == f.getClosestXIndex(5.0));
	}

	@Test
	public void setYofXTest() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0.0, 9.0, 10);
		for (int i = 0; i < func.size(); i++) {
			func.set(i, i);
		}
		assertEquals(List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0), func.yValues());

		func.setYofX(x -> 2 * x);
		assertEquals(List.of(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0), func.yValues());

		func.setYofX((x, y) -> y - x);
		assertEquals(List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0), func.yValues());
	}

}
