package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.CustomCacheWrappedSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Utility class that wraps an ERF such that each source/rupture surface has it's own single-valued distance cache.
 * This can be useful to avoid thread contention in multhreaded hazard calculations that reuse the same ERF across
 * threads.
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
				sources.add(source);
			}
		}
		this.sources = sources;
	}
	
	private static RuptureSurface getWrappedSurface(Map<RuptureSurface, CustomCacheWrappedSurface> wrappedMap, RuptureSurface origSurf) {
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
							new CustomCacheWrappedSurface((CacheEnabledSurface)subSurf, CacheTypes.SINGLE);
					subSurfs.add(cachedSubSurf);
					wrappedMap.put(subSurf, cachedSubSurf);
				} else {
					subSurfs.add(subSurf);
				}
			}
			wrappedSurf = new CustomCacheWrappedSurface(new CompoundSurface(subSurfs), CacheTypes.SINGLE);
			wrappedMap.put(origSurf, (CustomCacheWrappedSurface)wrappedSurf);
		} else if (origSurf instanceof CacheEnabledSurface) {
			wrappedSurf = new CustomCacheWrappedSurface((CacheEnabledSurface)origSurf, CacheTypes.SINGLE);
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
		return sources.get(idx);
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
			return sourceSurf.getQuickDistance(site.getLocation());
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