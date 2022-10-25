package org.opensha.commons.data.function;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.JsonAdapter;

public class XY_JSONDeserializationTests {
	
	private static Gson gson;
	
	private static XY_DataSet xy;
	private static ArbitrarilyDiscretizedFunc arbDiscFunc;
	private static EvenlyDiscretizedFunc evenDiscFunc;
	
	@BeforeClass
	public static void beforeClass() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		
		arbDiscFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<20; i++)
			arbDiscFunc.set(Math.random(), Math.random());
		
		evenDiscFunc = new EvenlyDiscretizedFunc(0d, 10d, 10);
		for (int i=0; i<evenDiscFunc.size(); i++)
			evenDiscFunc.set(i, Math.random());
		
		xy = new DefaultXY_DataSet();
		for (int i=0; i<20; i++)
			xy.set(Math.random(), Math.random());
	}

	@Test
	public void testSerXY() {
		String json = gson.toJson(xy, XY_DataSet.class);
		
		XY_DataSet recovered = gson.fromJson(json, XY_DataSet.class);
		
		testEquals(xy, recovered, XY_DataSet.class);
	}

	@Test
	public void testSerArb() {
		String json = gson.toJson(arbDiscFunc, ArbitrarilyDiscretizedFunc.class);
		
		ArbitrarilyDiscretizedFunc recovered = gson.fromJson(json, ArbitrarilyDiscretizedFunc.class);
		
		testEquals(arbDiscFunc, recovered, ArbitrarilyDiscretizedFunc.class);
	}

	@Test
	public void testSerEven() {
		String json = gson.toJson(evenDiscFunc, EvenlyDiscretizedFunc.class);
		
		EvenlyDiscretizedFunc recovered = gson.fromJson(json, EvenlyDiscretizedFunc.class);
		
		testEquals(evenDiscFunc, recovered, EvenlyDiscretizedFunc.class);
	}
	
	@Test
	public void testRespectDeserType() {
		// try to deserialize
		String json = gson.toJson(arbDiscFunc, ArbitrarilyDiscretizedFunc.class);
		
		XY_DataSet recovered = gson.fromJson(json, XY_DataSet.class);
		
		testEquals(arbDiscFunc, recovered, ArbitrarilyDiscretizedFunc.class);
	}
	
	@Test
	public void testRespectDeserType2() {
		// try to deserialize
		String json = gson.toJson(evenDiscFunc, EvenlyDiscretizedFunc.class);
		
		XY_DataSet recovered = gson.fromJson(json, XY_DataSet.class);
		
		testEquals(evenDiscFunc, recovered, EvenlyDiscretizedFunc.class);
	}
	
	private static final double tol = 1e-10;
	
	private static void testEquals(XY_DataSet orig, XY_DataSet deser, Class<? extends XY_DataSet> type) {
		assertTrue("Deserialized value is not of the expected type.\nexpected:\t"+type.getName()+"\nhave\t"
				+deser.getClass().getName(), type.isAssignableFrom(deser.getClass()));
		
		assertEquals("size mismatch", orig.size(), deser.size());
		
		for (int i=0; i<orig.size(); i++) {
			assertEquals("x["+i+"] mismatch", orig.getX(i), deser.getX(i), tol);
			assertEquals("y["+i+"] mismatch", orig.getY(i), deser.getY(i), tol);
		}
	}

}
