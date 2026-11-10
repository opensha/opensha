package org.opensha.sha.earthquake.faultSysSolution;

import org.junit.runner.RunWith;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationRuptureExemptionsTest;
import org.junit.runners.Suite;
import org.opensha.sha.earthquake.faultSysSolution.modules.StandardFaultSysModulesTest;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraintTest;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometryTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
//	RupSetBuilderTests.class, // this reproduces U3 and is expensive; U3 is now old and not necessary to test every time 
	RupSetSaveLoadTests.class,
	StandardFaultSysModulesTest.class,
	SegmentationProbabilityConstraintTest.class,
	FaultTraceSegmentationGeometryTest.class,
	SegmentationRuptureExemptionsTest.class
})

public class FaultSystemTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(FaultSystemTestSuite.class);
	}
}
