package org.opensha.sha.simulators.stiffness;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({GenericSubSectPatchBuilderTest.class, AutoPatchGenerationTest.class, PathologicalSubSectPatchBuilderTest.class, NarrowStripPatchInfillTest.class})
public class StiffnessTestSuite {}
