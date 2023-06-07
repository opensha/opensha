package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;

public class NSHM23_FaultCubeAssociationsTest {
	
	private static FaultSystemRupSet rupSet;
	private static int sectCount;
	private static NSHM23_FaultCubeAssociations assoc;
	private static int nodeCount;
	
	private static FaultGridAssociations averagedAssoc;
	
	@BeforeClass
	public static void beforeClass() throws IOException {
		NSHM23_LogicTreeBranch branch = NSHM23_LogicTreeBranch.DEFAULT_ON_FAULT;
		System.out.println("Building ref branch rup set");
		NSHM23_InvConfigFactory factory = new NSHM23_InvConfigFactory();
		File testCacheDir = new File("/home/kevin/OpenSHA/nshm23/rup_sets/cache");
		if (testCacheDir.exists())
			factory.setCacheDir(testCacheDir);
		rupSet = factory.buildRuptureSet(branch, FaultSysTools.defaultNumThreads());
		sectCount = rupSet.getNumSections();
		System.out.println("Building ref branch assoc");
		Region region = rupSet.requireModule(ModelRegion.class).getRegion();
		assoc = NSHM23_InvConfigFactory.buildFaultCubeAssociations(rupSet, branch, region);
		nodeCount = assoc.getRegion().getNodeCount();
		
		// now build averaged version (2 of the same averaged)
		FaultGridAssociations.Averager accumulator = (FaultGridAssociations.Averager)assoc.averagingAccumulator();
		
		accumulator.process(assoc, 1d);
		accumulator.process(assoc, 1d);
		if (!accumulator.areAllIdentical())
			System.err.println("WARNING: averager didn't think they were idential!");
		// force it to actually average
		accumulator.disableIdenticalCheck();
		
		averagedAssoc = accumulator.getAverage();
	}
	
	@Test
	public void testSectionFracsOnNode() {
		// test that they're all finite and >=0
		for (int n=0; n<nodeCount; n++) {
			Map<Integer, Double> sectFracts = assoc.getSectionFracsOnNode(n);
			double sumSectFracts = 0d;
			for (double val : sectFracts.values())
				sumSectFracts += val;
			assertNonNegAndFinite("sumSectFracts", sumSectFracts);
		}
	}
	
	@Test
	public void testScaledNodeFracts() {
		// test that they're all finite and >=0
		// test that scaled sums are <= unscaled sums
		// test that scaled sums are also <=1
		for (int n=0; n<nodeCount; n++) {
			double origSum = 0d;
			double scaledSum = 0d;
			for (int sectIndex : assoc.getSectionFracsOnNode(n).keySet()) {
				double origFract = assoc.getNodeFractions(sectIndex).get(n);
				double scaledFract = assoc.getScaledNodeFractions(sectIndex).get(n);
				assertTrue("Scaled fract exceeds original for node "+n+", sect "+sectIndex
						+": scaled="+scaledFract+", orig="+origFract,
						(float)scaledFract <= (float)origFract);
				assertZeroToOne("origFract["+n+"]["+sectIndex+"]", origFract);
				assertZeroToOne("scaledFract["+n+"]["+sectIndex+"]", scaledFract);
				origSum += origFract;
				scaledSum += scaledFract;
			}
			assertTrue("Scaled sum fract exceeds original for node "+n+": scaled="+scaledSum+", orig="+origSum,
					(float)scaledSum <= (float)origSum);
			assertZeroToOne("scaledSum["+n+"]", scaledSum);
		}
	}
	
	@Test
	public void testNodeFracts() {
		// test that they're all in [0,1]
		// test for consistency with scaledNodeFracts
		for (int n=0; n<nodeCount; n++) {
			double nodeFract = assoc.getNodeFraction(n);
			assertZeroToOne("Node fraction "+n, nodeFract);
			
			double calcNodeFract = 0d;
			for (int sectIndex : assoc.getSectionFracsOnNode(n).keySet())
				calcNodeFract += assoc.getScaledNodeFractions(sectIndex).get(n);
			
			assertEquals("NodeFract is inconsistent with sum from getScaledNodeFractions", calcNodeFract, nodeFract, TOL);
		}
	}
	
	private static void assertNonNegAndFinite(String name, double val) {
		assertTrue(name+" is non-finite: "+val, Double.isFinite(val));
		assertTrue(name+" is < 0: "+val, (float)val >= 0f);
	}
	
	private static void assertZeroToOne(String name, double val) {
		assertNonNegAndFinite(name, val);
		assertTrue(name+" is > 1: "+val, (float)val <= 1f);
	}
	
