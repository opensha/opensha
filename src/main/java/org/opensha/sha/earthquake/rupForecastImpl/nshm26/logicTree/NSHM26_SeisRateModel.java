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
	
	public static class NSHM26_SiesRateModelSampleData {
		public final NSHM26_SeismicityRegions region;
		public final TectonicRegionType trt;
		public final double M1;
		public final double rate;
		public final double b;
		
		public NSHM26_SiesRateModelSampleData(NSHM26_SeismicityRegions region, TectonicRegionType trt, PureGR gr) {
			this(region, trt, gr.M1, gr.rateAboveM1, gr.b);
		}
		
		public NSHM26_SiesRateModelSampleData(NSHM26_SeismicityRegions region, TectonicRegionType trt, double m1,
				double rate, double b) {
			this.region = region;
			this.trt = trt;
			M1 = m1;
			this.rate = rate;
			this.b = b;
		}
		
		public PureGR toPureGR() {
			return new PureGR(RateType.M1, M1, Double.NaN, rate, b, Double.NaN, true);
		}
	}
	
	public static class NSHM26_SiesRateModelSample extends SimpleValuedNode<NSHM26_SiesRateModelSampleData> implements NSHM26_SeisRateModel {
		
		@SuppressWarnings("unused") // deserialization
		private NSHM26_SiesRateModelSample() {}

		public NSHM26_SiesRateModelSample(NSHM26_SiesRateModelSampleData value, double weight, String name,
				String shortName, String filePrefix) {
			super(value, NSHM26_SiesRateModelSampleData.class, weight, name, shortName, filePrefix);
		}

		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			NSHM26_SiesRateModelSampleData value = getValue();
			Preconditions.checkState(region == value.region, "Region mismatch: %s != %s", region, value.region);
			Preconditions.checkState(trt == value.trt, "TRT mismatch: %s != %s", trt, value.trt);
			return SeismicityRateFileLoader.buildIncrementalMFD(value.toPureGR(), refMFD, mMax, Double.NaN);
		}
		
	}

}
