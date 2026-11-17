package org.opensha.sha.simulators.stiffness;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.simulators.stiffness.patches.AutoPatchGenerationTest;
import org.opensha.sha.simulators.stiffness.patches.GenericSubSectPatchBuilderTest;
import org.opensha.sha.simulators.stiffness.patches.NarrowStripPatchInfillTest;
import org.opensha.sha.simulators.stiffness.patches.PathologicalSubSectPatchBuilderTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({GenericSubSectPatchBuilderTest.class, AutoPatchGenerationTest.class, PathologicalSubSectPatchBuilderTest.class, NarrowStripPatchInfillTest.class})
public class StiffnessTestSuite {}
