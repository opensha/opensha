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
			
			sourceList = Lists.newArrayList();
			
			for (int i=0; i<numSources; i++) {
				int numRups = r.nextInt(3)+1; // random int between 1 and 4;
				ProbEqkSource source = new FakeSource(numRups);
				sourceList.add(source);
			}
			
			setAllNthRupRelatedArrays();
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

	@Test
	public void test() {
		TestERF erf = new TestERF();
		
		for (int n=0; n<10; n++) {
			erf.updateForecast();
			
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
		}
		
		int index = 0;
		for(int s=0; s<erf.getNumSources(); s++) {
			int[] test = erf.get_nthRupIndicesForSource(s);
			for(int r=0; r<test.length;r++) {
				int nthRup = test[r];
				assertEquals("Sequential test failed", index, nthRup);
				index++;
			}
		}
	}

}
