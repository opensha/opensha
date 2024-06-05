package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * This fitler disallows any jumps involving any proxy fault, i.e., a fault section where
 * {@link FaultSection#isProxyFault()} returns true.
 */
public class NoProxyFaultConnectionsFilter implements PlausibilityFilter {

	@Override
	public String getShortName() {
		return "NoProxyConns";
	}

	@Override
	public String getName() {
		return "No Proxy Fault Connections";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		for (Jump jump : rupture.getJumpsIterable())
			if (jump.fromSection.isProxyFault() || jump.toSection.isProxyFault())
				return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

}
