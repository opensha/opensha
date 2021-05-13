package scratch.UCERF3.inversion.laughTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	// these are unique to the old UCERF3 way of doing things, now disabled
//	TestCoulombFilter.class,
//	TestIncrementalVsFullTests.class,
	TestPlausbilityResult.class
})

public class LaughTestSuite {

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(LaughTestSuite.class);
	}
}
