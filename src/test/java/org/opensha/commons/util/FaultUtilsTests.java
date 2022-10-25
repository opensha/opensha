package org.opensha.commons.util;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FaultUtils;



public class FaultUtilsTests {

	public FaultUtilsTests() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test(expected=InvalidRangeException.class)
	public void testAssertValidStrike() {
		double strike1=  -1.0;
		FaultUtils.assertValidStrike(strike1);
	}
	
	@Test
	public void testLengthBasedAngleAverage() {
		ArrayList<Double> scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(1d);
		
		ArrayList<Double> angles = new ArrayList<Double>();
		angles.add(-175d);
		angles.add(175d);
		
		double avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertEquals(180d, avg, 0.1d);
		
		scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(1d);
		
		angles = new ArrayList<Double>();
		angles.add(-10d);
		angles.add(10d);
		
		avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertEquals(0d, avg, 0.1d);
		
		scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(2d);
		
		angles = new ArrayList<Double>();
		angles.add(90d);
		angles.add(180d);
		
		avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertTrue(avg > 135 && avg < 180);
	}
}
