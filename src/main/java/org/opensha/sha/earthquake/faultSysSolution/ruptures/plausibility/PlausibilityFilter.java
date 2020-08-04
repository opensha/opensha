package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.commons.data.ShortNamed;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public interface PlausibilityFilter extends ShortNamed {
	
	/**
	 * Apply the plausibility filter to the entire rupture
	 * @param rupture
	 * @param verbose
	 * @return
	 */
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose);
	
	/**
	 * Apply the plausibility filter to the given jump, assuming that existing rupture already passes
	 * @param rupture
	 * @param newJump
	 * @param verbose
	 * @return
	 */
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose);

}
