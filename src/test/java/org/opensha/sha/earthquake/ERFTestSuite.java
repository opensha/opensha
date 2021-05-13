package org.opensha.sha.earthquake;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
//import org.opensha.sha.cybershake.CyberShakeUCERF2ReproducabilityTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	ERFLoopTest.class,
//	CyberShakeUCERF2ReproducabilityTest.class,
	AbstractNthRupERFTest.class
})

public class ERFTestSuite {

}
