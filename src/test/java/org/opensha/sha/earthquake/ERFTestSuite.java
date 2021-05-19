package org.opensha.sha.earthquake;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.earthquake.rupForecastImpl.ProductionERFsInstantiationTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	ERFLoopTest.class,
//	CyberShakeUCERF2ReproducabilityTest.class,
	AbstractNthRupERFTest.class,
//	ProductionERFsInstantiationTest.class // TODO restore?
})

public class ERFTestSuite {

}
