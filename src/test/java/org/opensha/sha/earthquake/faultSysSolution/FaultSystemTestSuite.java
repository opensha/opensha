package org.opensha.sha.earthquake.faultSysSolution;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.earthquake.faultSysSolution.modules.StandardFaultSysModulesTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	RupSetBuilderTests.class,
	RupSetSaveLoadTests.class,
	StandardFaultSysModulesTest.class
})

public class FaultSystemTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(FaultSystemTestSuite.class);
	}
}
