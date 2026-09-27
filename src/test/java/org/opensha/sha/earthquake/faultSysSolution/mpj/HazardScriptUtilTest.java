package org.opensha.sha.earthquake.faultSysSolution.mpj;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;

public class HazardScriptUtilTest {

	@Test
	public void testTimeDependentArguments() {
		HazardConfig config = HazardConfig.builder()
				.durationYears(50d)
				.probabilityModel(FSS_ProbabilityModels.NSHM27_BRANCH_AVE)
				.startYear(2026)
				.build();
		String args = HazardScriptUtil.buildSharedArgs(config);
		assertTrue(args.contains(" --duration 50.0"));
		assertTrue(args.contains(" --prob-model NSHM27_BRANCH_AVE"));
		assertTrue(args.contains(" --start-year 2026"));
	}
}
