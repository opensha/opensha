package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractFaultCubeAssociationsTest;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;

public class NSHM23_FaultCubeAssociationsTest extends AbstractFaultCubeAssociationsTest {
	
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
}
