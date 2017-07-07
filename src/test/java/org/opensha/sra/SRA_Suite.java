package org.opensha.sra;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.util.InterpolateTests;
import org.opensha.sra.rtgm.RTGM_Tests;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	RTGM_Tests.class})

public class SRA_Suite {
	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(SRA_Suite.class);
	}
}
