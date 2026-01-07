package org.opensha.sha.calc;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

abstract class AbstractPointSourceOptimizedCalc {
	
	private static final boolean D = false;
	
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
			Preconditions.checkState(surf instanceof PointSurface, "Rupture is not a PointSurface: %s", surf);
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
	 * This is used as a key to find the imr-specific cache. It only keys off of the IMR class name, and name.
	 * Parameter values will still be checked each time a new instance is encountered, or a parameter is changed in
	 * an already known instance (see {@link UniqueIMR_Parameterization}.
	 */
	static class UniqueIMR {
		final String className;
		final String name;
		
		private final int hash;
		
		public UniqueIMR(ScalarIMR imr) {
			className = imr.getClass().getName();
			name = imr.getName();
			hash = Objects.hash(className, name);
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
			return hash == other.hash && Objects.equals(className, other.className) && Objects.equals(name, other.name);
		}

		@Override
		public String toString() {
			return "UniqueIMR [className=" + className + ", name=" + name + "]";
		}
	}
	
	/**
	 * More heavy-weight UniqueIMR instance that keeps track of parameter values. We use this to store the reference
	 * parameter values the first time a given IMR is encountered, and also watch for parameter changes to already known
	 * IMRs.
	 */
	static class UniqueIMR_Parameterization extends UniqueIMR implements ParameterChangeListener {
		
		// IMR parameters that we're tracking
		private final ParameterList params;
		
		// if true, indicates that the parameter values stored are cloned and not necessarily still those of the original
		// IMR. It implies that value checks should be redone using the current IMR state, rather than the list above.
		private final boolean isCloned;
		
		// if true, a parameter of this IMR was changed after it was first encountered and we need to do the value
		// check again. we skip that check if this is false because it would be costly to do on every rupture
		volatile boolean paramChanged = false;

		/**
		 * 
		 * @param imr
		 * @param clone if true, parameter values will be cloned and stored as is; used for the first time an IMR
		 * instance is encoutnered
		 * @param listen if false, this is assumed to be a temporary instance and we won't listen for future parameter changes
		 */
		public UniqueIMR_Parameterization(ScalarIMR imr, boolean clone, boolean listen) {
			super(imr);
			isCloned = clone;
			
			String imrName = imr.getShortName();
			
			ParameterList[] inputLists = getInputParamLists(imr);
			
			int capacity = 0;
			for (ParameterList list : inputLists)
				capacity += list.size();
			
			params = new ParameterList(capacity);
			for (ParameterList inputList : inputLists) {
				for (Parameter<?> param : inputList) {
					Preconditions.checkNotNull(param, "Null param found in %s getOtherParams().", imrName);
					for (Parameter<?> depParam : param.getIndependentParameterList()) {
						if (listen)
							depParam.addParameterChangeListener(this);
						if (clone)
							depParam = (Parameter<?>)depParam.clone();
						if (D) System.out.println("\tAdding parameter "+depParam.getName()+" with value '"+depParam.getValue()+"' (clone="+clone+")");
						params.addParameter(depParam);
					}
					if (listen)
						param.addParameterChangeListener(this);
					if (clone) {
						Object copy = param.clone();
						Preconditions.checkNotNull(copy, "Paramter %s clone() returned null for %s", param.getName(), imrName);
						param = (Parameter<?>)copy;
					}
					if (D) System.out.println("\tAdding parameter "+param.getName()+" with value '"+param.getValue()+"' (clone="+clone+")");
					params.addParameter(param);
				}
			}
		}
		
		private static ParameterList[] getInputParamLists(ScalarIMR imr) {
			return new ParameterList[] {
					// "other" params are settings in the IMR that don't change for individual sites/ruptures and
					// must be tracked
					imr.getOtherParams(),
					// also track site parameters (site data), but not the site location itself
					imr.getSiteParams()
			};
		}
		
		void assertIMRParamsMatch(UniqueIMR_Parameterization other) {
			// first time encountering this instance, make sure it's actually the same as the one encountered earlier
			for (Parameter<?> param : params)
				assertIMRParamMatch(param, other.params, name);
		}

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			paramChanged = true;
		}
		
	}
	
