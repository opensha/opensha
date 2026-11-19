package org.opensha.sha.simulators.stiffness;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.simulators.stiffness.patches.AutoPatchGenerationTest;
import org.opensha.sha.simulators.stiffness.patches.TriangularSubSectPatchBuilderTest;
import org.opensha.sha.simulators.stiffness.patches.RibbonSurfaceReferenceFrameTest;
import org.opensha.sha.simulators.stiffness.patches.GenericSubSectPatchBuilderTest;
import org.opensha.sha.simulators.stiffness.patches.NarrowStripPatchInfillTest;
import org.opensha.sha.simulators.stiffness.patches.PathologicalSubSectPatchBuilderTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({ DoubleDoubleTest.class, TriangularPrecisionTest.class, TriangularDislocationCalcTest.class,
		SelfStiffnessCapTest.class, AggregationMethodTest.class, AreaWeightedStiffnessTest.class,
		TriangularSubSectPatchBuilderTest.class, GenericSubSectPatchBuilderTest.class, AutoPatchGenerationTest.class,
		PathologicalSubSectPatchBuilderTest.class, NarrowStripPatchInfillTest.class,
		RibbonSurfaceReferenceFrameTest.class })
public class StiffnessTestSuite {
}
