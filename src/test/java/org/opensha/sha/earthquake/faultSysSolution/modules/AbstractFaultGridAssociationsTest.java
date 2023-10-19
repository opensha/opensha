package org.opensha.sha.earthquake.faultSysSolution.modules;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;

public abstract class AbstractFaultGridAssociationsTest {

	protected static File tempDir;
	
	protected final FaultSystemRupSet rupSet;
	protected final FaultGridAssociations assoc;
	protected final int sectCount;
	protected final int nodeCount;

	private static Map<FaultGridAssociations, FaultGridAssociations> averagedAssocCache;
	private static Map<FaultGridAssociations, FaultGridAssociations> serializedAssocCache;

	public AbstractFaultGridAssociationsTest(FaultSystemRupSet rupSet, FaultGridAssociations assoc) {
		this.rupSet = rupSet;
		this.assoc = assoc;
		this.sectCount = rupSet.getNumSections();
		this.nodeCount = assoc.getRegion().getNodeCount();
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tempDir = Files.createTempDir();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		FileUtils.deleteRecursive(tempDir);
	}
	
	protected synchronized FaultGridAssociations getAveragedInstance(FaultGridAssociations assoc) {
		if (averagedAssocCache == null)
			averagedAssocCache = new HashMap<>();
		FaultGridAssociations averagedAssoc = averagedAssocCache.get(assoc);
		if (averagedAssoc == null) {
			System.out.println("Building average association instance");
			AveragingAccumulator<FaultGridAssociations> accumulator = assoc.averagingAccumulator();
			FaultGridAssociations.Averager assocAvg = accumulator instanceof FaultGridAssociations.Averager
					? (FaultGridAssociations.Averager)accumulator : null;

			accumulator.process(assoc, 1d);
			accumulator.process(assoc, 1d);
			if (assocAvg != null) {
				if (assocAvg != null && !assocAvg.areAllIdentical())
					System.err.println("WARNING: averager didn't think they were idential!");
				// force it to actually average
				assocAvg.disableIdenticalCheck();
			}

			averagedAssoc = accumulator.getAverage();
			averagedAssocCache.put(assoc, averagedAssoc);
		}
		return averagedAssoc;
	}
	