	/**
	 * IMT tracker; keeps track of the IMT name and any dependent parameters. It can be configured to track or ignore
	 * the SA {@link PeriodParam}; it should be tracked for regular hazard curves, but ignored for specta calculations
	 * where periods are set and handled externally.
	 */
	static class UniqueIMT {
		final String imtName;
		final String imtParams;
		
		final int hash;
		
		public UniqueIMT(ScalarIMR imr, boolean trackSAPeriod) {
			Parameter<?> imt = imr.getIntensityMeasure();
			Preconditions.checkNotNull(imt, "IMT is null for %s", imr);
			imtName = imt.getName();
			StringBuilder paramsBuilder = null;
			for (Parameter<?> imtParam : imt.getIndependentParameterList()) {
				Preconditions.checkNotNull(imtParam, "Null param found in %s IMT (%).getIndependentParameterList().",
						imr, imt.getName());
				if (!trackSAPeriod && imtParam instanceof PeriodParam)
					continue;
				if (paramsBuilder == null)
					paramsBuilder = new StringBuilder();
				else
					paramsBuilder.append("; ");
				paramsBuilder.append(imtParam.getName()).append("=").append(imtParam.getValue());
			}
			imtParams = paramsBuilder == null ? null : paramsBuilder.toString();
			hash = Objects.hash(imtName, imtParams);
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
			if (!(obj instanceof UniqueIMT))
				return false;
			UniqueIMT other = (UniqueIMT) obj;
			return hash == other.hash && Objects.equals(imtName, other.imtName) && Objects.equals(imtParams, other.imtParams);
		}

		@Override
		public String toString() {
			return "UniqueIMt [imtName=" + imtName + ", imtParams=" + imtParams + "]";
		}
	}
	
	private static void assertIMRParamMatch(Parameter<?> param, ParameterList newList, String name) {
		Preconditions.checkState(newList.containsParameter(param.getName()),
				"We encountered a new instance of %s, but it doesn't have parameter '%s'",
				name, param.getName());
		Object refValue = param.getValue();
		Object newValue = newList.getValue(param.getName());
		if (D) System.out.println("\tChecking "+param.getName()+": '"+refValue+"'\t'"+newValue+"'");
		Preconditions.checkState(Objects.equals(newValue, refValue),
				"We encountered a new instance of %s, but parameter '%s' varies: '%s' != '%s'",
				name, param.getName(), newValue, refValue);
	}
	
	// distance interpolator; this sets the distance bins and allows for quick interpolation between them
	protected final DistanceInterpolator interp = DistanceInterpolator.get();
	protected final int interpSize = interp.size();
	
	// This stores the reference IMR instance for any UniqueIMR; when other instances are encountered, we will check
	// parameters of the new one against the version we have here
	protected final ConcurrentMap<UniqueIMR, ScalarIMR> imrInstanceCheckMap;
	// This is used to see if a passed in IMR is one we've encountered already
	protected final ConcurrentMap<ScalarIMR, UniqueIMR_Parameterization> imrInstanceReverseCheckMap;
	
	public AbstractPointSourceOptimizedCalc() {
		imrInstanceCheckMap = new ConcurrentHashMap<>();
		imrInstanceReverseCheckMap = new ConcurrentHashMap<>();
	}
	
	/**
	 * Cache instance that returns a distance-binned cache array for each unique configuration of IMR, IMT, and rupture
	 * @param <E>
	 */
	class IMRPointSourceDistanceCache<E> {
		
		private ConcurrentMap<UniqueIMR, ConcurrentMap<UniqueIMT, ConcurrentMap<UniquePointRupture, E[]>>> cache;
		private Function<Integer, E[]> arrayBuilder;
		
		private volatile PrevIMR_IMT_Container<E> previous;
		
		public IMRPointSourceDistanceCache(Function<Integer, E[]> arrayBuilder) {
			this.arrayBuilder = arrayBuilder;
			this.cache = new ConcurrentHashMap<>();
		}
		
