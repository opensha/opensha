package org.opensha.sha.faultSurface.cache;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.threads.Task;
import org.opensha.commons.util.threads.ThreadedTaskComputer;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import util.TestUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

@RunWith(Parameterized.class)
public class TestSurfaceDistanceCaches {
	
	private static final Location traceLoc1 = new Location(35, -118);
	private static final Location traceLoc2 = new Location(35, -118.1);
	private static final Location traceLoc3 = new Location(35, -118.2);
	
	private static final int num_threads_normal = 4;
	private static final int num_threads_extra = 8;
	
	private static final double delta = 1e-10;
	
	private static SimpleFaultData buildSFD() {
		FaultTrace trace = new FaultTrace("");
		trace.add(traceLoc1);
		trace.add(traceLoc2);
		
		return new SimpleFaultData(60d, 10d, 2d, trace);
	}
	
	private static AbstractEvenlyGriddedSurface buildGriddedSurf() {
		return new StirlingGriddedSurface(buildSFD(), 1d);
	}
	
	private static QuadSurface buildQuadSurf() {
		SimpleFaultData sfd = buildSFD();
		return new QuadSurface(sfd.getFaultTrace(), sfd.getAveDip(),
				sfd.getLowerSeismogenicDepth()-sfd.getUpperSeismogenicDepth());
	}
	
	private static CompoundSurface buildCompoundSurf() {
		List<RuptureSurface> surfs = Lists.newArrayList();
		
		surfs.add(new StirlingGriddedSurface(buildSFD(), 1d));
		SimpleFaultData sfd2 = buildSFD();
		FaultTrace trace = new FaultTrace("");
		trace.add(traceLoc2);
		trace.add(traceLoc3);
		sfd2.setFaultTrace(trace);
		surfs.add(new StirlingGriddedSurface(sfd2, 1d));
		
		return new CompoundSurface(surfs);
	}
	
	private static CacheEnabledSurface[] buildSurfs() {
		return new CacheEnabledSurface[] { buildGriddedSurf(), buildQuadSurf(), buildCompoundSurf() };
	}
	
	@Parameters
	public static Collection<CacheEnabledSurface[]> data() throws IOException {
		List<CacheEnabledSurface[]> surfs = Lists.newArrayList();
		
		// single valued cache
		System.setProperty(SurfaceCachingPolicy.SIZE_PROP, "1");
		System.setProperty(SurfaceCachingPolicy.FORCE_TYPE, SurfaceCachingPolicy.CacheTypes.SINGLE.name());
		SurfaceCachingPolicy.loadConfigFromProps();
		surfs.add(buildSurfs());
		
		// multi cache
		System.setProperty(SurfaceCachingPolicy.SIZE_PROP, num_threads_normal+"");
		System.setProperty(SurfaceCachingPolicy.FORCE_TYPE, SurfaceCachingPolicy.CacheTypes.MULTI.name());
		SurfaceCachingPolicy.loadConfigFromProps();
		surfs.add(buildSurfs());
		
		// hybrid cache
		System.setProperty(SurfaceCachingPolicy.SIZE_PROP, num_threads_normal+"");
		System.setProperty(SurfaceCachingPolicy.FORCE_TYPE, SurfaceCachingPolicy.CacheTypes.HYBRID.name());
		SurfaceCachingPolicy.loadConfigFromProps();
		surfs.add(buildSurfs());
		
		System.clearProperty(SurfaceCachingPolicy.SIZE_PROP);
		System.clearProperty(SurfaceCachingPolicy.FORCE_TYPE);
		SurfaceCachingPolicy.loadConfigFromProps();
		
		return surfs;
	}
	
	private CacheEnabledSurface gridSurf;
	private CacheEnabledSurface quadSurf;
	private CacheEnabledSurface compoundSurf;
	
	public TestSurfaceDistanceCaches(CacheEnabledSurface gridSurf, CacheEnabledSurface quadSurf, CacheEnabledSurface compoundSurf) {
		Preconditions.checkState(gridSurf instanceof EvenlyGriddedSurface);
		this.gridSurf = gridSurf;
		
		Preconditions.checkState(quadSurf instanceof QuadSurface);
		this.quadSurf = quadSurf;
		
		Preconditions.checkState(compoundSurf instanceof CompoundSurface);
		this.compoundSurf = compoundSurf;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Test
	public void testGriddedNormal() throws Throwable {
		testSurface(gridSurf, num_threads_normal, 100, 100);
	}

	@Test
	public void testGriddedExtra() throws Throwable {
		testSurface(gridSurf, num_threads_extra, 100, 100);
	}

	@Test
	public void testQuadNormal() throws Throwable {
		testSurface(quadSurf, num_threads_normal, 100, 100);
	}

	@Test
	public void testQuadExtra() throws Throwable {
		testSurface(quadSurf, num_threads_extra, 100, 100);
	}

	@Test
	public void testCompoundNormal() throws Throwable {
		testSurface(compoundSurf, num_threads_normal, 100, 100);
	}

	@Test
	public void testCompoundExtra() throws Throwable {
		testSurface(compoundSurf, num_threads_extra, 100, 100);
	}
	
	private void testSurface(CacheEnabledSurface surf, int numThreads, int numTests, int numGetsPerTest) throws Throwable {
		List<Location> prevLocs = Lists.newArrayList();
		
		List<CalcTest> tests = Lists.newArrayList();
		
		Random r = new Random();
		
		for (int i=0; i<numTests; i++) {
			Location loc;
			if (!prevLocs.isEmpty() && r.nextDouble() < 0.4)
				// use previous loc
				loc = prevLocs.get(r.nextInt(prevLocs.size()));
			else
				loc = new Location(traceLoc2.getLatitude()+Math.random(), traceLoc2.getLongitude()+Math.random());
			
			tests.add(new CalcTest(surf, loc, numGetsPerTest));
		}
		
		try {
			new ThreadedTaskComputer(tests, true).computeThreaded(numThreads);
		} catch (InterruptedException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		for (CalcTest test : tests) {
			if (test.exception != null) {
				TestUtils.throwThreadedExceptionAsApplicable(test.exception);
			}
		}
	}
	
	private class CalcTest implements Task {
		
		private CacheEnabledSurface surf;
		private Location loc;
		private int numGetsPerTest;
		
		private Throwable exception;
		
		public CalcTest(CacheEnabledSurface surf, Location loc, int numGetsPerTest) {
			this.surf = surf;
			this.loc = loc;
			this.numGetsPerTest = numGetsPerTest;
		}

		@Override
		public void compute() {
			try {
				// calculate manually
				SurfaceDistances dists = surf.calcDistances(loc);
				double distX = surf.calcDistanceX(loc);
				
				// now verify with cached
				for (int i=0; i<numGetsPerTest; i++) {
					assertEquals(dists.getDistanceRup(), surf.getDistanceRup(loc), delta);
					assertEquals(dists.getDistanceJB(), surf.getDistanceJB(loc), delta);
					assertEquals(dists.getDistanceSeis(), surf.getDistanceSeis(loc), delta);
					assertEquals(distX, surf.getDistanceX(loc), delta);
				}
			} catch (Throwable e) {
				exception = e;
			}
		}
		
	}

}
