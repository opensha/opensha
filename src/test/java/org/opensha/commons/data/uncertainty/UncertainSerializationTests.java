package org.opensha.commons.data.uncertainty;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class UncertainSerializationTests {
	
	private static Gson gson;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		gson = new GsonBuilder().setPrettyPrinting().create();
	}

	@Test
	public void testUncertainArbDiscr() {
		ArbitrarilyDiscretizedFunc origFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<20; i++)
			origFunc.set(Math.random(), Math.random());
		
		ArbitrarilyDiscretizedFunc stdDevs = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<origFunc.size(); i++)
			stdDevs.set(origFunc.getX(i), Math.random());
		
		ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<origFunc.size(); i++) {
			double x = origFunc.getX(i);
			double y = origFunc.getY(i);
			double stdDev = stdDevs.getY(i);
			upperFunc.set(x, y+2*stdDev);
			lowerFunc.set(x, y-2*stdDev);
		}
		
		UncertainArbDiscFunc func = new UncertainArbDiscFunc(origFunc, lowerFunc, upperFunc);
		test(func);
		
		func = new UncertainArbDiscFunc(origFunc, lowerFunc, upperFunc, UncertaintyBoundType.TWO_SIGMA);
		test(func);		

		func = new UncertainArbDiscFunc(origFunc, lowerFunc, upperFunc, UncertaintyBoundType.TWO_SIGMA, stdDevs);
		test(func);
		
		func = new UncertainArbDiscFunc(origFunc, lowerFunc, upperFunc, null, stdDevs);
		test(func);
	}

	@Test
	public void testUncertainIncrMFD() {
		IncrementalMagFreqDist origFunc = new IncrementalMagFreqDist(0d, 5d, 20);
		for (int i=0; i<origFunc.size(); i++)
			origFunc.set(i, Math.random());
		
		UncertainIncrMagFreqDist func = UncertainIncrMagFreqDist.relStdDev(origFunc, M->Math.random());
		test(func);
		
		test(func.estimateBounds(UncertaintyBoundType.TWO_SIGMA));
	}
	
	private static final double tol = 1e-10;
	
	private void test(UncertainDiscretizedFunc func) {
		String json = gson.toJson(func);
		
		UncertainDiscretizedFunc deser = gson.fromJson(json, func.getClass());
		
		assertEquals("Different class after serialization", func.getClass(), deser.getClass());
		
		// check standard deviations
		DiscretizedFunc stdDevs = func.getStdDevs();
		DiscretizedFunc deserStdDevs = deser.getStdDevs();
		testFuncValues(stdDevs, deserStdDevs, "Std. Devs");
		
		if (func instanceof UncertainBoundedDiscretizedFunc) {
			UncertainBoundedDiscretizedFunc origBounded = (UncertainBoundedDiscretizedFunc)func;
			UncertainBoundedDiscretizedFunc deserBounded = (UncertainBoundedDiscretizedFunc)deser;
			
			testFuncValues(origBounded.getLower(), deserBounded.getLower(), "lower bound");
			testFuncValues(origBounded.getUpper(), deserBounded.getUpper(), "upper bound");
			
			UncertaintyBoundType type = origBounded.getBoundType();
			if (type == null) {
				assertNull(deserBounded.getBoundType());
			} else {
				assertNotNull(deserBounded.getBoundType());
				assertEquals(type, deserBounded.getBoundType());
			}
		}
	}
	
	private static void testFuncValues(DiscretizedFunc func1, DiscretizedFunc func2, String name) {
		if (func1 == null) {
			assertNull(name+" is null before serialization, non-null after", func2);
			return;
		}
		assertNotNull(name+" is null after serialization, non-null before", func1);
		assertEquals(name+" is of different size", func1.size(), func2.size());
		for (int i=0; i<func1.size(); i++) {
			assertEquals(name+" x-mismatch at "+i, func1.getX(i), func2.getX(i), tol);
			assertEquals(name+" y-mismatch at "+i, func1.getY(i), func2.getY(i), tol);
		}
	}

}
