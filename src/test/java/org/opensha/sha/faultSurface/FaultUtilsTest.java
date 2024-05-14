package org.opensha.sha.faultSurface;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;

public class FaultUtilsTest {
	
	private static FaultTrace[] simpleTraces;
	private static FaultTrace[] complexTraces;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		Random r = new Random(123456789l);
		Location baseLoc = new Location(34, -118);
		simpleTraces = new FaultTrace[10];
		for (int n=0; n<simpleTraces.length; n++) {
			simpleTraces[n] = new FaultTrace("simple "+n);
			simpleTraces[n].add(baseLoc);
			Location endLoc = LocationUtils.location(baseLoc, 2d*Math.PI*r.nextDouble(), 100d*r.nextDouble());
			simpleTraces[n].add(endLoc);
		}
		complexTraces = new FaultTrace[10];
		for (int n=0; n<complexTraces.length; n++) {
			complexTraces[n] = new FaultTrace("complex "+n);
			Location loc = baseLoc;
			complexTraces[n].add(loc);
			int num = r.nextInt(20)+1;
			for (int i=0; i<num; i++) {
				double dist = r.nextDouble()*5d;
				double azimuth = 0.1*Math.PI*r.nextDouble();
				loc = LocationUtils.location(loc, azimuth, dist);
				double depth = Math.max(0d, loc.depth + 0.5 - r.nextDouble());
				loc = new Location(loc.lat, loc.lon, depth);
				complexTraces[n].add(loc);
			}
		}
	}

	@Test
	public void testResampleSimpleTraceExpectedNum() {
		for (FaultTrace simpleTrace : simpleTraces)
			doTestResampleTraceExpectedNum(simpleTrace);
	}

	@Test
	public void testResampleComplexTraceExpectedNum() {
		for (FaultTrace complexTrace : complexTraces)
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
		for (FaultTrace simpleTrace : simpleTraces)
			doTestResampleTraceEvenlySpaced(simpleTrace);
	}

	@Test
	public void testResampleComplexTraceEvenlySpaced() {
		for (FaultTrace complexTrace : complexTraces)
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
