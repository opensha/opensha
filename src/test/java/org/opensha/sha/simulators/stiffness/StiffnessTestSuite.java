package org.opensha.sha.simulators.stiffness;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.simulators.stiffness.patches.AutoPatchGenerationTest;
import org.opensha.sha.simulators.stiffness.patches.TriangularSubSectPatchBuilderTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({ DoubleDoubleTest.class, TriangularPrecisionTest.class, TriangularDislocationCalcTest.class,
		SelfStiffnessCapTest.class, AggregationMethodTest.class, AreaWeightedStiffnessTest.class,
		TriangularSubSectPatchBuilderTest.class, AutoPatchGenerationTest.class,
		org.opensha.sha.simulators.stiffness.patches.StirlingSubSectPatchBuilderTest.class,
		org.opensha.sha.simulators.stiffness.patches.GriddedSurfaceGeometryTest.class })
public class StiffnessTestSuite {
}
