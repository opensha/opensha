package org.opensha.sha.earthquake;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.collect.Lists;

public class AbstractNthRupERFTest {
	
	private class TestERF extends AbstractNthRupERF {
		
		private Random r = new Random();
		private List<ProbEqkSource> sourceList;

		@Override
		public int getNumSources() {
			return sourceList.size();
		}

		@Override
		public ProbEqkSource getSource(int idx) {
			return sourceList.get(idx);
		}

		@Override
		public void updateForecast() {
			int numSources = r.nextInt(100)+50; // random int between 50 and 150
			
			int[] numRups = new int[numSources];
			for (int i=0; i<numSources; i++)
				numRups[i] = r.nextInt(3)+1; // random int between 1 and 4
			setSources(numRups);
		}
		
		public void setSources(int... numRups) {
			sourceList = Lists.newArrayList();
			for (int sourceNumRups : numRups)
				sourceList.add(new FakeSource(sourceNumRups));
			sourceRupIndexesChanged();
		}

		@Override
		public String getName() {
			return "Fake ERF";
		}
		
	}
	
	private class FakeSource extends ProbEqkSource {
		
		private List<ProbEqkRupture> rups;
		private Location loc;
		private PointSurface surf;

		public FakeSource(int numRups) {
			rups = Lists.newArrayList();
			loc = new Location(34+Math.random(), -118+Math.random());
			surf = new PointSurface(loc);
			for (int i=0; i<numRups; i++) {
				ProbEqkRupture rup = new ProbEqkRupture(Math.random()+6d, 0d, Math.random()*0.01, surf, loc);
				rups.add(rup);
			}
		}
		
		@Override
		public LocationList getAllSourceLocs() {
			LocationList locs = new LocationList();
			locs.add(loc);
			return locs;
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return surf;
		}

		@Override
		public double getMinDistance(Site site) {
			return 0;
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return rups.get(nRupture);
		}
		
	}

	private void assertNthRupMappingConsistent(AbstractNthRupERF erf) {
		int totNumRups = erf.getTotNumRups();
		
		int actualTotNumRups = 0;
		for (ProbEqkSource source : erf)
			actualTotNumRups += source.getNumRuptures();
		
		assertEquals("Nth rup count wrong", actualTotNumRups, totNumRups);
		
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			int[] sourceNthIndexes = erf.get_nthRupIndicesForSource(sourceID);
			
			assertEquals("Nth rup count wrong for source "+sourceID, source.getNumRuptures(), sourceNthIndexes.length);
			
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				int nthIndex = erf.getIndexN_ForSrcAndRupIndices(sourceID, rupID);
				assertEquals("Nth index inconsistent", nthIndex, sourceNthIndexes[rupID]);
				
				int testSourceIndex = erf.getSrcIndexForNthRup(nthIndex);
				assertEquals("Nth source index inconsistent", sourceID, testSourceIndex);
				
				int testRupIndex = erf.getRupIndexInSourceForNthRup(nthIndex);
				assertEquals("Nth rup in source index inconsistent", rupID, testRupIndex);
				
				ProbEqkRupture testRupture = erf.getNthRupture(nthIndex);
				assertEquals("Nth rup fetch incorrect", source.getRupture(rupID), testRupture);
			}
		}
		
		int index = 0;
		for (int s=0; s<erf.getNumSources(); s++) {
			int[] test = erf.get_nthRupIndicesForSource(s);
			for (int r=0; r<test.length; r++) {
				int nthRup = test[r];
				assertEquals("Sequential test failed", index, nthRup);
				index++;
			}
		}
		assertEquals("Sequential test didn't cover all ruptures", totNumRups, index);
	}

	@Test
	public void testRandomNonEmptySources() {
		TestERF erf = new TestERF();
		
		for (int n=0; n<10; n++) {
			erf.updateForecast();
			assertNthRupMappingConsistent(erf);
		}
	}
	
	@Test
	public void testInterleavedEmptySources() {
		TestERF erf = new TestERF();
		erf.setSources(0, 2, 0, 0, 1, 0, 3, 0);
		
		assertEquals(6, erf.getTotNumRups());
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(0));
		assertArrayEquals(new int[] { 0, 1 }, erf.get_nthRupIndicesForSource(1));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(2));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(3));
		assertArrayEquals(new int[] { 2 }, erf.get_nthRupIndicesForSource(4));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(5));
		assertArrayEquals(new int[] { 3, 4, 5 }, erf.get_nthRupIndicesForSource(6));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(7));
		
		assertEquals(1, erf.getSrcIndexForNthRup(0));
		assertEquals(1, erf.getSrcIndexForNthRup(1));
		assertEquals(4, erf.getSrcIndexForNthRup(2));
		assertEquals(6, erf.getSrcIndexForNthRup(3));
		assertEquals(6, erf.getSrcIndexForNthRup(4));
		assertEquals(6, erf.getSrcIndexForNthRup(5));
		
		assertEquals(0, erf.getRupIndexInSourceForNthRup(0));
		assertEquals(1, erf.getRupIndexInSourceForNthRup(1));
		assertEquals(0, erf.getRupIndexInSourceForNthRup(2));
		assertEquals(0, erf.getRupIndexInSourceForNthRup(3));
		assertEquals(1, erf.getRupIndexInSourceForNthRup(4));
		assertEquals(2, erf.getRupIndexInSourceForNthRup(5));
		
		assertNthRupMappingConsistent(erf);
	}
	
	@Test
	public void testAllSourcesEmpty() {
		TestERF erf = new TestERF();
		erf.setSources(0, 0, 0);
		
		assertEquals(0, erf.getTotNumRups());
		for (int s=0; s<erf.getNumSources(); s++)
			assertArrayEquals("Expected no nth ruptures for source "+s, new int[0], erf.get_nthRupIndicesForSource(s));
		assertNthRupMappingConsistent(erf);
	}
	
	@Test(expected = IllegalStateException.class)
	public void testAllSourcesEmptyRejectsNthLookup() {
		TestERF erf = new TestERF();
		erf.setSources(0, 0, 0);
		erf.getSrcIndexForNthRup(0);
	}
	
	@Test
	public void testCacheInvalidationAcrossForecastUpdates() {
		TestERF erf = new TestERF();
		erf.setSources(2, 0, 1);
		assertEquals(3, erf.getTotNumRups());
		assertArrayEquals(new int[] { 0, 1 }, erf.get_nthRupIndicesForSource(0));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(1));
		assertArrayEquals(new int[] { 2 }, erf.get_nthRupIndicesForSource(2));
		
		erf.setSources(0, 1, 0, 2);
		assertEquals(3, erf.getTotNumRups());
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(0));
		assertArrayEquals(new int[] { 0 }, erf.get_nthRupIndicesForSource(1));
		assertArrayEquals(new int[0], erf.get_nthRupIndicesForSource(2));
		assertArrayEquals(new int[] { 1, 2 }, erf.get_nthRupIndicesForSource(3));
		assertEquals(1, erf.getSrcIndexForNthRup(0));
		assertEquals(3, erf.getSrcIndexForNthRup(1));
		assertEquals(3, erf.getSrcIndexForNthRup(2));
		assertNthRupMappingConsistent(erf);
	}

}
