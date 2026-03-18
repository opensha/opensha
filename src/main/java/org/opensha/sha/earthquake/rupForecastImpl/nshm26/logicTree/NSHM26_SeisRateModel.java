package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateType;

public interface NSHM26_SeisRateModel {
	
	public abstract IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax);
	
	public static class NSHM26_SiesRateModelSample extends SimpleValuedNode<PureGR> implements NSHM26_SeisRateModel {
		
		private NSHM26_SeismicityRegions region;
		private TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private NSHM26_SiesRateModelSample() {}

		public NSHM26_SiesRateModelSample(PureGR value, NSHM26_SeismicityRegions region, TectonicRegionType trt, double weight, String name,
				String shortName, String filePrefix) {
			super(value, PureGR.class, weight, name, shortName, filePrefix);
			this.region = region;
			this.trt = trt;
		}

		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			PureGR value = getValue();
			Preconditions.checkState(this.region == null || region == this.region, "Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt, "TRT mismatch: %s != %s", trt, this.trt);
			return SeismicityRateFileLoader.buildIncrementalMFD(value, refMFD, mMax, Double.NaN);
		}
		
	}

}