		public E[] getCached(UniqueIMR uniqueIMR, UniqueIMT uniqueIMT, UniquePointRupture uniqueRup) {
			final PrevIMR_IMT_Container<E> previous = this.previous;
			if (previous != null && uniqueIMR.equals(previous.uniqueIMR) && uniqueIMT.equals(previous.uniqueIMT)) {
				// shortcut that will speed up single IMR/IMT calculations
				E[] cached = previous.cache.get(uniqueRup);
				if (cached == null) {
					previous.cache.putIfAbsent(uniqueRup, arrayBuilder.apply(interpSize));
					cached = previous.cache.get(uniqueRup);
				}
				return cached;
			}
			ConcurrentMap<UniqueIMT, ConcurrentMap<UniquePointRupture, E[]>> imrCache = cache.get(uniqueIMR);
			if (imrCache == null) {
				// first time we've encountered this IMR
				cache.putIfAbsent(uniqueIMR, new ConcurrentHashMap<>());
				imrCache = cache.get(uniqueIMR);
			}
			Preconditions.checkNotNull(imrCache, "No IMR cache for: %s", uniqueIMR);
			ConcurrentMap<UniquePointRupture, E[]> imtCache = imrCache.get(uniqueIMT);
			if (imtCache == null) {
				imrCache.putIfAbsent(uniqueIMT, new ConcurrentHashMap<>(1));
				imtCache = imrCache.get(uniqueIMT);
			}
			E[] cached = imtCache.get(uniqueRup);
			if (cached == null) {
				imtCache.putIfAbsent(uniqueRup, arrayBuilder.apply(interpSize));
				cached = imtCache.get(uniqueRup);
			}
			this.previous = new PrevIMR_IMT_Container<>(uniqueIMR, uniqueIMT, imtCache);
			return cached;
		}
		
	}
	
	private static class PrevIMR_IMT_Container<E> {
		final UniqueIMR uniqueIMR;
		final UniqueIMT uniqueIMT;
		final ConcurrentMap<UniquePointRupture, E[]> cache;
		
		private PrevIMR_IMT_Container(UniqueIMR uniqueIMR, UniqueIMT uniqueIMT,
				ConcurrentMap<UniquePointRupture, E[]> cache) {
			super();
			this.uniqueIMR = uniqueIMR;
			this.uniqueIMT = uniqueIMT;
			this.cache = cache;
		}
	}
	
	protected <E> void printCacheStats(IMRPointSourceDistanceCache<E> cache) {
		int numIMRinstances = imrInstanceCheckMap.size();
		int numIMRreverse = imrInstanceReverseCheckMap.size();
		int numUniqueIMRs = cache.cache.size();
		int numUniqueIMTs = 0;
		int numUniqueRups = 0;
		for (UniqueIMR uniqueIMR : new ArrayList<>(cache.cache.keySet())) {
			ConcurrentMap<UniqueIMT, ConcurrentMap<UniquePointRupture, E[]>> imrCache = cache.cache.get(uniqueIMR);
			for (UniqueIMT uniqueIMT : new ArrayList<>(imrCache.keySet())) {
				numUniqueIMTs++;
				ConcurrentMap<UniquePointRupture, E[]> imtCache = imrCache.get(uniqueIMT);
				numUniqueRups += imtCache.size();
			}
		}
		System.out.println("Point Source cache stats: " + "\n\tIMR instances tracked: " + numIMRinstances
				+ "\n\tIMR instances reverse tracked: " + numIMRreverse + "\n\tUnique IMRs: " + numUniqueIMRs
				+ "\n\tUnique IMTs: " + numUniqueIMTs + "\n\tUnique Rups: " + numUniqueRups);
	}
	
