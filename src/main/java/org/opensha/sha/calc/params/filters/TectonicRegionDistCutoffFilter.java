package org.opensha.sha.calc.params.filters;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Tectonic region-specific fixed distance cutoffs, applied at the source level
 * ({@link #canSkipRupture(EqkRupture, Site)} always returns false)
 */
public class TectonicRegionDistCutoffFilter implements SourceFilter, ParameterChangeListener {
	
	private TectonicRegionDistCutoffParam param;
	private TectonicRegionDistanceCutoffs cutoffs;
	private ParameterList params;
	
	public TectonicRegionDistCutoffFilter() {
		param = new TectonicRegionDistCutoffParam();
		cutoffs = param.getValue();
		param.addParameterChangeListener(this);
		
		params = new ParameterList();
		params.addParameter(param);
	}
	
	public TectonicRegionDistanceCutoffs getCutoffs() {
		return cutoffs;
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		TectonicRegionType trt = source.getTectonicRegionType();
		double maxDist = cutoffs.getCutoffDist(trt);
		return sourceSiteDistance > maxDist;
	}

	@Override
	public boolean canSkipRupture(EqkRupture rup, Site site) {
		// done at the source level
		return false;
	}

	@Override
	public ParameterList getAdjustableParams() {
		return params;
	}
	
	public static class TectonicRegionDistanceCutoffs {
		private TectonicRegionType[] trts;
		private double[] cutoffDists;
		private double unknownDist;
		
		public TectonicRegionDistanceCutoffs(double unknownDist) {
			trts = TectonicRegionType.values();
			cutoffDists = new double[trts.length];
			for (int i=0; i<trts.length; i++)
				cutoffDists[i] = trts[i].defaultCutoffDist();
			this.unknownDist = unknownDist;
		}
		
		public double getCutoffDist(TectonicRegionType trt) {
			if (trt == null)
				return unknownDist;
			return cutoffDists[trt.ordinal()];
		}
		
		public void setCutoffDist(TectonicRegionType trt, double dist) {
			Preconditions.checkState(dist > 0, "Distance must be >0: %s", dist);
			if (trt == null) {
				unknownDist = dist;
			} else {
				for (int i=0; i<trts.length; i++) {
					if (trt == trts[i]) {
						cutoffDists[i] = dist;
						return;
					}
				}
				throw new IllegalStateException("TRT not found? "+trt);
			}
		}
		
		public double getLargestCutoffDist() {
			return Math.max(unknownDist, StatUtils.max(cutoffDists));
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		cutoffs = param.getValue();
	}
	
	

}
