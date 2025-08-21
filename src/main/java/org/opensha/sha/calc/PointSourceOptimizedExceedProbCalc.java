package org.opensha.sha.calc;

import static org.opensha.sha.calc.RuptureExceedProbCalculator.calcExceedanceProbabilities;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.commons.util.interp.DistanceInterpolator.QuickInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

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
public class PointSourceOptimizedExceedProbCalc implements RuptureExceedProbCalculator {
	
	/**
	 * These properties define a unique point source rupture, for which conditional exceedance probabilities will be
	 * calculated and cached separately.
	 * 
	 * If we introduce any new fields that affect exceedance probabilities for a point surface ruptures, they must be
	 * added here (and also added to the hashCode and equals methods)
	 */
	static class UniquePointRupture {
		private final double mag;
		private final double rake;
		private final double zTOR;
		private final double length;
		private final double width;
		private final double dip;
		private final PointSourceDistanceCorrection distCorr;
		private final TectonicRegionType trt;
		private final double zHyp;
		private final int hash;
		public UniquePointRupture(EqkRupture rup) {
			this.mag = rup.getMag();
			this.rake = rup.getAveRake();
			RuptureSurface surf = rup.getRuptureSurface();
			Preconditions.checkState(surf instanceof PointSurface);
			this.zTOR = surf.getAveRupTopDepth();
			this.length = surf.getAveLength();
			this.width = surf.getAveWidth();
			this.dip = surf.getAveDip();
			if (surf instanceof PointSurface.DistanceCorrectionAttached) {
				this.distCorr = ((PointSurface.DistanceCorrectionAttached)surf).getDistanceCorrection();
				this.trt = ((PointSurface.DistanceCorrectionAttached)surf).getTectonicRegionType();
			} else {
				this.distCorr = null;
				this.trt = null;
			}
			Location hypo = rup.getHypocenterLocation();
			zHyp = hypo == null ? 0.5*(surf.getAveRupTopDepth()+surf.getAveRupBottomDepth()) : hypo.getDepth();
			hash = Objects.hash(dip, mag, rake, length, width, zTOR, distCorr, trt, zHyp);
		}
		@Override
		public int hashCode() {
			return hash;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniquePointRupture other = (UniquePointRupture) obj;
			return hash == other.hash && distCorr == other.distCorr && trt == other.trt
					&& Double.doubleToLongBits(zHyp) == Double.doubleToLongBits(other.zHyp)
					&& Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
					&& Double.doubleToLongBits(mag) == Double.doubleToLongBits(other.mag)
					&& Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
					&& Double.doubleToLongBits(length) == Double.doubleToLongBits(other.length)
					&& Double.doubleToLongBits(width) == Double.doubleToLongBits(other.width)
					&& Double.doubleToLongBits(zTOR) == Double.doubleToLongBits(other.zTOR);
		}
	}
	
	/**
	 * This is used as a key to find the imr-specific cache. It only keys off of the IMR class name, name, and IMT name.
	 * Parameter values will still be checked each time a new instance is encountered (see {@link UniqueIMR_Parameterization}.
	 */
	static class UniqueIMR {
		final String className;
		final String name;
		final String imtName;
		
		private final int hash;
		