	private static final double TOL = 1e-6;
	
	@Test
	public void testAveragedGetNodeExtentsEqual() {
		doTestGetNodeExtentsEqual(assoc, averagedAssoc);
	}
	
	private void doTestGetNodeExtentsEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		Map<Integer, Double> extents1 = assoc1.getNodeExtents();
		Map<Integer, Double> extents2 = assoc2.getNodeExtents();
		
		// need to remove all zeros
		extents1 = stripZeros(extents1);
		extents2 = stripZeros(extents2);
		testIntDoubleMapEqual(extents1, extents2, "Node extents");
	}
	
	private Map<Integer, Double> stripZeros(Map<Integer, Double> map) {
		Map<Integer, Double> ret = new HashMap<>(map.size());
		for (Integer key : map.keySet()) {
			Double val = map.get(key);
			if (val != null && val > 0d)
				ret.put(key, val);
		}
		return ret;
	}
	
	private static void testIntDoubleMapEqual(Map<Integer, Double> map1, Map<Integer, Double> map2, String type) {
		assertTrue(type+" null mismatch",(map1 == null || map1.isEmpty()) == (map2 == null || map2.isEmpty()));
		if (map1 != null && !map1.isEmpty()) {
			assertEquals(type+" set mismatch", map1.keySet(), map1.keySet());
			double sum1 = 0d;
			double sum2 = 0d;
			for (int key  : map1.keySet()) {
				double val1 = map1.get(key);
				double val2 = map2.get(key);
				assertEquals(type+" mismatch with key="+key, val1, val2, TOL);
				sum1 += val1;
				sum2 += val2;
			}
			assertEquals(type+" value sum mismatch", sum1, sum2, TOL);
		}
	}
	
	@Test
	public void testAveragedetNodeFractionEqual() {
		doTestGetNodeFractionEqual(assoc, averagedAssoc);
	}
	
	private void doTestGetNodeFractionEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		for (int n=0; n<nodeCount; n++) {
			double nodeFract1 = assoc1.getNodeFraction(n);
			double nodeFract2 = assoc2.getNodeFraction(n);
			assertEquals("Node fract mismatch for "+n,  nodeFract1, nodeFract2, TOL);
		}
	}
	
	@Test
	public void testAveragedGetScaledNodeFractionsEqual() {
		doTestGetScaledNodeFractionsEqual(assoc, averagedAssoc);
	}
	
	private void doTestGetScaledNodeFractionsEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		for (int s=0; s<sectCount; s++) {
			Map<Integer, Double> scaledFracts1 = assoc1.getScaledNodeFractions(s);
			Map<Integer, Double> scaledFracts2 = assoc2.getScaledNodeFractions(s);
			testIntDoubleMapEqual(scaledFracts1, scaledFracts2, "Node scaled fractions for sect "+s);
		}
	}
	
	@Test
	public void testAveragedGetNodeFractions() {
		doTestGetNodeFractionsEqual(assoc, averagedAssoc);
	}
	
	private void doTestGetNodeFractionsEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		for (int s=0; s<sectCount; s++) {
			Map<Integer, Double> nodeFracts1 = assoc1.getNodeFractions(s);
			Map<Integer, Double> nodeFracts2 = assoc2.getNodeFractions(s);
			testIntDoubleMapEqual(nodeFracts1, nodeFracts2, "Node fractions for sect "+s);
		}
	}
	
	@Test
	public void testAveragedGetSectionFracsOnNode() {
		doTestGetSectionFracsOnNodeEqual(assoc, averagedAssoc);
	}
	
	private void doTestGetSectionFracsOnNodeEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		for (int n=0; n<nodeCount; n++) {
			Map<Integer, Double> sectFracts1 = assoc1.getSectionFracsOnNode(n);
			Map<Integer, Double> sectFracts2 = assoc2.getSectionFracsOnNode(n);
			testIntDoubleMapEqual(sectFracts1, sectFracts2, "Sect fractions for node "+n);
		}
	}
	
	@Test
	public void testAveragedSectIndices() {
		doTestSectIndicesEqual(assoc, averagedAssoc);
	}
	
	private void doTestSectIndicesEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		Collection<Integer> set1 = assoc1.sectIndices();
		Collection<Integer> set2 = assoc2.sectIndices();
		
		assertEquals("Sect count mismatch", set1.size(), set2.size());
		
		for (int sectIndex : set1) {
			assertTrue("Section "+sectIndex+" is not in both", set2.contains(sectIndex));
		}
	}
}
