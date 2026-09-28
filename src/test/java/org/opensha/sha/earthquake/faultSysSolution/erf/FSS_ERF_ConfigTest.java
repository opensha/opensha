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
import org.opensha.sha.earthquake.param.AseismicityAreaReductionParam;

public class FSS_ERF_ConfigTest {

	@Test
	public void testTimeIndependentConfigUsesBaseERF() {
		FSS_ERF_Config config = FSS_ERF_Config.timeIndependent(50d);
		BaseFaultSystemSolutionERF erf = config.buildERF(null);
		assertFalse(erf instanceof TimeDepFaultSystemSolutionERF);
		assertEquals(StartTimePrecision.NONE, erf.getTimeSpan().getStartTimePrecision());
		assertEquals(50d, erf.getTimeSpan().getDuration(), 0d);
		assertNull(config.probabilityModel());
		assertEquals(BaseFaultSystemSolutionERF.ASEIS_REDUCES_AREA_DEAFULT, config.aseisReducesArea());
		assertEquals(BaseFaultSystemSolutionERF.USE_RUP_MFDS_DEAFULT, config.useRupMFDs());
		assertEquals(BaseFaultSystemSolutionERF.USE_PROXY_RUPS_DEAFULT, config.useProxyRuptures());
	}

	@Test
	public void testSourceConfiguration() {
		FSS_ERF_Config config = FSS_ERF_Config.builder()
				.aseisReducesArea(false)
				.useRupMFDs(false)
				.useProxyRuptures(false)
				.build();
		assertFalse(config.aseisReducesArea());
		assertFalse(config.useRupMFDs());
		assertFalse(config.useProxyRuptures());
		assertFalse((Boolean)config.buildERF(null).getParameter(AseismicityAreaReductionParam.NAME).getValue());
	}

	@Test
	public void testExplicitPoissonConfigHasNoStartTime() {
		FSS_ERF_Config config = FSS_ERF_Config.forProbabilityModel(
				FSS_ProbabilityModels.POISSON, 30d);
		TimeDepFaultSystemSolutionERF erf = (TimeDepFaultSystemSolutionERF)config.buildERF(null);
		assertTrue(erf.isPoisson());
		assertEquals(StartTimePrecision.NONE, erf.getTimeSpan().getStartTimePrecision());
		assertEquals(30d, erf.getTimeSpan().getDuration(), 0d);
	}

	@Test
	public void testTimeDependentConfig() {
		FSS_ERF_Config config = FSS_ERF_Config.timeDependent(
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
		assertInvalid(() -> FSS_ERF_Config.timeDependent(FSS_ProbabilityModels.POISSON, 2026, 50d));
		assertInvalid(() -> FSS_ERF_Config.forProbabilityModel(FSS_ProbabilityModels.NSHM27, 50d));
	}

	private static void assertInvalid(Runnable runnable) {
		try {
			runnable.run();
			fail("Expected invalid ERF configuration");
		} catch (IllegalArgumentException | NullPointerException expected) {}
	}
}
