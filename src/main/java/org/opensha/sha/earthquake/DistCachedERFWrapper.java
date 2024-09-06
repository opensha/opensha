package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.CustomCacheWrappedSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Utility class that wraps an ERF such that each source/rupture surface has it's own single-valued distance cache.
 * This can be useful to avoid thread contention in multhreaded hazard calculations that reuse the same ERF across
 * threads. This optimization only applies to {@link FaultRuptureSource} source implementations, or custom implementations
 * where each rupture surface implements {@link CacheEnabledSurface}.
 * 
 * The simplest method would be to instantiate a different ERF for each thread, but sometimes that isn't practical
 * due to memory constraints. Use this if re-instantiating ERFs is not practical.
 * 
 * @author kevin
 *
 */
public class DistCachedERFWrapper extends AbstractERF {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private AbstractERF erf;
	
	private List<ProbEqkSource> sources = null;

	public DistCachedERFWrapper(AbstractERF erf) {
		this.erf = erf;
	}

	@Override
	public int getNumSources() {
		return erf.getNumSources();
	}
	
	public AbstractERF getOriginalERF() {
		return erf;
	}
	
	private void initSources() {
		List<ProbEqkSource> sources = new ArrayList<>();
		Map<RuptureSurface, CustomCacheWrappedSurface> wrappedMap = new HashMap<>();
		for (ProbEqkSource source : erf) {
			if (source instanceof FaultRuptureSource) {
				RuptureSurface sourceSurf = getWrappedSurface(wrappedMap, source.getSourceSurface());
				
				List<ProbEqkRupture> rups = new ArrayList<>();
				for (ProbEqkRupture origRup : source) {
					RuptureSurface rupSurf = getWrappedSurface(wrappedMap, origRup.getRuptureSurface());
					rups.add(new ProbEqkRupture(origRup.getMag(), origRup.getAveRake(),
							origRup.getProbability(), rupSurf, origRup.getHypocenterLocation()));
				}
				sources.add(new CustomSource(source, sourceSurf, rups));
			} else {
				// see if everything is cache-enabled
				boolean cacheEnabled = true;
				RuptureSurface sourceSurf = null;
				try {
					sourceSurf = source.getSourceSurface();
					cacheEnabled = sourceSurf == null || sourceSurf instanceof CacheEnabledSurface;
				} catch (Exception e) {}
				for (int rupID=0; cacheEnabled && rupID<source.getNumRuptures(); rupID++)
					cacheEnabled = cacheEnabled && source.getRupture(rupID).getRuptureSurface() instanceof CacheEnabledSurface;
				if (cacheEnabled) {
					List<ProbEqkRupture> rups = new ArrayList<>();
					for (ProbEqkRupture origRup : source) {
						RuptureSurface rupSurf = getWrappedSurface(wrappedMap, origRup.getRuptureSurface());
						rups.add(new ProbEqkRupture(origRup.getMag(), origRup.getAveRake(),
								origRup.getProbability(), rupSurf, origRup.getHypocenterLocation()));
					}
					sources.add(new CustomSource(source, sourceSurf, rups));
				} else {
					// will retrieve directly from ERF when queried
					sources.add(null);
				}
			}
		}
		this.sources = sources;
	}
	
	public static class DistCacheWrapperRupture extends ProbEqkRupture {
		
		private ProbEqkRupture origRup;

		public DistCacheWrapperRupture(ProbEqkRupture origRup, RuptureSurface wrappedSurf) {
			super(origRup.getMag(), origRup.getAveRake(), origRup.getProbability(), wrappedSurf, origRup.getHypocenterLocation());
			this.origRup = origRup;
		}
		
		public ProbEqkRupture getOriginalRupture() {
			return origRup;
		}
	}
	
	private static RuptureSurface getWrappedSurface(Map<RuptureSurface, CustomCacheWrappedSurface> wrappedMap,
			RuptureSurface origSurf) {
		RuptureSurface wrappedSurf;
		if (wrappedMap.containsKey(origSurf))
			// already encountered (duplicate surface)
			return wrappedMap.get(origSurf);
		// need to wrap it here
		if (origSurf instanceof CompoundSurface) {
			// wrap the individual ones first
			List<RuptureSurface> subSurfs = new ArrayList<>();
			for (RuptureSurface subSurf : ((CompoundSurface)origSurf).getSurfaceList()) {
				if (wrappedMap.containsKey(subSurf)) {
					subSurfs.add(wrappedMap.get(subSurf));
				} else if (subSurf instanceof CacheEnabledSurface) {
					CustomCacheWrappedSurface cachedSubSurf =
							new CustomCacheWrappedSurface((CacheEnabledSurface)subSurf);
					subSurfs.add(cachedSubSurf);
					wrappedMap.put(subSurf, cachedSubSurf);
				} else {
					subSurfs.add(subSurf);
				}
			}
			wrappedSurf = new CustomCacheWrappedSurface(new CompoundSurface(subSurfs));
			wrappedMap.put(origSurf, (CustomCacheWrappedSurface)wrappedSurf);
		} else if (origSurf instanceof CacheEnabledSurface) {
			wrappedSurf = new CustomCacheWrappedSurface((CacheEnabledSurface)origSurf);
			wrappedMap.put(origSurf, (CustomCacheWrappedSurface)wrappedSurf);
		} else {
			wrappedSurf = origSurf;
		}
		return wrappedSurf;
	}

	@Override
	public ProbEqkSource getSource(int idx) {
		if (sources == null) {
			synchronized (this) {
				if (sources == null)
					initSources();
			}
		}
		ProbEqkSource source = sources.get(idx);
		if (source == null)
			source = erf.getSource(idx);
		return source;
	}

	@Override
	public void updateForecast() {
		this.sources = null;
		erf.updateForecast();
	}

	@Override
	public String getName() {
		return erf.getName();
	}
	
	@Override
	public UnaryOperator<List<DisaggregationSourceRuptureInfo>> getDisaggSourceConsolidator() {
		return erf.getDisaggSourceConsolidator();
	}

	private static class CustomSource extends ProbEqkSource {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private ProbEqkSource origSource;
		private RuptureSurface sourceSurf;
		private List<ProbEqkRupture> rups;

		public CustomSource(ProbEqkSource origSource, RuptureSurface sourceSurf, List<ProbEqkRupture> rups) {
			this.origSource = origSource;
			this.sourceSurf = sourceSurf;
			this.rups = rups;
		}

		@Override
		public LocationList getAllSourceLocs() {
			return origSource.getAllSourceLocs();
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return sourceSurf;
		}

		@Override
		public double getMinDistance(Site site) {
			Location loc = site.getLocation();
			Preconditions.checkNotNull(loc);
			if (sourceSurf == null) {
				return origSource.getMinDistance(site);
//				double minDist = Double.POSITIVE_INFINITY;
//				for (ProbEqkRupture rup : rups)
//					minDist = Math.min(minDist, rup.getRuptureSurface().getDistanceRup(loc));
//				return minDist;
			}
			return sourceSurf.getQuickDistance(loc);
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return rups.get(nRupture);
		}

		@Override
		public boolean isSourcePoissonian() {
			return origSource.isSourcePoissonian();
		}

		@Override
		public boolean isPoissonianSource() {
			return origSource.isPoissonianSource();
		}

		@Override
		public TectonicRegionType getTectonicRegionType() {
			return origSource.getTectonicRegionType();
		}

		@Override
		public void setTectonicRegionType(TectonicRegionType tectonicRegionType) {
			origSource.setTectonicRegionType(tectonicRegionType);
		}
		
	}
	
}