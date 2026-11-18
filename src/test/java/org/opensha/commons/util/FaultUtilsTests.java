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
	
	@Test
	public void testResampleHairpinRetainsEndpoint() {
		FaultTrace trace = new FaultTrace("hairpin");
		trace.add(new Location(0, 0));
		trace.add(new Location(0, 0.11));
		trace.add(new Location(0, 0.082));
		// For num=3, the last arc-length interval turns back so its chord is
		// shorter than half an interval. For num=1 the entire trace does so.
		for (int num : new int[] {1, 3, 10, 100}) {
			FaultTrace sampled = FaultUtils.resampleTrace(trace, num);
			assertEquals(num+1, sampled.size());
			assertSame(trace.first(), sampled.first());
			assertSame(trace.last(), sampled.last());
		}
		FaultTrace thirds = FaultUtils.resampleTrace(trace, 3);
		assertEquals(0.046, thirds.get(1).getLongitude(), 1e-10);
		assertEquals(0.092, thirds.get(2).getLongitude(), 1e-10);
	}

	@Test
	public void testResampleIndianHillLowerTrace() {
		FaultTrace trace = new FaultTrace("Indian Hill, Subsection 2 lower");
		trace.add(new Location(39.04650353721698, -119.7506503711397, 5.973870399464156));
		trace.add(new Location(39.048166295546466, -119.74875415260016, 6.091210054065506));
		trace.add(new Location(39.060218319100564, -119.72457265710096, 8.4449140625));
		trace.add(new Location(39.05877333632404, -119.73220800046131, 8.4449140625));
		FaultTrace sampled = FaultUtils.resampleTrace(trace, 3);
		assertEquals(4, sampled.size());
		assertSame(trace.last(), sampled.last());
		assertTrue(sampled.get(1).getDepth() > trace.first().getDepth());
		assertTrue(sampled.get(1).getDepth() < trace.last().getDepth());
	}

	@Test
	public void testResamplePortlandHillsLowerTrace() {
		FaultTrace trace = new FaultTrace("Portland Hills (south), Subsection 0 lower");
		trace.add(new Location(45.512101639223026, -122.70301287958166, 4.3687353515625));
		trace.add(new Location(45.50586225234113, -122.70034133241876, 4.3687353515625));
		trace.add(new Location(45.50855871374699, -122.7074110863874, 4.3687353515625));
		FaultTrace sampled = FaultUtils.resampleTrace(trace, 1);
		assertEquals(2, sampled.size());
		assertSame(trace.first(), sampled.first());
		assertSame(trace.last(), sampled.last());
	}

	@Test
	public void testResampleExactEndpointAndRepeatedVertices() {
		FaultTrace trace = new FaultTrace("repeated vertices");
		trace.add(new Location(0, 0, 1));
		trace.add(trace.first());
		trace.add(new Location(0, 0.05, 2));
		trace.add(new Location(0, 0.1, 3));
		trace.add(trace.last());
		for (int num : new int[] {1, 2, 3, 100}) {
			FaultTrace sampled = FaultUtils.resampleTrace(trace, num);
			assertEquals(num+1, sampled.size());
			assertSame(trace.first(), sampled.first());
			assertSame(trace.last(), sampled.last());
		}
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
