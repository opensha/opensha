package org.opensha.commons.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.FaultTrace;



public class FaultUtilsTests {

	public FaultUtilsTests() {
	}
	
	private static FaultTrace[] simpleFixedDepthTraces;
	private static FaultTrace[] simpleVariableDepthTraces;
	private static FaultTrace[] complexFixedDepthTraces;
	private static FaultTrace[] complexVariableDepthTraces;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		Random r = new Random(123456789l);
		Location baseLoc = new Location(34, -118);
		simpleFixedDepthTraces = new FaultTrace[10];
		simpleVariableDepthTraces = new FaultTrace[10];
		for (int n=0; n<simpleFixedDepthTraces.length; n++) {
			simpleFixedDepthTraces[n] = new FaultTrace("simple fixed depth "+n);
			simpleFixedDepthTraces[n].add(baseLoc);
			Location endLoc = LocationUtils.location(baseLoc, 2d*Math.PI*r.nextDouble(), 100d*r.nextDouble());
			simpleFixedDepthTraces[n].add(endLoc);
			simpleVariableDepthTraces[n] = new FaultTrace("simple variable depth "+n);
			simpleVariableDepthTraces[n].add(new Location(baseLoc.lat, baseLoc.lon, r.nextDouble()*5d));
			simpleVariableDepthTraces[n].add(new Location(endLoc.lat, endLoc.lon, r.nextDouble()*5d));
		}
		complexVariableDepthTraces = new FaultTrace[10];
		complexFixedDepthTraces = new FaultTrace[10];
		for (int n=0; n<complexVariableDepthTraces.length; n++) {
			complexVariableDepthTraces[n] = new FaultTrace("complex variable depth "+n);
			complexFixedDepthTraces[n] = new FaultTrace("complex fixed depth "+n);
			Location loc = baseLoc;
			double fixedDepth = r.nextDouble()*5;
			loc = new Location(loc.lat, loc.lon, fixedDepth);
			complexVariableDepthTraces[n].add(loc);
			complexFixedDepthTraces[n].add(loc);
			int num = r.nextInt(20)+1;
			for (int i=0; i<num; i++) {
				double dist = r.nextDouble()*5d;
				double azimuth = 0.1*Math.PI*r.nextDouble();
				loc = LocationUtils.location(loc, azimuth, dist);
				double varDepth = Math.max(0d, loc.depth + 0.5 - r.nextDouble());
				loc = new Location(loc.lat, loc.lon, varDepth);
				complexVariableDepthTraces[n].add(loc);
				complexFixedDepthTraces[n].add(new Location(loc.lat, loc.lon, fixedDepth));
			}
		}
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
	
	@Test
	public void testGetAbsAngleDiff() {
		double tol = 0.0001;
		List<double[]> tests = new ArrayList<>();
		
		// angle1, angle2, expected diff
		// tests around zero
		tests.add(new double[] {0, 0, 0});
		tests.add(new double[] {0, -5, 5});
		tests.add(new double[] {0, 5, 5});
		// tests around -180/180 cut
		tests.add(new double[] {-180d, 180d, 0});
		tests.add(new double[] {-190d, -180d, 10});
		tests.add(new double[] {185d, -175d, 0});
		// tests around 0/360 cut
		tests.add(new double[] {0d, 360d, 0});
		tests.add(new double[] {5d, 355d, 10});
		tests.add(new double[] {5d, 365d, 0});
		tests.add(new double[] {0d, 365d, 5});
		
		for (double[] test : tests) {
			double angle1 = test[0];
			double angle2 = test[1];
			double expected = test[2];
			double diff = FaultUtils.getAbsAngleDiff(angle1, angle2);
			assertEquals("Bad getAbsAngleDiff for angle1="+(float)angle1+" and angle2="+(float)angle2,
					expected, diff, tol);
		}
	}

	@Test
	public void testResampleSimpleTraceExpectedNum() {
		for (FaultTrace simpleTrace : simpleFixedDepthTraces)
			doTestResampleTraceExpectedNum(simpleTrace);
	}

	@Test
	public void testResampleSimpleVariableDepthTraceExpectedNum() {
		for (FaultTrace simpleTrace : simpleVariableDepthTraces)
			doTestResampleTraceExpectedNum(simpleTrace);
	}

	@Test
	public void testResampleComplexFixedDepthTraceExpectedNum() {
		for (FaultTrace complexTrace : complexFixedDepthTraces)
			doTestResampleTraceExpectedNum(complexTrace);
	}

	@Test
	public void testResampleComplexVariableDepthTraceExpectedNum() {
		for (FaultTrace complexTrace : complexVariableDepthTraces)
			doTestResampleTraceExpectedNum(complexTrace);
	}
	
	private void doTestResampleTraceExpectedNum(FaultTrace trace) {
		for (int num=1; num<100; num++) {
			FaultTrace resampled = FaultUtils.resampleTrace(trace, num);
			assertEquals("FaultUtils.resampleTrace() returned an unexpected size for the "+trace.getName()+" trace",
					num+1, resampled.size());
		}
	}

	@Test
	public void testResampleSimpleTraceEvenlySpaced() {
		for (FaultTrace simpleTrace : simpleFixedDepthTraces)
			doTestResampleTraceEvenlySpaced(simpleTrace);
	}

	@Test
	public void testResampleSimpleVariableDepthTraceEvenlySpaced() {
		for (FaultTrace simpleTrace : simpleVariableDepthTraces)
			doTestResampleTraceEvenlySpaced(simpleTrace);
	}

	@Test
	public void testResampleComplexFixedDepthTraceEvenlySpaced() {
		for (FaultTrace complexTrace : complexFixedDepthTraces)
			doTestResampleTraceEvenlySpaced(complexTrace);
	}

	@Test
	public void testResampleComplexVariableDepthTraceEvenlySpaced() {
		for (FaultTrace complexTrace : complexVariableDepthTraces)
			doTestResampleTraceEvenlySpaced(complexTrace);
	}
	
	private void doTestResampleTraceEvenlySpaced(FaultTrace trace) {
		for (int num=1; num<100; num++) {
			FaultTrace resampled = FaultUtils.resampleTrace(trace, num);
			double firstSpacing = LocationUtils.horzDistance(resampled.get(0), resampled.get(1));
			double tol = firstSpacing*0.01;
			for (int i=1; i<resampled.size(); i++) {
				double spacing = LocationUtils.horzDistance(resampled.get(i-1), resampled.get(i));
				assertEquals("FaultUtils.resampleTrace() returned an non-even spacing for the "
						+trace.getName()+" trace, span "+(i+1)+"/"+trace.size(), firstSpacing, spacing, tol);
			}
		}
	}
}
