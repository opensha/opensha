package org.opensha.sha.earthquake.faultSysSolution.erf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.junit.Test;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModel.WeightedCombination;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenInterval;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenIntervals;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.UCERF3_ProbabilityModel;
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

	@Test
	public void testSingleValueAndYearInference() {
		FSS_ERF_Config config = FSS_ERF_Config.builder()
				.probabilityModel(FSS_ProbabilityModels.UCERF3_METHOD)
				.startYear(2026)
				.aperiodicityValue(0.55)
				.historicalOpenIntervalYear(1900)
				.build();
		assertEquals(AperiodicityModels.SINGLE_VALUED, config.aperiodicityModel());
		assertEquals(Double.valueOf(0.55), config.aperiodicityValue());
		assertEquals(HistoricalOpenIntervals.SINGLE_YEAR, config.historicalOpenInterval());
		assertEquals(Integer.valueOf(1900), config.historicalOpenIntervalYear());
	}

	@Test
	public void testProbabilityModelOverridesApplied() throws Exception {
		FaultSystemSolution solution = loadDemoSolution();
		FSS_ERF_Config config = FSS_ERF_Config.builder()
				.probabilityModel(FSS_ProbabilityModels.UCERF3_METHOD)
				.startYear(2026)
				.renewalModel(RenewalModels.WEIBULL)
				.aperiodicityValue(0.55)
				.historicalOpenIntervalYear(1900)
				.build();
		TimeDepFaultSystemSolutionERF erf = (TimeDepFaultSystemSolutionERF)config.buildERF(solution);
		UCERF3_ProbabilityModel model = (UCERF3_ProbabilityModel)erf.getProbabilityModel();
		assertEquals(RenewalModels.WEIBULL, model.getRenewalModelChoice());
		assertEquals(AperiodicityModels.SINGLE_VALUED, model.getAperiodicityModelChoice());
		assertEquals(0.55,
				((AperiodicityModel.SingleValued)model.getAperiodicityModel()).getAperiodicity(), 0d);
		assertEquals(HistoricalOpenIntervals.SINGLE_YEAR, model.getHistOpenIntervalChoice());
		assertEquals(1900, ((HistoricalOpenInterval.SingleYear)model.getHistOpenInterval()).getYear());
	}

	@Test
	public void testLinkedWeightedModelOverride() throws Exception {
		FSS_ERF_Config config = FSS_ERF_Config.builder()
				.probabilityModel(FSS_ProbabilityModels.NSHM27_BRANCH_AVE)
				.startYear(2026)
				.historicalOpenIntervalYear(1900)
				.build();
		TimeDepFaultSystemSolutionERF erf = (TimeDepFaultSystemSolutionERF)config.buildERF(loadDemoSolution());
		WeightedCombination combination = (WeightedCombination)erf.getProbabilityModel();
		for (int i=0; i<combination.getProbModelList().size(); i++) {
			FSS_ProbabilityModel model = combination.getProbModelList().getValue(i);
			if (model instanceof UCERF3_ProbabilityModel)
				assertEquals(1900, ((HistoricalOpenInterval.SingleYear)
						((UCERF3_ProbabilityModel)model).getHistOpenInterval()).getYear());
		}
	}

	@Test
	public void testInvalidProbabilityModelOverrides() {
		assertInvalid(() -> FSS_ERF_Config.builder().aperiodicityValue(0.5).build());
		assertInvalid(() -> FSS_ERF_Config.builder()
				.probabilityModel(FSS_ProbabilityModels.UCERF3_METHOD).startYear(2026)
				.aperiodicityModel(AperiodicityModels.UCERF3_LOW).aperiodicityValue(0.5).build());
		assertInvalid(() -> FSS_ERF_Config.builder()
				.probabilityModel(FSS_ProbabilityModels.UCERF3_METHOD).startYear(2026)
				.historicalOpenInterval(HistoricalOpenIntervals.NONE)
				.historicalOpenIntervalYear(1900).build());
	}

	private static void assertInvalid(Runnable runnable) {
		try {
			runnable.run();
			fail("Expected invalid ERF configuration");
		} catch (IllegalArgumentException | NullPointerException expected) {}
	}

	private static FaultSystemSolution loadDemoSolution() throws Exception {
		return FaultSystemSolution.load(new File(
				"src/test/resources/org/opensha/sha/earthquake/faultSysSolution/demo_sol.zip"));
	}
}
