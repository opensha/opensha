package org.opensha.sha.faultSurface;

import java.io.Serializable;

import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;

public interface RupInRegionCache extends Serializable {
	
	public boolean isRupInRegion(ERF erf, ProbEqkSource source, EqkRupture rup,
			int srcIndex, int rupIndex, Region region);

}
