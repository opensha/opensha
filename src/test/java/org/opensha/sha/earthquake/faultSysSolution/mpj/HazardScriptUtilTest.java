package org.opensha.sha.earthquake.faultSysSolution.mpj;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;

public class HazardScriptUtilTest {

	@Test
	public void testTimeDependentArguments() {
		HazardConfig config = HazardConfig.builder()
				.durationYears(50d)
				.probabilityModel(FSS_ProbabilityModels.NSHM27_BRANCH_AVE)
				.startYear(2026)
				.aseisReducesArea(false)
				.useRupMFDs(false)
				.useProxyRuptures(false)
				.build();
		String args = HazardScriptUtil.buildSharedArgs(config);
		assertTrue(args.contains(" --duration 50.0"));
		assertTrue(args.contains(" --prob-model NSHM27_BRANCH_AVE"));
		assertTrue(args.contains(" --start-year 2026"));
		assertTrue(args.contains(" --no-aseis-reduces-area"));
		assertTrue(args.contains(" --no-mfds"));
		assertTrue(args.contains(" --no-proxy-ruptures"));
	}

	@Test
	public void testMetaCLIERFArguments() throws Exception {
		Options options = new Options();
		HazardConfig.addOptions(options);
		CommandLine commandLine = new DefaultParser().parse(options, new String[] {
				"--hazard-duration", "50", "--hazard-prob-model", "NSHM27_BRANCH_AVE",
				"--hazard-start-year", "2026", "--hazard-no-aseis-reduces-area",
				"--hazard-no-mfds", "--hazard-no-proxy-ruptures"
		});
		HazardConfig config = HazardConfig.builder().forCMD(commandLine).build();
		assertFalse(config.erfConfig().aseisReducesArea());
		assertFalse(config.erfConfig().useRupMFDs());
		assertFalse(config.erfConfig().useProxyRuptures());
		assertTrue(HazardScriptUtil.buildSharedArgs(config).contains(" --duration 50.0"));
	}
}
