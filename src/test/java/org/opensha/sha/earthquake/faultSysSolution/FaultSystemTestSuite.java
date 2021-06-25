package org.opensha.sha.earthquake.faultSysSolution;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	RupSetBuilderTests.class,
	RupSetSaveLoadTests.class
})

public class FaultSystemTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(FaultSystemTestSuite.class);
	}
}
