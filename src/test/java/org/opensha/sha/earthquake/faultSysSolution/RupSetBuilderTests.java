package org.opensha.sha.earthquake.faultSysSolution;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class RupSetBuilderTests {
	
	private static InversionFaultSystemRupSet u3Default;
	private static FaultSystemRupSet reproduced;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("Building original U3 InversionFaultSystemRupSet");
		u3Default = InversionFaultSystemRupSetFactory.forBranch(U3LogicTreeBranch.DEFAULT);
		System.out.println("Building reproduction");
		reproduced = FaultSystemRupSet.builder(
				u3Default.getFaultSectionDataList(), u3Default.getSectionIndicesForAllRups())
				.forU3Branch(U3LogicTreeBranch.DEFAULT).build();
	}

	@Test
	public void testReproduceU3Mags() {
		testExactlyEqual(u3Default.getMagForAllRups(), reproduced.getMagForAllRups(), "Magnitude");
	}
	
	@Test
	public void testReproduceU3RupAreas() {
		testExactlyEqual(u3Default.getAreaForAllRups(), reproduced.getAreaForAllRups(), "Rupture Areas");
	}
	
	@Test
	public void testReproduceU3SectAreas() {
		testExactlyEqual(u3Default.getAreaForAllSections(), reproduced.getAreaForAllSections(), "Section Areas");
	}
	
	@Test
	public void testReproduceU3Rakes() {
		testExactlyEqual(u3Default.getAveRakeForAllRups(), reproduced.getAveRakeForAllRups(), "Rupture Rakes");
	}
	
	@Test
	public void testReproduceU3SectMinMags() {
		ModSectMinMags minMags = reproduced.getModule(ModSectMinMags.class);
		assertNotNull("Reproduction doesn't have ModSectMinMags module", minMags);
		double[] expected = new double[u3Default.getNumSections()];
		for (int s=0; s<expected.length; s++)
			expected[s] = u3Default.getFinalMinMagForSection(s);
		testExactlyEqual(expected, minMags.getMinMagForSections(), "Modified Section Min Mags");
	}
	
	@Test
	public void testReproduceU3AveSlips() {
		AveSlipModule aveSlips = reproduced.getModule(AveSlipModule.class);
		assertNotNull("Reproduction doesn't have AveSlipModule module", aveSlips);
		double[] actual = new double[reproduced.getNumRuptures()];
		for (int r=0; r<actual.length; r++)
			actual[r] = aveSlips.getAveSlip(r);
		testExactlyEqual(u3Default.getAveSlipForAllRups(), actual, "Average Slips");
	}
	
	@Test
	public void testReproduceU3Lengths() {
		testExactlyEqual(u3Default.getLengthForAllRups(), reproduced.getLengthForAllRups(), "Rupture Lengths");
	}
	
	@Test
	public void testReproduceU3SlipAlongs() {
		SlipAlongRuptureModel slipModule = reproduced.getModule(SlipAlongRuptureModel.class);
		AveSlipModule aveSlipModule = reproduced.getModule(AveSlipModule.class);
		assertNotNull("Reproduction doesn't have SlipAlongRuptureModule module", slipModule);
		for (int r=0; r<reproduced.getNumRuptures(); r++) {
			if (reproduced.getNumRuptures() > 50000 && Math.random() > 0.1)
				continue;
			testExactlyEqual(u3Default.getSlipOnSectionsForRup(r),
					slipModule.calcSlipOnSectionsForRup(reproduced, aveSlipModule, r), "Rupture "+r+" Dsr");
		}
	}
	
	@Test
	public void testReproduceU3Slips() {
		testExactlyEqual(u3Default.getSlipRateForAllSections(), reproduced.getSlipRateForAllSections(), "Section Slip Rates");
	}
	
	@Test
	public void testReproduceU3SlipStdDevs() {
		testExactlyEqual(u3Default.getSlipRateStdDevForAllSections(), reproduced.getSlipRateStdDevForAllSections(), "Section Slip Rates");
	}
	
	private static void testExactlyEqual(double[] expected, double[] actual, String name) {
		if (expected == null) {
			assertNull(name+": null was expected but we have values", actual);
			return;
		} else {
			assertNotNull(name+": we should have values, but it's null", actual);
		}
		assertEquals(name+": length mismatch", expected.length, actual.length);
		if (expected == actual)
			throw new IllegalStateException("expected and actual are the same object!");
		for (int i=0; i<expected.length; i++) {
			if (Double.isNaN(expected[i]))
				assertTrue(name+"["+i+"]: NaN expected, have "+actual[i], Double.isNaN(actual[i]));
			else
				assertTrue(name+"["+i+"]: values not exactly identical. Expected "+expected[i]+", have "+actual[i]
						+". Difference: "+Math.abs(expected[i]-actual[i]), expected[i] == actual[i]);
		}
	}

}