	/**
	 * This is the crux of the class; it returns a unique cache key ({@link UniqueIMR}) for the passed in
	 * {@link ScalarIMR}, and does various consistency checks to ensure that the cache is not stale.
	 * 
	 * We ensure that the given IMR matches the parameterization of the first IMR of that instance encountered.
	 * If multiple threads use this same calculator with separate IMR instances, we need to make sure they're all
	 * parameterized the same (otherwise the cache could contain values generated with other parameterizations).
	 * 
	 * This also applies to using the same IMR for multiple TRTs; currently we assume that the IMRs would be
	 * parameterized identically, and this ensures that.
	 * 
	 * You can safely change the IMT for an IMR and reuse the same cache because values are stored separately for each
	 * {@link UniqueIMR} and {@link UniqueIMT} pair.
	 * 
	 * @param imr
	 * @return unique cache key for the given IMR
	 */
	protected UniqueIMR getUniqueIMR(ScalarIMR imr) {
		if (D) System.out.println("getUniqueIMR for "+imr);
		ScalarIMR refIMR = imrInstanceCheckMap.get(new UniqueIMR(imr));
		if (refIMR == null) {
			// we have never encountered any instance of this IMR
			
			synchronized (imrInstanceReverseCheckMap) {
				// make sure another thread didn't put it in instead
				refIMR = imrInstanceCheckMap.get(new UniqueIMR(imr));
				if (refIMR == null) {
					if (D) System.out.println("getUniqueIMR it's our first instance of "+imr.getName());
					// store it in the cache, and clone all parameters so that their settings are stored as is
					UniqueIMR_Parameterization unique = new UniqueIMR_Parameterization(imr, true, true);
					imrInstanceReverseCheckMap.put(imr, unique);
					imrInstanceCheckMap.put(unique, imr);
					return unique;
				}
			}
		}
		// this is the full parameterization of the passed in IMR instance, if we've encountered that instance
		UniqueIMR_Parameterization myUnique = imrInstanceReverseCheckMap.get(imr);
		if (myUnique == null) {
			synchronized (imrInstanceReverseCheckMap) {
				// make sure another thread didn't put it in instead
				myUnique = imrInstanceReverseCheckMap.get(imr);
				if (myUnique == null) {
					// first time encountering this IMR instance, make sure it's the same as the original
					if (D) System.out.println("getUniqueIMR new instance of "+imr.getName()+"; will validate against "+refIMR);
					
					// this is the original reference parameterization for this IMR; we'll use it to make sure that this
					// instance matches it
					UniqueIMR_Parameterization refUnique = imrInstanceReverseCheckMap.get(refIMR);
					
					// false here means don't clone parameters; we only need to clone the original reference values 
					// true here means listen to parameter changes so that we know to run checks again if things change in the future
					myUnique = new UniqueIMR_Parameterization(imr, false, true);
					
					// ensure that we match the reference parameters
					refUnique.assertIMRParamsMatch(myUnique);
					
					// store the parameterization of this IMR instance, which will track any subsequent changes
					imrInstanceReverseCheckMap.put(imr, myUnique);
					return myUnique;
				}
			}
		}
		// if we're this far, then we have encountered this exact imr instance before
		if (myUnique.paramChanged) {
			// a parameter might have changed, make sure we're still the same
			
			// this is the original reference parameterization for this IMR; we'll use it to make sure that this
			// instance matches it
			UniqueIMR_Parameterization refUnique = imrInstanceReverseCheckMap.get(refIMR);
			if (myUnique.isCloned) {
				// myUnique contains the original values (cloned), not the current
				// check against the current values
				refUnique.assertIMRParamsMatch(new UniqueIMR_Parameterization(imr, false, false));
				myUnique.paramChanged = false;
			} else {
				refUnique.assertIMRParamsMatch(myUnique);
				myUnique.paramChanged = false;
			}
		}
		return myUnique;
	}
	
	protected static void quickAssertSameXVals(DiscretizedFunc cached, DiscretizedFunc passedIn) {
		Preconditions.checkState(cached.size() == passedIn.size()
				&& Precision.equals(cached.getMinX(), passedIn.getMinX(), 1e-10)
				&& Precision.equals(cached.getMaxX(), passedIn.getMaxX(), 1e-10),
				"Passed in exceedance probabilities differs from cached version; must use consistent x values");
	}

}