	protected synchronized FaultGridAssociations getSerializedInstance(FaultGridAssociations assoc) {
		if (serializedAssocCache == null)
			serializedAssocCache = new HashMap<>();
		FaultGridAssociations serializedAssoc = serializedAssocCache.get(assoc);
		if (serializedAssoc == null) {
			System.out.println("Building serialized association instance");
			
			assertTrue(assoc.getName()+" isn't archivable!", assoc instanceof ArchivableModule);
			File archiveFile = new File(tempDir, "serialized_"+assoc.hashCode()+".zip");
			
			try {
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(assoc);
				archive.write(archiveFile);
				
				archive = new ModuleArchive<>(archiveFile);
				serializedAssoc = archive.requireModule(FaultGridAssociations.class);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			serializedAssocCache.put(assoc, serializedAssoc);
		}
		return serializedAssoc;
	}
	
	/*
	 * Test for node-aggregated quantities
	 */
	
	@Test
	public void testSectionFracsOnNode() {
		// test that they're all finite and >=0
		Table<Integer, Integer, Double> sectNodeFractsTable = HashBasedTable.create();
		for (int n=0; n<nodeCount; n++) {
			Map<Integer, Double> sectFracts = assoc.getSectionFracsOnNode(n);
			double sumSectFracts = 0d;
			for (int sectIndex : sectFracts.keySet()) {
				double val = sectFracts.get(sectIndex);
				sumSectFracts += val;
				sectNodeFractsTable.put(sectIndex, n, val);
			}
			assertNonNegAndFinite("sumSectFracts", sumSectFracts);
		}
		
		// test that each section within the region is fully mapped
		for (int sectIndex=0; sectIndex<sectCount; sectIndex++) {
			if (!sectNodeFractsTable.containsRow(sectIndex))
				continue;
			double sum = 0d;
			Map<Integer, Double> nodeFracts = sectNodeFractsTable.row(sectIndex);
			for (int nodeIndex : nodeFracts.keySet()) {
				double val = nodeFracts.get(nodeIndex);
				assertZeroToOne("Sect "+sectIndex+", node "+nodeIndex+", sectFractsOnNode", val);
				sum += val;
			}
			assertEquals("Sect "+sectIndex+" summed over all nodes from getSectionFracsOnNode mismatch",
					assoc.getSectionFractInRegion(sectIndex), sum, TOL);
		}
	}
	
	@Test
	public void testScaledNodeFracts() {
		// test that they're all finite and >=0
		// test that they equal that returned by getScaledSectFracsOnNode
		for (int s=0; s<sectCount; s++) {
			Map<Integer, Double> map = assoc.getScaledNodeFractions(s);
			for (int n : map.keySet()) {
				double scaledFract = map.get(n);
				assertZeroToOne("scaledFract["+n+"]["+s+"]", scaledFract);
				assertEquals("getScaledNodeFractions and getScaledSectFracsOnNode return different "
						+ "values for node "+n+", sect ", scaledFract, assoc.getScaledSectFracsOnNode(n).get(s), TOL);
			}
		}
		for (int n=0; n<nodeCount; n++) {
			double scaledSum = 0d;
			for (int sectIndex : assoc.getSectionFracsOnNode(n).keySet()) {
				double scaledFract = assoc.getScaledNodeFractions(sectIndex).get(n);
				assertZeroToOne("scaledFract["+n+"]["+sectIndex+"]", scaledFract);
				scaledSum += scaledFract;
			}
			assertZeroToOne("scaledSum["+n+"]", scaledSum);
		}
	}
	
	@Test
	public void testGetScaledSectFracsOnNode() {
		// test that they're all finite and >=0
		// test that scaled sums are also <=1
		for (int n=0; n<nodeCount; n++) {
			double scaledSum = 0d;
			Map<Integer, Double> map = assoc.getScaledSectFracsOnNode(n);
			for (int sectIndex : map.keySet()) {
				double scaledFract = map.get(sectIndex);
				assertZeroToOne("scaledFract["+n+"]["+sectIndex+"]", scaledFract);
				scaledSum += scaledFract;
			}
			assertZeroToOne("scaledSum["+n+"]", scaledSum);
		}
	}
	
	@Test
	public void testOrigNodeFracts() {
		// test that they're all finite and >=0
		// test that scaled sums are <= unscaled sums
		// test that scaled sums are also <=1
		// test for consistency with getSectionFracsOnNode
		
		for (int s=0; s<sectCount; s++) {
			Map<Integer, Double> nodeFracts = assoc.getNodeFractions(s);
			double sumFract = 0d;
			double sumSectFracts = 0d;
			for (int nodeIndex : nodeFracts.keySet()) {
				double fract = nodeFracts.get(nodeIndex);
				assertZeroToOne("Sect "+s+" fract for node "+nodeIndex, fract);
				sumFract += fract;
				double sectFractOnNode = assoc.getSectionFracsOnNode(nodeIndex).get(s);
				assertEquals("getSectionFracsOnNode and getNodeFractions not identical for sect "+s+", node "+nodeIndex,
						fract, sectFractOnNode, TOL);
				sumSectFracts += sectFractOnNode;
			}
			assertEquals("Sum of node fracts for sect "+s+" should equal the fraction in region",
					assoc.getSectionFractInRegion(s), sumFract, TOL);
			assertEquals("NodeFracts for sect "+s+" is inconsistent with sum from getSectionFracsOnNode", sumFract, sumSectFracts, TOL);
		}
	}
	
	@Test
	public void testNodeFracts() {
		// test that they're all in [0,1]
		// test for consistency with scaledNodeFracts
		// test for consistency with getSectionFracsOnNode
		for (int n=0; n<nodeCount; n++) {
			double nodeFract = assoc.getNodeFraction(n);
			assertZeroToOne("Node fraction "+n, nodeFract);
			
			double calcNodeFract = 0d;
			for (int sectIndex : assoc.getSectionFracsOnNode(n).keySet())
				calcNodeFract += assoc.getScaledNodeFractions(sectIndex).get(n);
			
			assertEquals("NodeFract["+n+"] is inconsistent with sum from getScaledNodeFractions", calcNodeFract, nodeFract, TOL);
		}
	}
	
	@Test
	public void testSectIndices() {
		doTestSectIndices(assoc);
	}
	
	private void doTestSectIndices(FaultGridAssociations assoc) {
		HashSet<Integer> sectIndices = new HashSet<>(assoc.sectIndices());
		HashSet<Integer> nodeObsSectIndices = new HashSet<>();
		for (int n=0; n<nodeCount; n++) {
			Map<Integer, Double> fracts = assoc.getSectionFracsOnNode(n);
			if (fracts != null)
				nodeObsSectIndices.addAll(fracts.keySet());
		}
		assertSetEquals("Sects set determined from node fracts", nodeObsSectIndices, sectIndices);
		
		HashSet<Integer> sectObsSectIndices = new HashSet<>();
		for (int s=0; s<sectCount; s++) {
			Map<Integer, Double> fracts = assoc.getNodeFractions(s);
			if (fracts != null && !fracts.isEmpty())
				sectObsSectIndices.add(s);
		}
		assertSetEquals("Sects set determined from sect fracts", sectObsSectIndices, sectIndices);
	}
	
	protected static <E> void assertSetEquals(String name, Set<E> refSet, Set<E> testSet) {
		assertEquals(name+" sizes inconsistent", refSet.size(), testSet.size());
		for (E refVal : refSet)
			assertTrue(name+" doesn't contain "+refVal, testSet.contains(refVal));
	}
	
	protected static void assertNonNegAndFinite(String name, double val) {
		assertTrue(name+" is non-finite: "+val, Double.isFinite(val));
		assertTrue(name+" is < 0: "+val, (float)val >= 0f);
	}
	
	protected static void assertZeroToOne(String name, double val) {
		assertNonNegAndFinite(name, val);
		assertTrue(name+" is > 1: "+val, (float)val <= 1f);
	}
	
	protected static final double TOL = 1e-6;
	
	/*
	 * Tests for to make sure that things average correctly
	 */
	
	@Test
	public void testAveragedGetNodeExtentsEqual() {
		doTestGetNodeExtentsEqual(assoc, getAveragedInstance(assoc));
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
		doTestGetNodeFractionEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedNodeFractionEqual() {
		doTestGetNodeFractionEqual(assoc, getSerializedInstance(assoc));
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
		doTestGetScaledNodeFractionsEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetScaledNodeFractionsEqual() {
		doTestGetScaledNodeFractionsEqual(assoc, getSerializedInstance(assoc));
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
		doTestGetNodeFractionsEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetNodeFractions() {
		doTestGetNodeFractionsEqual(assoc, getSerializedInstance(assoc));
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
		doTestGetSectionFracsOnNodeEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetSectionFracsOnNode() {
		doTestGetSectionFracsOnNodeEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetSectionFracsOnNodeEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		for (int n=0; n<nodeCount; n++) {
			Map<Integer, Double> sectFracts1 = assoc1.getSectionFracsOnNode(n);
			Map<Integer, Double> sectFracts2 = assoc2.getSectionFracsOnNode(n);
			testIntDoubleMapEqual(sectFracts1, sectFracts2, "Sect fractions for node "+n);
		}
	}
	
	@Test
	public void testAveragedSectIndicesEqual() {
		doTestSectIndicesEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedSectIndicesEqual() {
		doTestSectIndicesEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestSectIndicesEqual(FaultGridAssociations assoc1, FaultGridAssociations assoc2) {
		Collection<Integer> set1 = assoc1.sectIndices();
		Collection<Integer> set2 = assoc2.sectIndices();
		
		assertEquals("Sect count mismatch", set1.size(), set2.size());
		
		for (int sectIndex : set1) {
			assertTrue("Section "+sectIndex+" is not in both", set2.contains(sectIndex));
		}
	}
	
	@Test
	public void testAveragedSectIndices() {
		doTestSectIndices(getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedSectIndices() {
		doTestSectIndices(getSerializedInstance(assoc));
	}

}
