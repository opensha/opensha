package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractFaultGridAssociationsTest;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;

public class NSHM23_FaultCubeAssociationsTest extends AbstractFaultGridAssociationsTest {
	
	private static FaultSystemRupSet rupSet;
	private static int sectCount;
	private static NSHM23_FaultCubeAssociations assoc;
	private static int cubeCount;
	
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
		cubeCount = assoc.getCubedGriddedRegion().getNumCubes();
	}
	
	public NSHM23_FaultCubeAssociationsTest() {
		super(rupSet, assoc);
	}
	
	/*
	 * tests for cube quantities
	 */
	
	@Test
	public void testSectDistWeightsAtCubes() {
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			double[] weights = assoc.getScaledSectDistWeightsAtCube(c);
			if (sects == null || sects.length == 0) {
				assertTrue(weights == null || weights.length == 0);
			} else {
				assertTrue(weights != null && weights.length == sects.length);
				for (int s=0; s<sects.length; s++)
					assertZeroToOne("Cube "+c+", sect weight for "+sects[s], weights[s]);
			}
		}
	}
	
	@Test
	public void testTotalSectDistWeightsAtCubes() {
		double[] calcSectSums = new double[sectCount];
		for (int c=0; c<cubeCount; c++) {
			int[] sects = assoc.getSectsAtCube(c);
			double[] weights = assoc.getScaledSectDistWeightsAtCube(c);
			if (sects == null || sects.length == 0) {
				assertTrue(weights == null || weights.length == 0);
			} else {
				assertTrue(weights != null && weights.length == sects.length);
				for (int s=0; s<sects.length; s++)
					calcSectSums[sects[s]] += weights[s];
			}
		}
		
		for (int s=0; s<sectCount; s++) {
			double fullSum = assoc.getTotalScaledDistWtAtCubesForSect(s);
			double sumInReg = fullSum * assoc.getSectionFractInRegion(s);
			double tol = Math.max(TOL, 0.001*calcSectSums[s]);
			assertEquals("Recalculated total dist-wt sect sum across all cubes is inconsistent for sect "+s,
					sumInReg, calcSectSums[s], tol);
		}
	}
}
