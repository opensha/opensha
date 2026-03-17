package org.opensha.refFaultParamDb.vo;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	FaultSectionPrefDataTest.class
	})

public class FaultSectionPrefDataTestSuite {
	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(FaultSectionPrefDataTestSuite.class);
	}
}
