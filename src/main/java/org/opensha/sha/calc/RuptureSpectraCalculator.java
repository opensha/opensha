package org.opensha.sha.calc;

import java.util.List;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.PointSurface.DistanceCorrectable;
import org.opensha.sha.faultSurface.PointSurface.SiteSpecificDistanceCorrected;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.imr.ErgodicIMR;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;

import com.google.common.base.Preconditions;

/**
 * Interface for calculating spectra, analagous to {@link RuptureExceedProbCalculator} except for spectra calculations.
 * This takes care of any point-source distance corrections, and allows for point-source optimizations (i.e.,
 * interpolated exceedance probabilities for point-source ruptures). 
 */
public interface RuptureSpectraCalculator {
	
	public DiscretizedFunc getSA_ExceedProbSpectrum(ScalarIMR gmm, EqkRupture rupture, double iml);
	
	public void getMultiPeriodExceedProbabilities(ScalarIMR gmm, PeriodParam periodParam, List<Double> periods,
			EqkRupture eqkRupture, DiscretizedFunc[] exceedProbs);
	
	static LightFixedXFunc calcSA_ExceedProbSpectrum(ScalarIMR gmm, EqkRupture eqkRupture, double iml) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof DistanceCorrectable) {
			// point surface with distance corrections
			Location siteLoc = gmm.getSite().getLocation();
			WeightedList<SurfaceDistances> surfs = ((PointSurface.DistanceCorrectable)surf).getCorrectedDistances(siteLoc);
			Preconditions.checkState(!surfs.isEmpty());
			Preconditions.checkState(surfs.isNormalized());
			
			double[] periods = null;
			double[] avgProbs = null;
			for (int s=0; s<surfs.size(); s++) {
				WeightedValue<SurfaceDistances> dists = surfs.get(s);
				
				if (s == 0 || !(gmm instanceof ErgodicIMR)) {
					// first time, need to set the full rupture
					SiteSpecificDistanceCorrected corrSurf = new SiteSpecificDistanceCorrected((PointSurface)surf, siteLoc, dists.value);
					gmm.setEqkRupture(new EqkRupture(eqkRupture.getMag(), eqkRupture.getAveRake(), corrSurf, eqkRupture.getHypocenterLocation()));
				} else {
					// subsequent time(s), only need to set the distances
					((ErgodicIMR)gmm).setPropagationEffectParams(dists.value);
				}
				
				DiscretizedFunc rupSpectrum = gmm.getSA_ExceedProbSpectrum(iml);
				if (s == 0) {
					avgProbs = new double[rupSpectrum.size()];
					if (rupSpectrum instanceof LightFixedXFunc) {
						periods = ((LightFixedXFunc)rupSpectrum).getXVals();
					} else {
						periods = new double[avgProbs.length];
						for (int i=0; i<periods.length; i++)
							periods[i] = rupSpectrum.getX(i);
					}
				}
				
				for (int i=0; i<avgProbs.length; i++)
					avgProbs[i] = Math.fma(rupSpectrum.getY(i), dists.weight, avgProbs[i]);
			}
			
			// clear the eqkRupture object so that we don't leave a stale site-specific corrected instance
			gmm.setEqkRupture(null);
			
			return new LightFixedXFunc(periods, avgProbs);
		} else {
			// no special treatment
			gmm.setEqkRupture(eqkRupture);
			
			DiscretizedFunc ret = gmm.getSA_ExceedProbSpectrum(iml);
			if (ret instanceof LightFixedXFunc)
				return (LightFixedXFunc)ret;
			return new LightFixedXFunc(ret);
		}
	}
	
	static void calcMultiPeriodExceedProbabilities(ScalarIMR gmm, PeriodParam periodParam,  List<Double> periods,
			EqkRupture eqkRupture, DiscretizedFunc[] exceedProbs) {
		Preconditions.checkState(periods.size() == exceedProbs.length);
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof DistanceCorrectable) {
			// point surface with distance corrections
			Location siteLoc = gmm.getSite().getLocation();
			WeightedList<SurfaceDistances> surfs = ((PointSurface.DistanceCorrectable)surf).getCorrectedDistances(siteLoc);
			Preconditions.checkState(surfs.isNormalized());
			
			LightFixedXFunc[] working = new LightFixedXFunc[periods.size()];
			double[][] avgProbs = new double[periods.size()][];
			for (int p=0; p<periods.size(); p++) {
				if (exceedProbs[p] instanceof LightFixedXFunc)
					working[p] = (LightFixedXFunc)exceedProbs[p];
				else
					working[p] = new LightFixedXFunc(exceedProbs[p]);
				avgProbs[p] = new double[working[p].size()];
			}
			
			for (int s=0; s<surfs.size(); s++) {
				WeightedValue<SurfaceDistances> dists = surfs.get(s);
				
				if (s == 0 || !(gmm instanceof ErgodicIMR)) {
					// first time, need to set the full rupture
					SiteSpecificDistanceCorrected corrSurf = new SiteSpecificDistanceCorrected((PointSurface)surf, siteLoc, dists.value);
					gmm.setEqkRupture(new EqkRupture(eqkRupture.getMag(), eqkRupture.getAveRake(), corrSurf, eqkRupture.getHypocenterLocation()));
				} else {
					// subsequent time(s), only need to set the distances
					((ErgodicIMR)gmm).setPropagationEffectParams(dists.value);
				}
				
				for (int p=0; p<periods.size(); p++) {
					periodParam.setValue(periods.get(p));
					gmm.getExceedProbabilities(working[p]);
					for (int i=0; i<avgProbs[p].length; i++)
						avgProbs[p][i] = Math.fma(working[p].getY(i), dists.weight, avgProbs[p][i]);
				}
			}
			
			// clear the eqkRupture object so that we don't leave a stale site-specific corrected instance
			gmm.setEqkRupture(null);
			
			for (int p=0; p<periods.size(); p++)
				for (int i=0; i<avgProbs[p].length; i++)
					exceedProbs[p].set(i, avgProbs[p][i]);
		} else {
			// no special treatment
			gmm.setEqkRupture(eqkRupture);
			
			for (int p=0; p<periods.size(); p++) {
				periodParam.setValue(periods.get(p));
				gmm.getExceedProbabilities(exceedProbs[p]);
			}
		}
	}
	
	public static Basic BASIC_IMPLEMENTATION = new Basic();
	
	public static class Basic implements RuptureSpectraCalculator {

		@Override
		public DiscretizedFunc getSA_ExceedProbSpectrum(ScalarIMR gmm, EqkRupture rupture, double iml) {
			return calcSA_ExceedProbSpectrum(gmm, rupture, iml);
		}

		@Override
		public void getMultiPeriodExceedProbabilities(ScalarIMR gmm, PeriodParam periodParam, List<Double> periods,
				EqkRupture eqkRupture, DiscretizedFunc[] exceedProbs) {
			calcMultiPeriodExceedProbabilities(gmm, periodParam, periods, eqkRupture, exceedProbs);
		}
		
	}
		

}
