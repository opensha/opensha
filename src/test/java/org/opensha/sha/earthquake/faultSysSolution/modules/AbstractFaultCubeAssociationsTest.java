package org.opensha.sha.earthquake.faultSysSolution.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Test;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public abstract class AbstractFaultCubeAssociationsTest extends AbstractFaultGridAssociationsTest {
	
	protected final FaultCubeAssociations assoc;
	protected final int cubeCount;
	
	public AbstractFaultCubeAssociationsTest(FaultSystemRupSet rupSet, FaultCubeAssociations assoc) {
		super(rupSet, assoc);
		this.assoc = assoc;
		this.cubeCount = assoc.getCubedGriddedRegion().getNumCubes();
	}
	
	@Override
	protected synchronized FaultCubeAssociations getAveragedInstance(FaultGridAssociations assoc) {
		Preconditions.checkState(assoc instanceof FaultCubeAssociations);
		FaultGridAssociations averaged = super.getAveragedInstance(assoc);
		Preconditions.checkState(averaged instanceof FaultCubeAssociations);
		return (FaultCubeAssociations)averaged;
	}
	
	@Override
	protected synchronized FaultCubeAssociations getSerializedInstance(FaultGridAssociations assoc) {
		Preconditions.checkState(assoc instanceof FaultCubeAssociations);
		FaultGridAssociations serialized = super.getSerializedInstance(assoc);
		Preconditions.checkState(serialized instanceof FaultCubeAssociations);
		return (FaultCubeAssociations)serialized;
	}
	
	/*
	 * tests for cube quantities
	 */
	
	@Test
	public void testScaledSectDistWeightsAtCubes() {
		doTestSectDistWeightsAtCubes(true);
	}
	
	@Test
	public void testOrigSectDistWeightsAtCubes() {
		doTestSectDistWeightsAtCubes(false);
	}
	
	private void doTestSectDistWeightsAtCubes(boolean scaled) {
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			double[] weights = scaled ? assoc.getScaledSectDistWeightsAtCube(c) : assoc.getOrigSectDistWeightsAtCube(c);
			if (sects == null || sects.length == 0) {
				assertTrue(weights == null || weights.length == 0);
			} else {
				assertTrue(weights != null && weights.length == sects.length);
				for (int s=0; s<sects.length; s++)
					assertZeroToOne("Cube "+c+", sect weight for "+sects[s]+", scaled="+scaled, weights[s]);
			}
		}
	}
	
	@Test
	public void testSectDistWeightsAtCubesScaledLessThanOrig() {
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			if (sects != null && sects.length > 0) {
				double[] origWeights = assoc.getOrigSectDistWeightsAtCube(c);
				double[] scaledWeights = assoc.getScaledSectDistWeightsAtCube(c);
				for (int i=0; i<origWeights.length; i++) {
					assertTrue("Scaled weight for cube "+c+", sect "+sects[i]+" is greater than original: "
							+ "scaled="+scaledWeights[i]+", orig="+origWeights[i],
							(float)scaledWeights[i] <= (float)origWeights[i]);
				}
			}
		}
		for (int s=0; s<sectCount; s++) {
			double fullSum = assoc.getTotalOrigDistWtAtCubesForSect(s);
			double scaledSum = assoc.getTotalScaledDistWtAtCubesForSect(s);
			assertTrue("Scaled sum for sect "+s+" is greater than original: scaled="+scaledSum+", orig="+fullSum,
					(float)scaledSum <= (float)fullSum);
		}
	}
	
	@Test
	public void testTotalScaledSectDistWeightsAtCubes() {
		doTestTotalSectDistWeightsAtCubes(true);
	}
	
	@Test
	public void testTotalOrigSectDistWeightsAtCubes() {
		doTestTotalSectDistWeightsAtCubes(false);
	}
	
	private void doTestTotalSectDistWeightsAtCubes(boolean scaled) {
		double[] calcSectSums = new double[sectCount];
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			double[] weights = scaled ? assoc.getScaledSectDistWeightsAtCube(c) : assoc.getOrigSectDistWeightsAtCube(c);
			if (sects == null || sects.length == 0) {
				assertTrue(weights == null || weights.length == 0);
			} else {
				assertTrue(weights != null && weights.length == sects.length);
				for (int s=0; s<sects.length; s++)
					calcSectSums[sects[s]] += weights[s];
			}
		}
		
		for (int s=0; s<sectCount; s++) {
			double fullSum = scaled ? assoc.getTotalScaledDistWtAtCubesForSect(s) : assoc.getTotalOrigDistWtAtCubesForSect(s);
			double sumInReg = fullSum * assoc.getSectionFractInRegion(s);
			double tol = Math.max(TOL, 0.001*calcSectSums[s]);
			assertEquals("Recalculated total dist-wt sect sum across all cubes is inconsistent for sect "+s+", scaled="+scaled,
					sumInReg, calcSectSums[s], tol);
		}
	}
	
	@Test
	public void testTotalSectDistWeightsAtCubesScaledLessThanOrig() {
		for (int s=0; s<sectCount; s++) {
			double fullSum = assoc.getTotalOrigDistWtAtCubesForSect(s);
			double scaledSum = assoc.getTotalScaledDistWtAtCubesForSect(s);
			assertTrue("Scaled sum for sect "+s+" is greater than original: scaled="+scaledSum+", orig="+fullSum,
					(float)scaledSum <= (float)fullSum);
		}
	}
	
	@Test
	public void testCubeSectIndices() {
		doTestCubeSectIndices(assoc);
	}
	
	private void doTestCubeSectIndices(FaultCubeAssociations assoc) {
		HashSet<Integer> sectIndices = new HashSet<>(assoc.sectIndices());
		
		HashSet<Integer> cubeObsSectIndices = new HashSet<>();
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			if (sects != null)
				for (int sectIndex : sects)
					cubeObsSectIndices.add(sectIndex);
		}
		assertSetEquals("Sects set determined from node fracts", cubeObsSectIndices, sectIndices);
	}
	
	/*
	 * Averaging and serialization
	 */

	@Test
	public void testAveragedSectIndices() {
		doTestGetSectsAtCubeEqual(assoc, getAveragedInstance(assoc));
	}

	@Test
	public void testSerializedSectIndices() {
		doTestGetSectsAtCubeEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetSectsAtCubeEqual(FaultCubeAssociations assoc1, FaultCubeAssociations assoc2) {
		for (int c=0; c<cubeCount; c++) {
			int[] sects1 = assoc1.getSectsAtCube(c);
			int[] sects2 = assoc2.getSectsAtCube(c);
			
			boolean empty1 = sects1 == null || sects1.length == 0;
			boolean empty2 = sects2 == null || sects2.length == 0;
			assertEquals("One is empty and another is not for cube "+c, empty1, empty2);
			
			if (empty1 && empty2)
				continue;
			assertEquals("Sect count mismatch for cube "+c, sects1.length, sects2.length);
			for (int i=0; i<sects1.length; i++) {
				int sectIndex = sects1[i];
				int matchIndex = -1;
				if (sects2[i] == sectIndex)
					matchIndex = i;
				else
					matchIndex = Ints.indexOf(sects2, sectIndex);
				assertTrue("Section "+sectIndex+" not found for cube "+c, matchIndex >= 0);
			}
		}
	}
	
	@Test
	public void testAveragedGetScaledSectDistWeightsAtCube() {
		doTestGetScaledSectDistWeightsAtCubeEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetScaledSectDistWeightsAtCube() {
		doTestGetScaledSectDistWeightsAtCubeEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetScaledSectDistWeightsAtCubeEqual(FaultCubeAssociations assoc1, FaultCubeAssociations assoc2) {
		for (int c=0; c<cubeCount; c++) {
			int[] sects1 = assoc1.getSectsAtCube(c);
			int[] sects2 = assoc2.getSectsAtCube(c);
			
			if (sects1 != null || sects2 != null) {
				double[] weights1 = assoc1.getScaledSectDistWeightsAtCube(c);
				double[] weights2 = assoc2.getScaledSectDistWeightsAtCube(c);
				doTestCubeSectWeightArray(c, sects1, weights1, sects2, weights2);
			}
		}
	}
	
	@Test
	public void testAveragedGetOrigSectDistWeightsAtCube() {
		doTestGetOrigSectDistWeightsAtCubeEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetOrigSectDistWeightsAtCube() {
		doTestGetOrigSectDistWeightsAtCubeEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetOrigSectDistWeightsAtCubeEqual(FaultCubeAssociations assoc1, FaultCubeAssociations assoc2) {
		for (int c=0; c<cubeCount; c++) {
			int[] sects1 = assoc1.getSectsAtCube(c);
			int[] sects2 = assoc2.getSectsAtCube(c);
			
			if (sects1 != null || sects2 != null) {
				double[] weights1 = assoc1.getOrigSectDistWeightsAtCube(c);
				double[] weights2 = assoc2.getOrigSectDistWeightsAtCube(c);
				doTestCubeSectWeightArray(c, sects1, weights1, sects2, weights2);
			}
		}
	}
	
	private void doTestCubeSectWeightArray(int c, int[] sects1, double[] weights1, int[] sects2, double[] weights2) {
		for (int i=0; i<sects1.length; i++) {
			int sectIndex = sects1[i];
			int matchIndex = -1;
			if (sects2[i] == sectIndex)
				matchIndex = i;
			else
				matchIndex = Ints.indexOf(sects2, sectIndex);
			assertTrue("Section "+sectIndex+" not found for cube "+c, matchIndex >= 0);
			assertEquals("Weight mismatch for cube "+c+", sect "+sectIndex, weights1[i], weights2[matchIndex], TOL);
		}
	}
	
	@Test
	public void testAveragedGetTotalScaledDistWtAtCubesForSect() {
		doTestGetTotalScaledDistWtAtCubesForSectEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetTotalScaledDistWtAtCubesForSect() {
		doTestGetTotalScaledDistWtAtCubesForSectEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetTotalScaledDistWtAtCubesForSectEqual(FaultCubeAssociations assoc1, FaultCubeAssociations assoc2) {
		for (int s=0; s<sectCount; s++) {
			double val1 = assoc1.getTotalScaledDistWtAtCubesForSect(s);
			double val2 = assoc2.getTotalScaledDistWtAtCubesForSect(s);
			assertEquals("getTotalScaledDistWtAtCubesForSect mismatch for sect "+s, val1, val2, TOL);
		}
	}
	
	@Test
	public void testAveragedGetTotalOrigDistWtAtCubesForSect() {
		doTestGetTotalOrigDistWtAtCubesForSectEqual(assoc, getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGetTotalOrigDistWtAtCubesForSect() {
		doTestGetTotalOrigDistWtAtCubesForSectEqual(assoc, getSerializedInstance(assoc));
	}
	
	private void doTestGetTotalOrigDistWtAtCubesForSectEqual(FaultCubeAssociations assoc1, FaultCubeAssociations assoc2) {
		for (int s=0; s<sectCount; s++) {
			double val1 = assoc1.getTotalOrigDistWtAtCubesForSect(s);
			double val2 = assoc2.getTotalOrigDistWtAtCubesForSect(s);
			assertEquals("getTotalOrigDistWtAtCubesForSect mismatch for sect "+s, val1, val2, TOL);
		}
	}
	
	@Test
	public void testAveragedGridRegHasCubeInfo() {
		doTestGridRegHasCubeInfo(getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedGridRegHasCubeInfo() {
		doTestGridRegHasCubeInfo(getSerializedInstance(assoc));
	}
	
	private void doTestGridRegHasCubeInfo(FaultCubeAssociations assoc) {
		assertTrue("Instance should be of type precomputed", assoc instanceof FaultCubeAssociations.Precomputed);
		Feature feature = ((FaultCubeAssociations.Precomputed)assoc).getRegionFeature();
		assertNotNull("Gridded region feature should already be populated", feature);
		String[] keys = {
				CubedGriddedRegion.JSON_MAX_DEPTH,
				CubedGriddedRegion.JSON_NUM_CUBE_DEPTHS,
				CubedGriddedRegion.JSON_NUM_CUBES_PER_GRID_EDGE
		};
		for (String key : keys)
			assertTrue("GriddedRegion feature doesn't contain property: "+key, feature.properties.containsKey(key));
		CubedGriddedRegion cgr = assoc.getCubedGriddedRegion();
		assertNotNull("Couldn't build CGR?", cgr);
	}
	
	@Test
	public void testAveragedCubeSectIndices() {
		doTestCubeSectIndices(getAveragedInstance(assoc));
	}
	
	@Test
	public void testSerializedCubeSectIndices() {
		doTestCubeSectIndices(getSerializedInstance(assoc));
	}

}
