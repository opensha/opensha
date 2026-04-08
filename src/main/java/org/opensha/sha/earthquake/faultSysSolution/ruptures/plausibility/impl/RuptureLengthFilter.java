package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class RuptureLengthFilter implements PlausibilityFilter {
	
	private double maxLen;

	/**
	 * 
	 * @param maxLen maximum rupture length (km)
	 */
	public RuptureLengthFilter(double maxLen) {
		Preconditions.checkArgument(maxLen > 0d);
		this.maxLen = maxLen;
	}

	@Override
	public String getShortName() {
		return "MaxLen";
	}

	@Override
	public String getName() {
		return "Maximum Length "+(float)maxLen+" km";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double len = 0d;
		if (rupture != null) {
			for (FaultSubsectionCluster cluster : rupture.clusters) {
				for (FaultSection sect : cluster.subSects) {
					len += sect.getTraceLength();
					if (len > maxLen)
						return PlausibilityResult.FAIL_HARD_STOP;
				}
			}
		}
		return PlausibilityResult.PASS;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return false;
	}

}
