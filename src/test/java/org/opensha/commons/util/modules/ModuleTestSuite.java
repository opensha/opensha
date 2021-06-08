package org.opensha.commons.util.modules;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	ModuleContainerTest.class,
	ModuleArchiveTest.class
})

public class ModuleTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(ModuleTestSuite.class);
	}
}
