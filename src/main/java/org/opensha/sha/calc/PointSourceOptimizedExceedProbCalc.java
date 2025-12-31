package org.opensha.sha.calc;

import static org.opensha.sha.calc.RuptureExceedProbCalculator.*;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.commons.util.interp.DistanceInterpolator.QuickInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.ScalarIMR;

/**
 * Rupture exceedance probability calculator that includes point-source optimizations. For ruptures with
 * a {@link PointSurface}, exceedance probabilities are calculated and cached at fixed distances (those from
 * {@link DistanceInterpolator}) and interpolated. The cache is built dynamically and stored separately for each
 * {@link ScalarIMR} used.
 * 
 * Note that each {@link PointSourceOptimizedExceedProbCalc} may only be used with a single IMT and set of IMRs. It will
 * attempt to detect IMT or IMR parameter changes and throw an exception rather than reuse stale cache values. It can
 * be shared across multiple threads (each with their own IMR instance), and will ensure that each IMR matches the
 * original parameterization. 
 */
public class PointSourceOptimizedExceedProbCalc extends AbstractPointSourceOptimizedCalc implements RuptureExceedProbCalculator {
	
	// exceedance probability cache for point ruptures, unique to each IMR
	private final IMRPointSourceDistanceCache<LightFixedXFunc> exceedProbCache;
	
	public PointSourceOptimizedExceedProbCalc() {
		// true here means to track the SA period
		exceedProbCache = new IMRPointSourceDistanceCache<>(size -> {return new LightFixedXFunc[size];});
	}
	
	private static LightFixedXFunc calcExceedProbsAtDistance(ScalarIMR gmm, EqkRupture eqkRupture,
			DiscretizedFunc exceedProbs, Location sourceLoc, double dist) {
		Location siteLoc;
		if (dist < 1e-10)
			siteLoc = sourceLoc;
		else
			siteLoc = LocationUtils.location(sourceLoc, 0d, dist);
		gmm.setEqkRupture(null); // so that the next call doesn't trigger unnecessary calculations
		gmm.setSiteLocation(siteLoc);
		
		LightFixedXFunc ret;
		if (exceedProbs instanceof LightFixedXFunc)
			ret = ((LightFixedXFunc)exceedProbs).deepClone();
		else
			ret = new LightFixedXFunc(exceedProbs);
		
		calcExceedanceProbabilities(gmm, eqkRupture, ret);
		
		return ret;
	}

	@Override
	public void getExceedProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof PointSurface && !(surf instanceof PointSurface.SiteSpecificDistanceCorrected)) {
			PointSurface pointSurf = (PointSurface)surf;
			Location sourceLoc = pointSurf.getLocation();
			Location origSiteLoc = gmm.getSite().getLocation();
			double dist = pointSurf.getQuickDistance(origSiteLoc);
			
			QuickInterpolator qi = interp.getQuickInterpolator(dist, true); // true here means interpolate in log-distance domain
//			LightFixedXFunc[] cached = getDistCache(gmm, eqkRupture, exceedProbCache);
			
			UniquePointRupture uniqueRup = new UniquePointRupture(eqkRupture);
			UniqueIMR uniqueIMR = getUniqueIMR(gmm);
			UniqueIMT uniqueIMT = new UniqueIMT(gmm, true); // true here means that we're tracking the SA period
			
			LightFixedXFunc[] cached = exceedProbCache.getCached(uniqueIMR, uniqueIMT, uniqueRup);
			
			int index1 = qi.getIndex1();
			boolean calculated = false;
			if (cached[index1] == null) {
				// need to calculate
				cached[index1] = calcExceedProbsAtDistance(gmm, eqkRupture, exceedProbs, sourceLoc, qi.getDistance1());
				calculated = true;
			}
			LightFixedXFunc cached1 = cached[index1];
			quickAssertSameXVals(cached1, exceedProbs);
			if (qi.isDiscrete()) {
				// no interpolation needed
				for (int i=0; i<cached1.size(); i++)
					exceedProbs.set(i, cached1.getY(i));
			} else {
				// need to interpolate
				int index2 = qi.getIndex2();
				if (cached[index2] == null) {
					// need to calculate
					cached[index2] = calcExceedProbsAtDistance(gmm, eqkRupture, exceedProbs, sourceLoc, qi.getDistance2());
					calculated = true;
				}
				LightFixedXFunc cached2 = cached[index2];
				quickAssertSameXVals(cached2, exceedProbs);
				
				for (int i=0; i<cached1.size(); i++)
					exceedProbs.set(i, qi.interpolate(cached1.getY(i), cached2.getY(i)));
			}
			
			if (calculated)
				// we reset the site location, need to change it back
				gmm.setSiteLocation(origSiteLoc);
			
//			if (Math.random() < 0.01)
//				printCacheStats(exceedProbCache);
			return;
		}
		calcExceedanceProbabilities(gmm, eqkRupture, exceedProbs);
	}

}
