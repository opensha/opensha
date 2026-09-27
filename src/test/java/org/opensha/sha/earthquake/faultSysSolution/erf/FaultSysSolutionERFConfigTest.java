package org.opensha.sha.earthquake.faultSysSolution.erf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;

public class FaultSysSolutionERFConfigTest {

	@Test
	public void testTimeIndependentConfigUsesBaseERF() {
		FaultSysSolutionERFConfig config = FaultSysSolutionERFConfig.timeIndependent(50d);
		BaseFaultSystemSolutionERF erf = config.buildERF(null);
		assertFalse(erf instanceof TimeDepFaultSystemSolutionERF);
		assertEquals(StartTimePrecision.NONE, erf.getTimeSpan().getStartTimePrecision());
		assertEquals(50d, erf.getTimeSpan().getDuration(), 0d);
		assertNull(config.probabilityModel());
	}

	@Test
	public void testExplicitPoissonConfigHasNoStartTime() {
		FaultSysSolutionERFConfig config = FaultSysSolutionERFConfig.forProbabilityModel(
				FSS_ProbabilityModels.POISSON, 30d);
		TimeDepFaultSystemSolutionERF erf = (TimeDepFaultSystemSolutionERF)config.buildERF(null);
		assertTrue(erf.isPoisson());
		assertEquals(StartTimePrecision.NONE, erf.getTimeSpan().getStartTimePrecision());
		assertEquals(30d, erf.getTimeSpan().getDuration(), 0d);
	}

	@Test
	public void testTimeDependentConfig() {
		FaultSysSolutionERFConfig config = FaultSysSolutionERFConfig.timeDependent(
				FSS_ProbabilityModels.NSHM27_BRANCH_AVE, 2026, 50d);
		TimeDepFaultSystemSolutionERF erf = (TimeDepFaultSystemSolutionERF)config.buildERF(null);
		assertFalse(erf.isPoisson());
		assertEquals(StartTimePrecision.YEARS, erf.getTimeSpan().getStartTimePrecision());
		assertEquals(2026, erf.getTimeSpan().getStartTimeYear());
		assertEquals(50d, erf.getTimeSpan().getDuration(), 0d);
		assertEquals(erf.getTimeSpan(), config.buildTimeSpan());
	}

	@Test
	public void testInvalidStartYearCombinations() {
		assertInvalid(() -> FaultSysSolutionERFConfig.timeDependent(FSS_ProbabilityModels.POISSON, 2026, 50d));
		assertInvalid(() -> FaultSysSolutionERFConfig.forProbabilityModel(FSS_ProbabilityModels.NSHM27, 50d));
	}

	private static void assertInvalid(Runnable runnable) {
		try {
			runnable.run();
			fail("Expected invalid ERF configuration");
		} catch (IllegalArgumentException | NullPointerException expected) {}
	}
}
