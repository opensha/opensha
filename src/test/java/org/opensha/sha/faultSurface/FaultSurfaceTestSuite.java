package org.opensha.sha.faultSurface;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.faultSurface.cache.TestSurfaceDistanceCaches;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestSurfaceDistanceCaches.class})

// TODO add quad surface when finalized

public class FaultSurfaceTestSuite {
	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(FaultSurfaceTestSuite.class);
	}
}
