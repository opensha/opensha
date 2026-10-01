package org.opensha.sha.earthquake.faultSysSolution.mpj;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenIntervals;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;

public class HazardScriptUtilTest {

	@Test
	public void testTimeDependentArguments() {
		HazardConfig config = HazardConfig.builder()
				.durationYears(50d)
				.probabilityModel(FSS_ProbabilityModels.NSHM27_BRANCH_AVE)
				.startYear(2026)
				.historicalOpenIntervalYear(1900)
				.aseisReducesArea(false)
				.useRupMFDs(false)
				.useProxyRuptures(false)
				.build();
		String args = HazardScriptUtil.buildSharedArgs(config);
		assertTrue(args.contains(" --duration 50.0"));
		assertTrue(args.contains(" --prob-model NSHM27_BRANCH_AVE"));
		assertTrue(args.contains(" --start-year 2026"));
		assertTrue(args.contains(" --hist-open-interval SINGLE_YEAR"));
		assertTrue(args.contains(" --hist-open-interval-year 1900"));
		assertTrue(args.contains(" --no-aseis-reduces-area"));
		assertTrue(args.contains(" --no-mfds"));
		assertTrue(args.contains(" --no-proxy-ruptures"));
	}

	@Test
	public void testMetaCLIERFArguments() throws Exception {
		Options options = new Options();
		HazardConfig.addOptions(options);
		CommandLine commandLine = new DefaultParser().parse(options, new String[] {
				"--hazard-duration", "50", "--hazard-prob-model", "UCERF3_METHOD",
				"--hazard-start-year", "2026", "--hazard-renewal-model", "WEIBULL",
				"--hazard-aperiodicity-value", "0.55", "--hazard-hist-open-interval-year", "1900",
				"--hazard-no-aseis-reduces-area",
				"--hazard-no-mfds", "--hazard-no-proxy-ruptures"
		});
		HazardConfig config = HazardConfig.builder().forCMD(commandLine).build();
		assertTrue(config.erfConfig().renewalModel() == RenewalModels.WEIBULL);
		assertTrue(config.erfConfig().aperiodicityModel() == AperiodicityModels.SINGLE_VALUED);
		assertTrue(config.erfConfig().historicalOpenInterval() == HistoricalOpenIntervals.SINGLE_YEAR);
		assertFalse(config.erfConfig().aseisReducesArea());
		assertFalse(config.erfConfig().useRupMFDs());
		assertFalse(config.erfConfig().useProxyRuptures());
		String args = HazardScriptUtil.buildSharedArgs(config);
		assertTrue(args.contains(" --duration 50.0"));
		assertTrue(args.contains(" --renewal-model WEIBULL"));
		assertTrue(args.contains(" --aperiodicity-model SINGLE_VALUED"));
		assertTrue(args.contains(" --aperiodicity-value 0.55"));
		assertTrue(args.contains(" --hist-open-interval SINGLE_YEAR"));
		assertTrue(args.contains(" --hist-open-interval-year 1900"));
	}
}
