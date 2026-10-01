package org.opensha.sha.earthquake.faultSysSolution.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.sha.earthquake.faultSysSolution.erf.FSS_ERF_Config;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenIntervals;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;

public class FaultSysHazardCalcSettingsTest {

	@Test
	public void testDefaultERFConfig() throws Exception {
		FSS_ERF_Config config = parse();
		assertEquals(1d, config.durationYears(), 0d);
		assertNull(config.probabilityModel());
		assertNull(config.startYear());
	}

	@Test
	public void testTimeDependentERFConfig() throws Exception {
		FSS_ERF_Config config = parse("--duration", "50", "--prob-model", "nshm27_branch_ave",
				"--start-year", "2026");
		assertEquals(50d, config.durationYears(), 0d);
		assertEquals(FSS_ProbabilityModels.NSHM27_BRANCH_AVE, config.probabilityModel());
		assertEquals(Integer.valueOf(2026), config.startYear());
		assertEquals(StartTimePrecision.YEARS, config.buildTimeSpan().getStartTimePrecision());
	}

	@Test
	public void testInvalidStartYearCombinations() throws Exception {
		assertInvalid("--start-year", "2026");
		assertInvalid("--prob-model", "POISSON", "--start-year", "2026");
		assertInvalid("--prob-model", "NSHM27");
	}

	@Test
	public void testSourceOptions() throws Exception {
		FSS_ERF_Config config = parse("--no-aseis-reduces-area", "--no-mfds", "--no-proxy-ruptures");
		assertFalse(config.aseisReducesArea());
		assertFalse(config.useRupMFDs());
		assertFalse(config.useProxyRuptures());
		assertInvalid("--aseis-reduces-area", "--no-aseis-reduces-area");
	}

	@Test
	public void testProbabilityModelOverrides() throws Exception {
		FSS_ERF_Config config = parse("--prob-model", "ucerf3_method", "--start-year", "2026",
				"--renewal-model", "weibull", "--aperiodicity-value", "0.55",
				"--hist-open-interval-year", "1900");
		assertEquals(RenewalModels.WEIBULL, config.renewalModel());
		assertEquals(AperiodicityModels.SINGLE_VALUED, config.aperiodicityModel());
		assertEquals(Double.valueOf(0.55), config.aperiodicityValue());
		assertEquals(HistoricalOpenIntervals.SINGLE_YEAR, config.historicalOpenInterval());
		assertEquals(Integer.valueOf(1900), config.historicalOpenIntervalYear());
	}

	@Test
	public void testInvalidProbabilityModelOverrides() throws Exception {
		assertInvalid("--aperiodicity-value", "0.5");
		assertInvalid("--prob-model", "UCERF3_METHOD", "--start-year", "2026",
				"--aperiodicity-model", "UCERF3_LOW", "--aperiodicity-value", "0.5");
		assertInvalid("--prob-model", "UCERF3_METHOD", "--start-year", "2026",
				"--hist-open-interval", "NONE", "--hist-open-interval-year", "1900");
	}

	private static FSS_ERF_Config parse(String... args) throws Exception {
		Options options = new Options();
		FaultSysHazardCalcSettings.addERFOptions(options);
		CommandLine commandLine = new DefaultParser().parse(options, args);
		return FaultSysHazardCalcSettings.getERFConfig(commandLine);
	}

	private static void assertInvalid(String... args) throws Exception {
		try {
			parse(args);
			fail("Expected invalid ERF configuration");
		} catch (IllegalArgumentException | NullPointerException expected) {}
	}
}