		public UniqueIMR(ScalarIMR imr) {
			className = imr.getClass().getName();
			name = imr.getName();
			imtName = imr.getIntensityMeasure().getName();
			hash = Objects.hash(className, name, imtName);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof UniqueIMR))
				return false;
			UniqueIMR other = (UniqueIMR) obj;
			return hash == other.hash && Objects.equals(className, other.className) && Objects.equals(name, other.name)
					&& Objects.equals(imtName, other.imtName);
		}
	}
	
	/**
	 * More heavy-weight UniqueIMR instance that keeps track of parameter values
	 */
	static class UniqueIMR_Parameterization extends UniqueIMR implements ParameterChangeListener {
		
		private ParameterList params;
		private ParameterList imtParams;
		// if true, indicates that the parameter values stored are clone and not necessarily still those of the original
		// IMR. It implies that value checks should be redone using the current IMR state
		private boolean isCloned;
		
		// if true, a parameter of this IMR was changed after it was first encountered and we need to do the value
		// check again. we skip that check if this is false because it would be costly to do on every rupture
		volatile boolean paramChanged = false;

		public UniqueIMR_Parameterization(ScalarIMR imr, boolean clone, boolean listen) {
			super(imr);
			isCloned = clone;
			
			params = new ParameterList();
			for (Parameter<?> param : imr.getOtherParams()) {
				for (Parameter<?> depParam : param.getIndependentParameterList()) {
					if (listen)
						depParam.addParameterChangeListener(this);
					if (clone)
						depParam = (Parameter<?>)depParam.clone();
					params.addParameter(depParam);
				}
				if (listen)
					param.addParameterChangeListener(this);
				if (clone)
					param = (Parameter<?>)param.clone();
				params.addParameter(param);
			}
			
			// the IMT itself is checked in upstream UniqueIMR we don't want to check the IMT value, it is supposed to
			// change, but we do want to check any dependent parameters and their values
			Parameter<?> imt = imr.getIntensityMeasure();
			imtParams = new ParameterList();
			for (Parameter<?> imtParam : imt.getIndependentParameterList()) {
				if (listen)
					imtParam.addParameterChangeListener(this);
				if (clone)
					imtParam = (Parameter<?>)imtParam.clone();
				imtParams.addParameter(imtParam);
			}
		}
		
		void assertIMRParamsMatch(UniqueIMR_Parameterization other) {
			// first time encountering this instance, make sure it's actually the same as the one encountered earlier
			for (Parameter<?> param : params)
				assertIMRParamMatch(param, other.params);
			for (Parameter<?> param : imtParams)
				assertIMRParamMatch(param, other.imtParams);
		}
		
		void assertIMRParamMatch(Parameter<?> param, ParameterList newList) {
			Preconditions.checkState(newList.containsParameter(param.getName()),
					"We encountered a new instance of %s, but it doesn't have parameter '%s'",
					name, param.getName());
			Object refValue = param.getValue();
			Object newValue = newList.getValue(param.getName());
			Preconditions.checkState(Objects.equals(newValue, refValue),
					"We encountered a new instance of %s, but parameter '%s' varies: '%s' != '%s'",
					name, param.getName(), newValue, refValue);
		}

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			paramChanged = true;
		}
		
	}
	
	// distance interpolator; this sets the distance bins and allows for quick interpolation between them
	private final DistanceInterpolator interp = DistanceInterpolator.get();
	private final int interpSize = interp.size();
	
	// exceedance probability cache for point ruptures, unique to each IMR
	private final Map<UniqueIMR, ConcurrentMap<UniquePointRupture, LightFixedXFunc[]>> cache;
	// This stores the reference IMR instance for any UniqueIMR; when other instances are encountered, we will check
	// parameters of the new one against the version we have here
	private final Map<UniqueIMR, ScalarIMR> imrInstanceCheckMap;
	// This is used to see if a passed in IMR is one we've encountered already
	private final Map<ScalarIMR, UniqueIMR_Parameterization> imrInstanceReverseCheckMap;
	
	public PointSourceOptimizedExceedProbCalc(Map<TectonicRegionType, ScalarIMR> imrMap) {
		if (imrMap.size() == 1) {
			ScalarIMR imr = imrMap.values().iterator().next();
			UniqueIMR_Parameterization unique = new UniqueIMR_Parameterization(imr, true, true);
			cache = Map.of(unique, new ConcurrentHashMap<>());
			imrInstanceCheckMap = Map.of(unique, imr);
			imrInstanceReverseCheckMap = new HashMap<>(Map.of(imr, unique));
		} else {
			cache = new HashMap<>(imrMap.size());
			imrInstanceCheckMap = new HashMap<>(imrMap.size());
			imrInstanceReverseCheckMap = new HashMap<>(imrMap.size());
			for (ScalarIMR imr : imrMap.values()) {
				UniqueIMR unique = getUniqueIMR(imr, true);
				if (!cache.containsKey(unique)) {
					cache.put(unique, new ConcurrentHashMap<>());
					imrInstanceCheckMap.put(unique, imr);
				}
			}
		}
	}
	
	/**
	 * This will ensure that the given IMR matches the parameterization of the IMR used to build this cache. If multiple
	 * threads use this same calculator with separate IMR instances, we want to make sure they're all parameterized the
	 * same (otherwise the cache could contain values generated with other parameterizations).
	 * 
	 * This also applies to using the same IMR for multiple TRTs; currently we assume that the IMRs would be
	 * parameterized identically, this ensures that.
	 */
	private UniqueIMR getUniqueIMR(ScalarIMR imr, boolean allowNew) {
		ScalarIMR refIMR = imrInstanceCheckMap.get(new UniqueIMR(imr));
		if (refIMR == null) {
			// we have never encountered this IMR, or the IMT changed in it
			if (allowNew) {
				// this is expected during construction, go ahead and add it
				UniqueIMR_Parameterization unique = new UniqueIMR_Parameterization(imr, true, true);
				imrInstanceReverseCheckMap.put(imr, unique);
				return unique;
			}
			StringBuilder exception = new StringBuilder("Unexpected IMR passed to PointSourceOptimizedExceedProbCalc that was not "
					+ "used in initialization: '").append(imr.getName()).append("' w/ imt='").append(imr.getIntensityMeasure().getName());
			exception.append("\nAvailable IMRs:");
			for (UniqueIMR unique : imrInstanceCheckMap.keySet())
				exception.append("\n\t'"+unique.name+"' w/ imt='"+unique.imtName+"'");
			throw new RuntimeException(exception.toString());
		}
		UniqueIMR_Parameterization myUnique = imrInstanceReverseCheckMap.get(imr);
		if (myUnique == null) {
			synchronized (imrInstanceReverseCheckMap) {
				// make sure another thread didn't put it in instead
				myUnique = imrInstanceReverseCheckMap.get(imr);
				if (myUnique == null) {
					// first time encountering this IMR instance, make sure it's actually the same as the original
					UniqueIMR_Parameterization refUnique = imrInstanceReverseCheckMap.get(refIMR);
					// false here means don't clone parameters; we only need to clone the original reference values 
					myUnique = new UniqueIMR_Parameterization(refIMR, false, true);
					refUnique.assertIMRParamsMatch(myUnique);
					// store the initial parameterization of this IMR
					imrInstanceReverseCheckMap.put(imr, myUnique);
					return myUnique;
				}
			}
		}
		if (myUnique.paramChanged) {
			// we've encountered this before, but a parameter might have changed
			UniqueIMR_Parameterization refUnique = imrInstanceReverseCheckMap.get(refIMR);
			if (myUnique.isCloned) {
				refUnique.assertIMRParamsMatch(new UniqueIMR_Parameterization(imr, false, false));
				myUnique.paramChanged = false;
			} else {
				refUnique.assertIMRParamsMatch(myUnique);
				myUnique.paramChanged = false;
			}
		}
		return myUnique;
	}
	
	private LightFixedXFunc[] getDistCache(ScalarIMR gmm, EqkRupture eqkRupture) {
		UniquePointRupture unique = new UniquePointRupture(eqkRupture);
		UniqueIMR uniqueIMR = getUniqueIMR(gmm, false);
		
		ConcurrentMap<UniquePointRupture, LightFixedXFunc[]> imrCache = cache.get(uniqueIMR);
		Preconditions.checkNotNull(imrCache, "No IMR cache for: %s", gmm);
		LightFixedXFunc[] cached = imrCache.get(unique);
		if (cached == null) {
			imrCache.putIfAbsent(unique, new LightFixedXFunc[interpSize]);
			cached = imrCache.get(unique);
		}
		return cached;
	}
	
	private static LightFixedXFunc calcAtDistance(ScalarIMR gmm, EqkRupture eqkRupture,
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
	
	private static void quickAssertSameXVals(DiscretizedFunc cached, DiscretizedFunc passedIn) {
		Preconditions.checkState(cached.size() == passedIn.size()
				&& Precision.equals(cached.getMinX(), passedIn.getMinX(), 1e-10)
				&& Precision.equals(cached.getMaxX(), passedIn.getMaxX(), 1e-10),
				"Passed in exceedance probabilities differs from cached version; must use consistent x values");
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
			LightFixedXFunc[] cached = getDistCache(gmm, eqkRupture);
			
			int index1 = qi.getIndex1();
			boolean calculated = false;
			if (cached[index1] == null) {
				// need to calculate
				cached[index1] = calcAtDistance(gmm, eqkRupture, exceedProbs, sourceLoc, qi.getDistance1());
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
					cached[index2] = calcAtDistance(gmm, eqkRupture, exceedProbs, sourceLoc, qi.getDistance2());
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
			return;
		}
		calcExceedanceProbabilities(gmm, eqkRupture, exceedProbs);
	}

}
