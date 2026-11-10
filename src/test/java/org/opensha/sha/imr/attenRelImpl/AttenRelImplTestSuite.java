package org.opensha.sha.imr.attenRelImpl;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Suite for the {@link org.opensha.sha.imr.attenRelImpl} tests that should run on every build. The production IMR
 * instantiation and consistency tests are deliberately left out; they are slow and are run on their own.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
	JointRuptureExperimentalIMRTest.class,
})

public class AttenRelImplTestSuite {

}
