package scratch.UCERF3.inversion.laughTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestCoulombFilter.class,
	TestIncrementalVsFullTests.class,
	TestPlausbilityResult.class
})

public class LaughTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(LaughTestSuite.class);
	}
}
