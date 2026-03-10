package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

public interface NSHM26_SeisRateModel {
	
	public abstract IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax);

}
