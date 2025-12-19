package org.opensha.sha.calc;

import static org.opensha.sha.calc.RuptureSpectraCalculator.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.interp.DistanceInterpolator.QuickInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class PointSourceOptimizedSpectraCalc extends AbstractPointSourceOptimizedCalc implements RuptureSpectraCalculator {
	
	// exceedance probability spectra cache for point ruptures, unique to each IMR
	private final Map<UniqueIMR, ConcurrentMap<UniquePointRupture, LightFixedXFunc[]>> saExceedProbSpectraCache;
	private final Map<UniqueIMR, ConcurrentMap<UniquePointRupture, LightFixedXFunc[][]>> exceedProbCache;

	public PointSourceOptimizedSpectraCalc(ScalarIMR imr) {
		this(Map.of(TectonicRegionType.ACTIVE_SHALLOW, imr));
	}

	public PointSourceOptimizedSpectraCalc(Map<TectonicRegionType, ScalarIMR> imrMap) {
		super(imrMap, false);
		if (imrMap.size() == 1) {
			UniqueIMR_Parameterization unique = imrInstanceReverseCheckMap.values().iterator().next();
			saExceedProbSpectraCache = Map.of(unique, new ConcurrentHashMap<>());
			exceedProbCache = Map.of(unique, new ConcurrentHashMap<>());
		} else {
			saExceedProbSpectraCache = new HashMap<>(imrInstanceCheckMap.size());
			exceedProbCache = new HashMap<>(imrInstanceCheckMap.size());
			for (UniqueIMR unique : imrInstanceCheckMap.keySet()) {
				saExceedProbSpectraCache.put(unique, new ConcurrentHashMap<>());
				exceedProbCache.put(unique, new ConcurrentHashMap<>());
			}
		}
	}
	
	private static LightFixedXFunc calcExceedProbSpectrumAtDistance(ScalarIMR gmm, EqkRupture eqkRupture,
			double iml, Location sourceLoc, double dist) {
		Location siteLoc;
		if (dist < 1e-10)
			siteLoc = sourceLoc;
		else
			siteLoc = LocationUtils.location(sourceLoc, 0d, dist);
		gmm.setEqkRupture(null); // so that the next call doesn't trigger unnecessary calculations
		gmm.setSiteLocation(siteLoc);
		
		return calcSA_ExceedProbSpectrum(gmm, eqkRupture, iml);
	}

	@Override
	public DiscretizedFunc getSA_ExceedProbSpectrum(ScalarIMR gmm, EqkRupture eqkRupture, double iml) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof PointSurface && !(surf instanceof PointSurface.SiteSpecificDistanceCorrected)) {
			PointSurface pointSurf = (PointSurface)surf;
			Location sourceLoc = pointSurf.getLocation();
			Location origSiteLoc = gmm.getSite().getLocation();
			double dist = pointSurf.getQuickDistance(origSiteLoc);
			
			QuickInterpolator qi = interp.getQuickInterpolator(dist, true); // true here means interpolate in log-distance domain
			LightFixedXFunc[] cached = getDistCache(gmm, eqkRupture, saExceedProbSpectraCache);
			
			int index1 = qi.getIndex1();
			boolean calculated = false;
			if (cached[index1] == null) {
				// need to calculate
				cached[index1] = calcExceedProbSpectrumAtDistance(gmm, eqkRupture, iml, sourceLoc, qi.getDistance1());
				calculated = true;
			}
			LightFixedXFunc cached1 = cached[index1];
			LightFixedXFunc ret = new LightFixedXFunc(cached1.getXVals(), new double[cached1.size()]);
			if (!qi.isDiscrete()) {
				// need to interpolate
				int index2 = qi.getIndex2();
				if (cached[index2] == null) {
					// need to calculate
					cached[index2] = calcExceedProbSpectrumAtDistance(gmm, eqkRupture, iml, sourceLoc, qi.getDistance2());
					calculated = true;
				}
				LightFixedXFunc cached2 = cached[index2];
				quickAssertSameXVals(cached2, ret);
				
				for (int i=0; i<cached1.size(); i++)
					ret.set(i, qi.interpolate(cached1.getY(i), cached2.getY(i)));
			}
			
			if (calculated)
				// we reset the site location, need to change it back
				gmm.setSiteLocation(origSiteLoc);
			return ret;
		}
		return calcSA_ExceedProbSpectrum(gmm, eqkRupture, iml);
	}
	
	private static LightFixedXFunc[] calcMultiPeriodExceedProbsAtDistance(ScalarIMR gmm, PeriodParam periodParam,
			List<Double> periods, EqkRupture eqkRupture, DiscretizedFunc[] exceedProbs, Location sourceLoc, double dist) {
		Preconditions.checkState(periods.size() == exceedProbs.length);
		Location siteLoc;
		if (dist < 1e-10)
			siteLoc = sourceLoc;
		else
			siteLoc = LocationUtils.location(sourceLoc, 0d, dist);
		gmm.setEqkRupture(null); // so that the next call doesn't trigger unnecessary calculations
		gmm.setSiteLocation(siteLoc);
		
		LightFixedXFunc[] ret = new LightFixedXFunc[exceedProbs.length];
		for (int i=0; i<ret.length; i++) {
			if (exceedProbs[i] instanceof LightFixedXFunc)
				ret[i] = (LightFixedXFunc)exceedProbs[i].deepClone();
			else
				ret[i] = new LightFixedXFunc(exceedProbs[i]);
		}
		calcMultiPeriodExceedProbabilities(gmm, periodParam, periods, eqkRupture, ret);
		
		return ret;
	}

	@Override
	public void getMultiPeriodExceedProbabilities(ScalarIMR gmm, PeriodParam periodParam, List<Double> periods,
			EqkRupture eqkRupture, DiscretizedFunc[] exceedProbs) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof PointSurface && !(surf instanceof PointSurface.SiteSpecificDistanceCorrected)) {
			PointSurface pointSurf = (PointSurface)surf;
			Location sourceLoc = pointSurf.getLocation();
			Location origSiteLoc = gmm.getSite().getLocation();
			double dist = pointSurf.getQuickDistance(origSiteLoc);
			
			int numPeriods = periods.size();
			Preconditions.checkState(periods.size() == exceedProbs.length);
			
			QuickInterpolator qi = interp.getQuickInterpolator(dist, true); // true here means interpolate in log-distance domain
			LightFixedXFunc[][] cached = getMultiPeriodDistCache(gmm, eqkRupture, exceedProbCache);
			
			int index1 = qi.getIndex1();
			boolean calculated = false;
			if (cached[index1] == null) {
				// need to calculate
				cached[index1] = calcMultiPeriodExceedProbsAtDistance(gmm, periodParam, periods, eqkRupture, exceedProbs, sourceLoc, qi.getDistance1());
				calculated = true;
			}
			LightFixedXFunc[] cached1 = cached[index1];
			quickAssertSameXVals(cached1[0], exceedProbs[0]);
			if (qi.isDiscrete()) {
				// no interpolation needed
				for (int p=0; p<numPeriods; p++)
					for (int i=0; i<cached1[p].size(); i++)
						exceedProbs[p].set(i, cached1[p].getY(i));
			} else {
				// need to interpolate
				int index2 = qi.getIndex2();
				if (cached[index2] == null) {
					// need to calculate
					cached[index2] = calcMultiPeriodExceedProbsAtDistance(gmm, periodParam, periods, eqkRupture, exceedProbs, sourceLoc, qi.getDistance2());
					calculated = true;
				}
				LightFixedXFunc[] cached2 = cached[index2];
				quickAssertSameXVals(cached2[0], exceedProbs[0]);
				
				for (int p=0; p<numPeriods; p++)
					for (int i=0; i<cached1[p].size(); i++)
						exceedProbs[p].set(i, qi.interpolate(cached1[p].getY(i), cached2[p].getY(i)));
			}
			
			if (calculated)
				// we reset the site location, need to change it back
				gmm.setSiteLocation(origSiteLoc);
			return;
		}
		calcMultiPeriodExceedProbabilities(gmm, periodParam, periods, eqkRupture, exceedProbs);
	}

}
