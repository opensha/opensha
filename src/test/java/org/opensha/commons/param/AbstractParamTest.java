package org.opensha.commons.param;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;

public class AbstractParamTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	@Test
	public final void testEqualsObject() {
		Parameter<?> bp1 = new BooleanParameter("testBoolParam", true);
		// null passed in
		Parameter<?> bp2 = null;
		assertFalse(bp1.equals(bp2));
		// same object
		bp2 = bp1;
		assertTrue(bp1.equals(bp2));
		// wrong object type
		assertFalse(bp1.equals(new Object()));
		// wrong param class
		bp2 = new DoubleParameter("testDoubleParam", 1.0);
		assertFalse(bp1.equals(bp2));
		// wrong name
		bp2 = new BooleanParameter("testBoolParam2", true);
		assertFalse(bp1.equals(bp2));
		// wrong value
		bp2 = new BooleanParameter("testBoolParam", false);
		assertFalse(bp1.equals(bp2));
		// correct match
		bp2 = new BooleanParameter("testBoolParam", true);
		assertTrue(bp1.equals(bp2));
		
		// test null values
		Double d = null;
		Parameter<Double> p1 = new DoubleParameter("test", d);
		Parameter<Double> p2 = new DoubleParameter("test", d);
		assertTrue(p1.equals(p2));
		p2.setValue(1.0);
		assertFalse(p1.equals(p2));
		p1.setValue(1.0);
		assertTrue(p1.equals(p2));
	}

	@Test
	public final void testCompareTo() {
		Parameter<?> p1 = new BooleanParameter("F", true);
		Parameter<?> p2 = new DoubleParameter("a", 1.0);
		Parameter<?> p3 = new DoubleParameter("0", 1.0);
		List<Parameter<?>> list = new ArrayList<Parameter<?>>();
		list.add(p2);
		list.add(p1);
		list.add(p3);
		// sort list to exercise compareTo; p1.compareTo(p2) throws compile
		// errors because of parameterized-type mismatches that are masked
		// by sorting post-compile
		Collections.sort(list);
		assertTrue(list.get(0).equals(p3));
		assertTrue(list.get(1).equals(p2));
		assertTrue(list.get(2).equals(p1));
	}

}
