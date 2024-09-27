package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
		UniqueRuptureTest.class,
		SectIDRangeTest.class
	})

public class RuptureUtilTestSuite {
	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(RuptureUtilTestSuite.class, SectIDRangeTest.class);
	}
}
