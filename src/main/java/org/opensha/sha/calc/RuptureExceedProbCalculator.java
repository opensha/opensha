package org.opensha.sha.calc;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.PointSurface.DistanceCorrectable;
import org.opensha.sha.faultSurface.PointSurface.SiteSpecificDistanceCorrected;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.imr.ScalarIMR;

import com.google.common.base.Preconditions;

/**
 * Interface for calculating exceedance probabilities. This takes care of any point-source distance corrections,
 * and allows for point-source optimizations (i.e., interpolated exceedance probabilities for point-source ruptures). 
 */
public interface RuptureExceedProbCalculator {
	
	public void getExceedProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs);
	
	static void calcExceedanceProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof DistanceCorrectable) {
			// point surface with distance corrections
			Location siteLoc = gmm.getSite().getLocation();
			WeightedList<SurfaceDistances> surfs = ((PointSurface.DistanceCorrectable)surf).getCorrectedDistances(siteLoc);
			Preconditions.checkState(surfs.isNormalized());
			
			LightFixedXFunc working;
			if (exceedProbs instanceof LightFixedXFunc)
				working = (LightFixedXFunc)exceedProbs;
			else
				working = new LightFixedXFunc(exceedProbs);
			
			double[] avgProbs = new double[working.size()];
			for (int s=0; s<surfs.size(); s++) {
				WeightedValue<SurfaceDistances> dists = surfs.get(s);
				
				if (s == 0) {
					// first time, need to set the full rupture
					SiteSpecificDistanceCorrected corrSurf = new SiteSpecificDistanceCorrected((PointSurface)surf, siteLoc, dists.value);
					gmm.setEqkRupture(new EqkRupture(eqkRupture.getMag(), eqkRupture.getAveRake(), corrSurf, eqkRupture.getHypocenterLocation()));
				} else {
					// subsequent time(s), only need to set the distances
					gmm.setPropagationEffectParams(dists.value);
				}
				
				gmm.getExceedProbabilities(working);
				
				for (int i=0; i<avgProbs.length; i++)
					avgProbs[i] = Math.fma(working.getY(i), dists.weight, avgProbs[i]);
			}
			
			// clear the eqkRupture object so that we don't leave a stale site-specific corrected instance
			gmm.setEqkRupture(null);
			
			for (int i=0; i<avgProbs.length; i++)
				exceedProbs.set(i, avgProbs[i]);
		} else {
			// no special treatment
			gmm.setEqkRupture(eqkRupture);
			
			gmm.getExceedProbabilities(exceedProbs);
		}
	}
	
	public static class Basic implements RuptureExceedProbCalculator {

		@Override
		public void getExceedProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs) {
			calcExceedanceProbabilities(gmm, eqkRupture, exceedProbs);
		}
		
	}

}
