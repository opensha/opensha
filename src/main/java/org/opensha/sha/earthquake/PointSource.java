package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.griddedForecast.HypoMagFreqDistAtLoc;
import org.opensha.sha.earthquake.util.GridCellSuperSamplingPoissonPointSourceData;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.PointSurface.DistanceCorrected;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public abstract class PointSource extends ProbEqkSource implements SiteAdaptiveSource {
	
	/*
	 * Basic source items, common to all implementations
	 */
	
	private Location loc;
	private PointSurface pointSurf; // lazily initialized, used for getSourceSurface()
	private LocationList sourceLocs; // lazily initialized, used for getAllSourceLocs()
	protected PointSourceDistanceCorrection distCorr;
	protected double minMagForDistCorr;

	public PointSource(Location loc) {
		this(loc, TECTONIC_REGION_TYPE_DEFAULT, null, Double.NEGATIVE_INFINITY);
	}

	public PointSource(Location loc, TectonicRegionType tectonicRegionType,
			PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
		this.loc = loc;
		this.setTectonicRegionType(tectonicRegionType);
		this.setDistCorr(distCorr, minMagForDistCorr);
	}

	public PointSourceDistanceCorrection getDistCorr() {
		return distCorr;
	}

	public void setDistCorr(PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
		this.distCorr = distCorr;
		this.minMagForDistCorr = minMagForDistCorr;
	}

	@Override
	public LocationList getAllSourceLocs() {
		if (sourceLocs == null) {
			LocationList sourceLocs = new LocationList(1);
			sourceLocs.add(loc);
			this.sourceLocs = sourceLocs;
		}
		return sourceLocs;
	}

	@Override
	public RuptureSurface getSourceSurface() {
		if (pointSurf == null)
			pointSurf = new PointSurface(loc);
		return pointSurf;
	}

	@Override
	public double getMinDistance(Site site) {
		return LocationUtils.horzDistanceFast(loc, site.getLocation());
	}
	
	public Location getLocation() {
		return loc;
	}
	
	/**
	 * {@link PointSource} always implements {@link SiteAdaptiveSource} for distance-corrections; this can be used to
	 * detect if the underlying data are site adaptive, or if the only site-adaptive aspect are distance corrections. 
	 * 
	 * @return true if the underlying point source data are site adaptive, false otherwise
	 */
	public abstract boolean isDataSiteAdaptive();
	
	/**
	 * @return copy of this point source with distance corrections disabled, without overriding the distance correction
	 * in the original source
	 */
	public abstract PointSource getUncorrected();
	
	/*
	 * Source data interfaces
	 */
	
	/**
	 * Base interface for point source data
	 */
	public static interface PointSourceData {
		
		/**
		 * @return the number of ruptures represented by this source
		 */
		public int getNumRuptures();
		
		/**
		 * 
		 * @param rupIndex index for the rupture
		 * @return magnitude for the given rupture
		 */
		public double getMagnitude(int rupIndex);
		
		/**
		 * 
		 * @param rupIndex index for the rupture
		 * @return rake for the given rupture
		 */
		public double getAveRake(int rupIndex);
		
		/**
		 * 
		 * @param rupIndex index for the rupture
		 * @return surface for the given rupture
		 */
		public RuptureSurface getSurface(int rupIndex);
		
		/**
		 * This tells the source if the given rupture uses a finite surface (e.g., {@link EvenlyGriddedSurface} or
		 * {@link QuadSurface}) if true, or a {@link PointSurface} if false. This is used to determine if any
		 * {@link PointSourceDistanceCorrection} should be applied.
		 * 
		 * @param rupIndex
		 * @return true if the given rupture uses a finite surface, false if it uses a {@link PointSurface} 
		 */
		public boolean isFinite(int rupIndex);
		
		/**
		 * This returns the hypocenter location to be set in the rupture object
		 * 
		 * @param sourceLoc original source location
		 * @param rupSurface already-built surface for the rupture
		 * @param rupIndex index for the rupture
		 * @return
		 */
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex);
		
	}
	
	/**
	 * Point source data for a non-Poisson source
	 */
	public static interface NonPoissonPointSourceData extends PointSourceData {
		
		/**
		 * 
		 * @param rupIndex index for the rupture
		 * @return probability for the given rupture
		 */
		public double getProbability(int rupIndex);
		
	}
	
	/**
	 * Point source data for a Poisson source
	 */
	public static interface PoissonPointSourceData extends PointSourceData {
		
		/**
		 * 
		 * @param rupIndex index for the rupture
		 * @return rate for the given rupture
		 */
		public double getRate(int rupIndex);
		
	}
	
	/**
	 * Site-Adaptive variant of {@link PointSourceData} for use with a {@link SiteAdaptiveSource}.
	 */
	public static interface SiteAdaptivePointSourceData<E extends PointSourceData> extends PointSourceData {
		
		/**
		 * Site-specific version of the {@link PointSourceData}
		 * @param site
		 * @return
		 */
		public E getForSite(Site site);
		
		/**
		 * If true, sources will be cached. Default returns false
		 * 
		 * @return true if there are a few fixed versions (e.g., different resolutions) for which sources should be
		 * cached, false if data is unique to each site and sources should be rebuilt every time
		 */
		public default boolean isDiscrete() {
			return false;
		}
		
	}

	
	/**
	 * Interface for building surfaces for a given magnitude and {@link FocalMechanism}.
	 */
	public static interface RuptureSurfaceBuilder {

		public int getNumSurfaces(double magnitude, FocalMechanism mech);
		
		public RuptureSurface getSurface(Location sourceLoc, double magnitude, FocalMechanism mech, int surfaceIndex);
		
		public double getSurfaceWeight(double magnitude, FocalMechanism mech, int surfaceIndex);
		
		public boolean isSurfaceFinite(double magnitude, FocalMechanism mech, int surfaceIndex);
		
		public default WeightedList<RuptureSurface> getSurfaces(Location sourceLoc, double magnitude, FocalMechanism mech) {
			int num = getNumSurfaces(magnitude, mech);
			if (num == 1)
				return WeightedList.evenlyWeighted(getSurface(sourceLoc, magnitude, mech, 0));
			WeightedList<RuptureSurface> ret = new WeightedList<>(num);
			for (int i=0; i<num; i++)
				ret.add(getSurface(sourceLoc, magnitude, mech, i), getSurfaceWeight(magnitude, mech, i));
			Preconditions.checkState(ret.isNormalized(),
					"Surface weights aren't normalized for mag=%s, mech=%s", magnitude, mech);
			return ret;
		}
		
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface);
	}
	
	/**
	 * Extension of {@link RuptureSurfaceBuilder} that instead uses the {@link FocalMech} enum.
	 */
	public static interface FocalMechRuptureSurfaceBuilder extends RuptureSurfaceBuilder {
		
		public default FocalMech getMatchingEnum(FocalMechanism mech) {
			FocalMech mechEnum = FocalMech.forFocalMechanism(mech);
			Preconditions.checkNotNull(mechEnum, "No exact enum match for %s", mech);
			return mechEnum;
		}

		@Override
		default int getNumSurfaces(double magnitude, FocalMechanism mech) {
			return getNumSurfaces(magnitude, getMatchingEnum(mech));
		}
		
		public int getNumSurfaces(double magnitude, FocalMech mech);

		@Override
		default RuptureSurface getSurface(Location sourceLoc, double magnitude, FocalMechanism mech, int surfaceIndex) {
			return getSurface(sourceLoc, magnitude, getMatchingEnum(mech), surfaceIndex);
		}
		
		public RuptureSurface getSurface(Location sourceLoc, double magnitude, FocalMech mech, int surfaceIndex);

		@Override
		default double getSurfaceWeight(double magnitude, FocalMechanism mech, int surfaceIndex) {
			return getSurfaceWeight(magnitude, getMatchingEnum(mech), surfaceIndex);
		}
		
		public double getSurfaceWeight(double magnitude, FocalMech mech, int surfaceIndex);

		@Override
		default boolean isSurfaceFinite(double magnitude, FocalMechanism mech, int surfaceIndex) {
			return isSurfaceFinite(magnitude, getMatchingEnum(mech), surfaceIndex);
		}
		
		public boolean isSurfaceFinite(double magnitude, FocalMech mech, int surfaceIndex);
		
	}
	
	/*
	 * Implementations
	 */
	
	/**
	 * Interface for resampled indexing of a value where each original value does not necessarily each have the same
	 * number or subsamples. Useful for tracking indexing of distance correction when multiple are present, or when
	 * supersampling a source.
	 */
	public static interface DataSubsamplingIndexTracker {
		public void set(int index, int origDataIndex, int subsampleIndex);
		public int getOriginalDataIndex(int rupIndex);
		public int getSubsampleIndex(int rupIndex);
	}
	
	/**
	 * Builds a {@link DataSubsamplingIndexTracker} instance for tracking data resampling indexes. If the original indexes
	 * are all less than Short.MAX_VALUE, a more memory efficient version that uses short[] arrays will be returned.
	 * 
	 * @param totalNumCombinations the total number of resamples, should be at most originalDataCount x subsampleCount
	 * @param originalDataCount the number or original data values
	 * @param subsampleCount the number of subsamples (not all data values need use the same number of samples, in 
	 * which case this is the maximum)
	 * @return
	 */
	public static DataSubsamplingIndexTracker getDataCorrIndexTracker(long totalNumCombinations, int originalDataCount, int subsampleCount) {
		if (originalDataCount < Short.MAX_VALUE && subsampleCount < Short.MAX_VALUE)
			// most common
			return new ShortDataCorrIndexTracker(totalNumCombinations, originalDataCount, subsampleCount);
		else
			// could happen with extreme combinations of super-sampling applied to finite ruptures with many finite
			return new IntDataCorrIndexTracker(totalNumCombinations, originalDataCount, subsampleCount);
	}
	
	private static class ShortDataCorrIndexTracker implements DataSubsamplingIndexTracker {
		
		private short[] origDataIndexes;
		private short[] subsampleIndexes;
		private int originalDataCount;
		private int subsampleCount;
		
		private ShortDataCorrIndexTracker(long totalNumCombinations, int originalDataCount, int subsampleCount) {
			this.originalDataCount = originalDataCount;
			this.subsampleCount = subsampleCount;
			Preconditions.checkState(totalNumCombinations < Integer.MAX_VALUE,
					"Have more than Integer.MAX_VALUE=%s original data and sub-sample combinations: %s; origCount=%s, subsampleCount=%s",
					Integer.MAX_VALUE, totalNumCombinations, originalDataCount, subsampleCount); // should never be the case, but good to check
			Preconditions.checkState(originalDataCount < Short.MAX_VALUE, // should never be the case, but good to check
					"Original data count %s greater than Short.MAX_VALUE=%s",
					originalDataCount, Short.MAX_VALUE);
			Preconditions.checkState(subsampleCount < Short.MAX_VALUE); // should never be the case, but good to check
			this.origDataIndexes = new short[(int)totalNumCombinations];
			this.subsampleIndexes = new short[(int)totalNumCombinations];
		}

		@Override
		public void set(int index, int origDataIndex, int subsampleIndex) {
			Preconditions.checkState(origDataIndex >= -1 && origDataIndex < originalDataCount,
					"Bad origDataIndex=%s for index=%s, subsampleIndex=%s, and originalDataCount=%s",
					origDataIndex, index, subsampleIndex, originalDataCount);
			Preconditions.checkState(subsampleIndex >= -1 && subsampleIndex < subsampleCount,
					"Bad subsampleIndex=%s for index=%s, origDataIndex=%s, and subsampleCount=%s",
					subsampleIndex, index, origDataIndex, subsampleCount);
			origDataIndexes[index] = (short)origDataIndex;
			subsampleIndexes[index] = (short)subsampleIndex;
		}

		@Override
		public int getOriginalDataIndex(int index) {
			return origDataIndexes[index];
		}

		@Override
		public int getSubsampleIndex(int index) {
			return subsampleIndexes[index];
		}
		
	}
	
	private static class IntDataCorrIndexTracker implements DataSubsamplingIndexTracker {
		
		private int[] origDataIndexes;
		private int[] subsampleIndexes;
		private int originalDataCount;
		private int subsampleCount;
		
		private IntDataCorrIndexTracker(long totalNumCombinations, int originalDataCount, int subsampleCount) {
			this.originalDataCount = originalDataCount;
			this.subsampleCount = subsampleCount;
			Preconditions.checkState(totalNumCombinations < Integer.MAX_VALUE,
					"Have more than Integer.MAX_VALUE=%s original data and sub-sample combinations: %s; origCount=%s, subsampleCount=%s",
					Integer.MAX_VALUE, totalNumCombinations, originalDataCount, subsampleCount); // should never be the case, but good to check
			this.origDataIndexes = new int[(int)totalNumCombinations];
			this.subsampleIndexes = new int[(int)totalNumCombinations];
		}

		@Override
		public void set(int index, int origDataIndex, int subsampleIndex) {
			Preconditions.checkState(origDataIndex >= -1 && origDataIndex < originalDataCount,
					"Bad origDataIndex=%s for index=%s, subsampleIndex=%s, and originalDataCount=%s",
					origDataIndex, index, subsampleIndex, originalDataCount);
			Preconditions.checkState(subsampleIndex >= -1 && subsampleIndex < subsampleCount,
					"Bad subsampleIndex=%s for index=%s, origDataIndex=%s, and subsampleCount=%s",
					subsampleIndex, index, origDataIndex, subsampleCount);
			origDataIndexes[index] = origDataIndex;
			subsampleIndexes[index] = subsampleIndex;
		}

		@Override
		public int getOriginalDataIndex(int index) {
			return origDataIndexes[index];
		}

		@Override
		public int getSubsampleIndex(int index) {
			return subsampleIndexes[index];
		}
		
	}
	
	/**
	 * This is a basic implementation that takes a {@link PointSourceData} implementation and build ruptures for the
	 * given distance correction(s).
	 * 
	 * It keeps track of rupture indexes in the source as well as those in the higher level data object. These indexes
	 * differ when there are multiple point source distance implementations
	 * 
	 * @param <E> data type
	 */
	private static abstract class BaseImplementation<E extends PointSourceData> extends PointSource {
		
		protected E data;
		
		protected int numRuptures;
		
		private ConcurrentMap<E, BaseImplementation<E>> siteAdaptiveSourceDataCache;
		
		public BaseImplementation(Location loc, TectonicRegionType tectonicRegionType,
				E data, PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
			super(loc, tectonicRegionType, distCorr, minMagForDistCorr);
			setData(data);
		}
		
		public void setData(E data) {
			this.data = data;
			if (data != null) {
				numRuptures = data.getNumRuptures();
				if (data instanceof SiteAdaptivePointSourceData<?> && ((SiteAdaptivePointSourceData<?>)data).isDiscrete())
					siteAdaptiveSourceDataCache = new ConcurrentHashMap<>(4);
				else
					siteAdaptiveSourceDataCache = null;
			} else {
				numRuptures = 0;
				siteAdaptiveSourceDataCache = null;
			}
		}

		@Override
		public int getNumRuptures() {
//			Preconditions.checkState(numRuptures > 0); // can be zero if min mag filter is set high
			return numRuptures;
		}
		
		/**
		 * @return point source data for this source
		 */
		public E getData() {
			return data;
		}
		
		/**
		 * 
		 * @param dataRuptureIndex rupture index in the {@link PointSourceData} data object
		 * @param distCorrWeight weight for the distance correction (if any). Will be 1 unless there are multiple
		 * distance corrections
		 * @return probability for this rupture
		 */
		protected abstract double getProbability(int dataRuptureIndex, double distCorrWeight);

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			// nRupture is data index here
			double mag = data.getMagnitude(nRupture);
			double rake = data.getAveRake(nRupture);
			RuptureSurface surf = data.getSurface(nRupture);
			if (distCorr != null && mag >= minMagForDistCorr && surf instanceof PointSurface) {
				// distance corrections are enabled and this is a point source
				// the distance-corrected version must be accessed via getForSite(Site)
				// protect the surface here so that it is not accidentally used
				surf = ((PointSurface)surf).getDistancedProtected(distCorr);
			}
			Location hypo = data.getHypocenter(getLocation(), surf, nRupture);
			double prob = getProbability(nRupture, 1d);
			return new ProbEqkRupture(mag, rake, prob, surf, hypo);
		}
		
		protected abstract BaseImplementation<E> newInstance(E data);

		@Override
		public boolean isDataSiteAdaptive() {
			return data instanceof SiteAdaptivePointSourceData<?>;
		}

		@Override
		public PointSource getUncorrected() {
			if (distCorr == null)
				return this;
			BaseImplementation<E> copy = newInstance(data);
			copy.setDistCorr(null, Double.MIN_VALUE);
			return copy;
		}

		@Override
		public PointSource getForSite(Site site) {
			BaseImplementation<E> ret = this;
			
			// first check to see if the underlying data are site adaptive
			if (isDataSiteAdaptive()) {
				// the data are site adaptive, take care of that first
				E dataForSite = ((SiteAdaptivePointSourceData<E>)data).getForSite(site);
				if (dataForSite != data) {
					boolean wasCached = false;
					if (siteAdaptiveSourceDataCache != null) {
						// see if cached
						BaseImplementation<E> cached = siteAdaptiveSourceDataCache.get(dataForSite);
						if (cached != null) {
							ret = cached;
							wasCached = true;
						}
					}
					if (!wasCached) {
						// need to build it
						ret = newInstance(dataForSite);
						if (siteAdaptiveSourceDataCache != null)
							siteAdaptiveSourceDataCache.putIfAbsent(dataForSite, ret);
					}
				}
			}
			
			// now check to see if we have a distance correction that we need to apply
			if (distCorr != null && ret.numRuptures > 0) {
				// we have a distance correction, apply it
				return new DistanceCorrectedPointSource<>(ret, distCorr, site.getLocation());
			}
			return ret;
		}
		
	}
	
	private static class DistanceCorrectedPointSource<E extends PointSourceData> extends PointSource {

		private final BaseImplementation<E> source;
		private final Location siteLoc;
		private final List<ProbEqkRupture> ruptures;

		private DistanceCorrectedPointSource(BaseImplementation<E> source,
				PointSourceDistanceCorrection distCorr, Location siteLoc) {
			// we intentionally use a passed in distance correction instead of the one attached to the upstream source
			// because it could have been cached previously as part of site-adaptive data, and the distance correction
			// on that cached version could be stale
			super(siteLoc, source.getTectonicRegionType(), distCorr, source.minMagForDistCorr);
			this.isPoissonian = source.isPoissonian;
			this.source = source;
//			super(sourceLoc, tectonicRegionType, data, distCorr, minMagForDistCorr);
			this.siteLoc = siteLoc;
			Preconditions.checkNotNull(siteLoc);
			Preconditions.checkNotNull(distCorr);
			Location sourceLoc = getLocation();
			
			E data = source.getData();
			
			double horzDist = LocationUtils.horzDistanceFast(siteLoc, source.getLocation());
			int numOrigRups = source.getNumRuptures();
			TectonicRegionType trt = getTectonicRegionType();
			ruptures = new ArrayList<>(source.getNumRuptures()); // at least this many ruptures, maybe more
			for (int dataIndex=0; dataIndex<numOrigRups; dataIndex++) {
				double mag = data.getMagnitude(dataIndex);
				double rake = data.getAveRake(dataIndex);
				RuptureSurface surf = data.getSurface(dataIndex);
				if (mag >= minMagForDistCorr && !data.isFinite(dataIndex)) {
					// point source, do distance correction(s)
					Preconditions.checkState(surf instanceof PointSurface,
							"Surface for rupture with data index %s was labeled as non-finite, but is not a PointSurface: %s",
							dataIndex, surf);
					PointSurface ptSurf = (PointSurface)surf;
					WeightedList<SurfaceDistances> corrDists = distCorr.getCorrectedDistances(
							siteLoc, ptSurf, trt, mag, horzDist);
					Preconditions.checkState(corrDists.isNormalized(),
							"Distance correction weights must be normalized to 1, aren't for %s", distCorr);
					for (int i=0; i<corrDists.size(); i++) {
						SurfaceDistances dists = corrDists.getValue(i);
						double weight = corrDists.getWeight(i);
						DistanceCorrected corrSurf = ptSurf.getForDistances(siteLoc, dists);
						Location hypo = data.getHypocenter(sourceLoc, corrSurf, dataIndex);
						double prob = source.getProbability(dataIndex, weight);
						ruptures.add(new ProbEqkRupture(mag, rake, prob, corrSurf, hypo));
					}
				} else {
					// finite source or below min correction magnitude, leave unchanged
					Location hypo = data.getHypocenter(getLocation(), surf, dataIndex);
					double prob = source.getProbability(dataIndex, 1d);
					ruptures.add(new ProbEqkRupture(mag, rake, prob, surf, hypo));
				}
			}
		}

		@Override
		public ProbEqkSource getForSite(Site site) {
			Location loc = site.getLocation();
			Preconditions.checkState(loc == siteLoc || LocationUtils.areSimilar(loc, siteLoc),
					"Cannot change sites, this source is already corrected for the specific site and correction");
			return this;
		}

		@Override
		public int getNumRuptures() {
			return ruptures.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return ruptures.get(nRupture);
		}

		@Override
		public PointSourceDistanceCorrection getDistCorr() {
			return distCorr;
		}

		@Override
		public void setDistCorr(PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
			if (source == null) {
				// this is the first time, from the constructor, don't need to protect
				super.setDistCorr(distCorr, minMagForDistCorr);
			} else {
				throw new UnsupportedOperationException("Cannot change distance corrections, this source is already "
						+ "corrected for the specific site and correction");
			}
		}

		@Override
		public LocationList getAllSourceLocs() {
			return source.getAllSourceLocs();
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return source.getSourceSurface();
		}

		@Override
		public double getMinDistance(Site site) {
			return source.getMinDistance(site);
		}

		@Override
		public Location getLocation() {
			return source.getLocation();
		}

		@Override
		public boolean isDataSiteAdaptive() {
			return source.isDataSiteAdaptive();
		}

		@Override
		public PointSource getUncorrected() {
			return source.getUncorrected();
		}
		
	}
	
	public static class NonPoissonPointSource extends BaseImplementation<NonPoissonPointSourceData> {

		public NonPoissonPointSource(Location loc, TectonicRegionType tectonicRegionType,
				NonPoissonPointSourceData data, PointSourceDistanceCorrection distCorr,
				double minMagForDistCorr) {
			super(loc, tectonicRegionType, data, distCorr, minMagForDistCorr);
			super.isPoissonian = false;
		}

		@Override
		protected double getProbability(int dataRuptureIndex, double distCorrWeight) {
			// if there are multiple distance correlations, just partition the probability across them
			return data.getProbability(dataRuptureIndex) * distCorrWeight;
		}

		@Override
		protected BaseImplementation<NonPoissonPointSourceData> newInstance(
				NonPoissonPointSourceData data) {
			return new NonPoissonPointSource(getLocation(), getTectonicRegionType(), data, distCorr, minMagForDistCorr);
		}
		
	}
	
	public static class PoissonPointSource extends BaseImplementation<PoissonPointSourceData> {

		private double duration;

		public PoissonPointSource(Location loc, TectonicRegionType tectonicRegionType,
				double duration, PoissonPointSourceData data) {
			this(loc, tectonicRegionType, duration, data, null, Double.NEGATIVE_INFINITY);
		}

		public PoissonPointSource(Location loc, TectonicRegionType tectonicRegionType,
				double duration, PoissonPointSourceData data,
				PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
			super(loc, tectonicRegionType, data, distCorr, minMagForDistCorr);
			this.duration = duration;
			super.isPoissonian = true;
		}

		/**
		 * This sets the duration used in computing Poisson probabilities.  This assumes
		 * the same units as in the magFreqDist rates.
		 * @param duration
		 */
		public void setDuration(double duration) {
			this.duration=duration;
		}

		/**
		 * This gets the duration used in computing Poisson probabilities
		 * @param duration
		 */
		public double getDuration() {
			return duration;
		}

		/**
		 * Given an observed annual rate of occurrence of some event (in num/yr),
		 * method returns the Poisson probability of occurence over the specified
		 * time period.
		 * @param rate (annual) of occurence of some event
		 * @param time period of interest
		 * @return the Poisson probability of occurrence in the specified
		 *         <code>time</code>
		 */
		public static double rateToProb(double rate, double time) {
			return 1 - Math.exp(-rate * time);
		}

		/**
		 * Given the Poisson probability of the occurence of some event over a
		 * specified time period, method returns the annual rate of occurrence of
		 * that event.
		 * @param P the Poisson probability of an event's occurrence
		 * @param time period of interest
		 * @return the annnual rate of occurrence of the event
		 */
		public static double probToRate(double P, double time) {
			return -Math.log(1 - P) / time;
		}

		@Override
		protected double getProbability(int dataIndex, double distCorrWeight) {
			// partition the rate, then convert that rate to a probability
			double rate = data.getRate(dataIndex) * distCorrWeight;
			return rateToProb(rate, duration);
		}

		@Override
		public double computeTotalEquivMeanAnnualRate(double duration) {
			// shortcut to just do it over rate (and not instantiate ruptures)
			double rate = 0d;
			int numRups = data.getNumRuptures();
			for (int i=0; i<numRups; i++)
				rate += data.getRate(i);
			return rate;
		}

		@Override
		protected BaseImplementation<PoissonPointSourceData> newInstance(PoissonPointSourceData data) {
			return new PoissonPointSource(getLocation(), getTectonicRegionType(), duration, data, distCorr, minMagForDistCorr);
		}
		
	}
	
	/**
	 * Builds point source data for the given MFD, {@link FocalMechanism} and {@link RuptureSurfaceBuilder}
	 * 
	 * @param loc
	 * @param mfd
	 * @param mech
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static PoissonPointSourceData dataForMFD(Location loc, IncrementalMagFreqDist mfd, FocalMechanism mech,
			RuptureSurfaceBuilder surfaceBuilder) {
		Set<FocalMechanism> mechs = Set.of(mech);
		MFDData data = new MFDData() {

			@Override
			public Set<FocalMechanism> mechanisms() {
				return mechs;
			}

			@Override
			public int size(FocalMechanism mech) {
				return mfd.size();
			}

			@Override
			public double magnitude(FocalMechanism mech, int index) {
				return mfd.getX(index);
			}

			@Override
			public double rate(FocalMechanism mech, int index) {
				return mfd.getY(index);
			}
		};
		return new MFDPoissonPointSourceData(loc, data, surfaceBuilder);
	}
	
	/**
	 * Builds point source data for the given MFD, weights, and {@link RuptureSurfaceBuilder}
	 * 
	 * @param loc
	 * @param mfd
	 * @param weights
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static <E> PoissonPointSourceData dataForMFDs(Location loc, IncrementalMagFreqDist mfd,
			Map<FocalMechanism, Double> weights, RuptureSurfaceBuilder surfaceBuilder) {
		double weightSum = 0d;
		for (FocalMechanism mech : weights.keySet()) {
			double weight = weights.get(mech);
			Preconditions.checkState(weight >= 0d && weight <= 1d, "Bad weight for %s: %s", mech, weight);
			weightSum += weight;
		}
		Preconditions.checkState(Precision.equals(weightSum, 1d, 0.001),
				"FocalMech weights don't sum to 1: %s", (float)weightSum);
		MFDData data = new MFDData() {

			@Override
			public Set<FocalMechanism> mechanisms() {
				return weights.keySet();
			}

			@Override
			public int size(FocalMechanism mech) {
				return mfd.size();
			}

			@Override
			public double magnitude(FocalMechanism mech, int index) {
				return mfd.getX(index);
			}

			@Override
			public double rate(FocalMechanism mech, int index) {
				return mfd.getY(index) * weights.get(mech);
			}
		};
		return new MFDPoissonPointSourceData(loc, data, surfaceBuilder);
	}
	
	/**
	 * Builds point source data for the given MFDs and {@link RuptureSurfaceBuilder}
	 * 
	 * @param loc
	 * @param mfds
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static <E> PoissonPointSourceData dataForMFDs(Location loc, Map<FocalMechanism, IncrementalMagFreqDist> mfds,
			RuptureSurfaceBuilder surfaceBuilder) {
		MFDData data = new MFDData() {

			@Override
			public Set<FocalMechanism> mechanisms() {
				return mfds.keySet();
			}

			@Override
			public int size(FocalMechanism mech) {
				return mfds.get(mech).size();
			}

			@Override
			public double magnitude(FocalMechanism mech, int index) {
				return mfds.get(mech).getX(index);
			}

			@Override
			public double rate(FocalMechanism mech, int index) {
				return mfds.get(mech).getY(index);
			}
		};
		return new MFDPoissonPointSourceData(loc, data, surfaceBuilder);
	}
	
	/**
	 * Builds point source data for the given MFDs and {@link RuptureSurfaceBuilder}
	 * 
	 * @param loc
	 * @param magnitude
	 * @param rate
	 * @param ruptureData
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static <E> PoissonPointSourceData dataForMagRate(Location loc, double magnitude, double rate,
			FocalMechanism mech, RuptureSurfaceBuilder surfaceBuilder) {
		Set<FocalMechanism> set = Set.of(mech);
		MFDData data = new MFDData() {

			@Override
			public Set<FocalMechanism> mechanisms() {
				return set;
			}

			@Override
			public int size(FocalMechanism mech) {
				return 1;
			}

			@Override
			public double magnitude(FocalMechanism mech, int index) {
				return magnitude;
			}

			@Override
			public double rate(FocalMechanism mech, int index) {
				return rate;
			}
		};
		return new MFDPoissonPointSourceData(loc, data, surfaceBuilder);
	}
	
	/**
	 * Builds point source data for the given MFDs and {@link RuptureSurfaceBuilder}
	 * 
	 * @param loc
	 * @param mfds
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static <E> PoissonPointSourceData dataForMagRates(Location loc, double magnitude,
			Map<FocalMechanism, Double> ruptureRates, RuptureSurfaceBuilder surfaceBuilder) {
		MFDData data = new MFDData() {

			@Override
			public Set<FocalMechanism> mechanisms() {
				return ruptureRates.keySet();
			}

			@Override
			public int size(FocalMechanism mech) {
				return 1;
			}

			@Override
			public double magnitude(FocalMechanism mech, int index) {
				return magnitude;
			}

			@Override
			public double rate(FocalMechanism mech, int index) {
				return ruptureRates.get(mech);
			}
		};
		return new MFDPoissonPointSourceData(loc, data, surfaceBuilder);
	}
	
	/**
	 * Builds point source data for the given {@link HypoMagFreqDistAtLoc} and {@link RuptureSurfaceBuilder}
	 * 
	 * @param hypoMFDs
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static PoissonPointSourceData dataForHypoMFDs(HypoMagFreqDistAtLoc hypoMFDs,
			RuptureSurfaceBuilder surfaceBuilder) {
		FocalMechanism[] mechs = hypoMFDs.getFocalMechanismList();
		IncrementalMagFreqDist[] mfds = hypoMFDs.getMagFreqDistList();
		Preconditions.checkNotNull(mechs, "Mechanisms can't be null");
		Preconditions.checkNotNull(mfds, "MFDs can't be null");
		Preconditions.checkState(mfds.length > 0, "Must have at least 1 MFD");
		Preconditions.checkState(mfds.length == mechs.length, "Mech and MFD size mismatch");
		HashMap<FocalMechanism, IncrementalMagFreqDist> map = new HashMap<>(mechs.length);
		for (int i=0; i<mechs.length; i++) {
			Preconditions.checkNotNull(mechs[i], "Mechanism %s is null", i);
			Preconditions.checkNotNull(mfds[i], "MFD %s is null", i);
			Preconditions.checkState(map.put(mechs[i], mfds[i]) == null, "Duplicate FocalMechanism encountered at %s", i);
		}
		return dataForMFDs(hypoMFDs.getLocation(), map, surfaceBuilder);
	}
	
	private interface MFDData {
		
		public Set<FocalMechanism> mechanisms();
		
		public int size(FocalMechanism mech);
		
		public double magnitude(FocalMechanism mech, int index);
		
		public double rate(FocalMechanism mech, int index);
	}
	
	private static class MFDPoissonPointSourceData implements PoissonPointSourceData {

		private Location loc;
		private RuptureSurfaceBuilder surfaceBuilder;
		
		private final double[] magnitudes;
		private final double[] rates;
		private final short[] surfIndexes;
		private final FocalMechanism[] mechs;
		private final int numRuptures;
		private MFDPoissonPointSourceData(Location loc, MFDData mfdData, RuptureSurfaceBuilder surfaceBuilder) {
			this.loc = loc;
			this.surfaceBuilder = surfaceBuilder;
			int numRups = 0;
			boolean anyMultiple = false;
			for (FocalMechanism mech : mfdData.mechanisms()) {
				int size = mfdData.size(mech);
				for (int m=0; m<size; m++) {
					double rate = mfdData.rate(mech, m);
					if (rate == 0d)
						continue;
					double mag = mfdData.magnitude(mech, m);
					int magDataCount = surfaceBuilder.getNumSurfaces(mag, mech);
					Preconditions.checkState(magDataCount > 0,
							"Surface count is %s for mech=%s and mag=%s", magDataCount, mech, mag);
					numRups += magDataCount;
					anyMultiple |= magDataCount > 1;
				}
			}
			Preconditions.checkState(numRups > 0, "No ruptures; MFDs all zeros?");
			
			magnitudes = new double[numRups];
			rates = new double[numRups];
			mechs = new FocalMechanism[numRups];
			surfIndexes = anyMultiple ? new short[numRups] : null;
			int index = 0;
			for (FocalMechanism mech : mfdData.mechanisms()) {
				int size = mfdData.size(mech);
				for (int m=0; m<size; m++) {
					double rate = mfdData.rate(mech, m);
					if (rate == 0d)
						continue;
					double mag = mfdData.magnitude(mech, m);
					int magMechCount = surfaceBuilder.getNumSurfaces(mag, mech);
					for (int i=0; i<magMechCount; i++) {
						rates[index] = magMechCount == 1 ? rate : rate *  surfaceBuilder.getSurfaceWeight(mag, mech, i);
						magnitudes[index] = mag;
						mechs[index] = mech;
						if (anyMultiple)
							surfIndexes[index] = (short)i;
						index++;
					}
				}
			}
			Preconditions.checkState(index == numRups);
			this.numRuptures = numRups;
		}

		@Override
		public int getNumRuptures() {
			return numRuptures;
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return magnitudes[rupIndex];
		}

		@Override
		public double getAveRake(int rupIndex) {
			return mechs[rupIndex].getRake();
		}

		@Override
		public double getRate(int rupIndex) {
			return rates[rupIndex];
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			return surfaceBuilder.getSurface(loc, magnitudes[rupIndex], mechs[rupIndex],
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			return surfaceBuilder.getHypocenter(sourceLoc, rupSurface);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			return surfaceBuilder.isSurfaceFinite(magnitudes[rupIndex], mechs[rupIndex],
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}
	}
	
	private static class NonPoissonPointRupture {
		
		public final double magnitude;
		public final double probability;
		public final FocalMechanism mechanism;
		
		private NonPoissonPointRupture(double magnitude, double probability, FocalMechanism mechanism) {
			super();
			this.magnitude = magnitude;
			this.probability = probability;
			this.mechanism = mechanism;
		}
		
	}
	
	private static class RupListNonPoissonPointSourceData implements NonPoissonPointSourceData {

		private Location loc;
		private List<NonPoissonPointRupture> ruptures;
		private RuptureSurfaceBuilder surfaceBuilder;
		
		private final int numRuptures;
		private final short[] rupIndexes;
		private final short[] surfIndexes;

		public RupListNonPoissonPointSourceData(Location loc, List<NonPoissonPointRupture> ruptures, RuptureSurfaceBuilder surfaceBuilder) {
			this.loc = loc;
			this.ruptures = ruptures;
			this.surfaceBuilder = surfaceBuilder;
			
			int numRuptures = 0;
			boolean anyMultiple = false;
			for (NonPoissonPointRupture rup : ruptures) {
				if (rup.probability > 0d) {
					int surfCount = surfaceBuilder.getNumSurfaces(rup.magnitude, rup.mechanism);
					Preconditions.checkState(surfCount > 0 && surfCount < Short.MAX_VALUE);
					anyMultiple |= surfCount > 1;
					numRuptures += surfCount;
				}
			}
			Preconditions.checkState(numRuptures > 0, "No ruptures?");
			Preconditions.checkState(ruptures.size() < Short.MAX_VALUE);
			
			short[] rupIndexes = new short[numRuptures];
			short[] surfIndexes = anyMultiple ? new short[numRuptures] : null;
			int index = 0;
			for (int r=0; r<ruptures.size(); r++) {
				NonPoissonPointRupture rup = ruptures.get(r);
				if (rup.probability > 0d) {
					int surfCount = surfaceBuilder.getNumSurfaces(rup.magnitude, rup.mechanism);
					for (int s=0; s<surfCount; s++) {
						rupIndexes[index] = (short)r;
						if (anyMultiple)
							surfIndexes[index] = (short)s;
						index++;
					}
				}
			}
			Preconditions.checkState(index == numRuptures);
			this.rupIndexes = rupIndexes;
			this.surfIndexes = surfIndexes;
			this.numRuptures = numRuptures;
		}

		@Override
		public int getNumRuptures() {
			return numRuptures;
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return ruptures.get(rupIndexes[rupIndex]).magnitude;
		}

		@Override
		public double getAveRake(int rupIndex) {
			return ruptures.get(rupIndexes[rupIndex]).mechanism.getRake();
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			NonPoissonPointRupture rupture = ruptures.get(rupIndexes[rupIndex]);
			return surfaceBuilder.getSurface(loc, rupture.magnitude, rupture.mechanism,
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			NonPoissonPointRupture rupture = ruptures.get(rupIndexes[rupIndex]);
			return surfaceBuilder.isSurfaceFinite(rupture.magnitude, rupture.mechanism,
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			return surfaceBuilder.getHypocenter(sourceLoc, rupSurface);
		}

		@Override
		public double getProbability(int rupIndex) {
			return ruptures.get(rupIndexes[rupIndex]).probability;
		}
		
	}
	
	public static RuptureSurfaceBuilder truePointSurfaceGenerator(double depth) {
		return new TruePointSurfaceGenerator(depth);
	}
	
	private static class TruePointSurfaceGenerator implements RuptureSurfaceBuilder {

		private double depth;

		public TruePointSurfaceGenerator(double depth) {
			this.depth = depth;
		}

		@Override
		public int getNumSurfaces(double magnitude, FocalMechanism mech) {
			return 1;
		}

		@Override
		public RuptureSurface getSurface(Location loc, double magnitude, FocalMechanism mech, int surfaceIndex) {
			loc = new Location(loc.lat, loc.lon, depth);
			PointSurface surf = new PointSurface(loc);
			surf.setAveDip(mech.getDip());
			return surf;
		}

		@Override
		public double getSurfaceWeight(double magnitude, FocalMechanism mech, int surfaceIndex) {
			return 1d;
		}

		@Override
		public boolean isSurfaceFinite(double magnitude, FocalMechanism mech, int surfaceIndex) {
			return false;
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
			return sourceLoc;
		}
		
	}
	
	/*
	 * Builders
	 */
	
	public static class AbstractBuilder<E extends PointSourceData, B extends AbstractBuilder<E, B>> {
		
		protected Location loc;
		protected RuptureSurfaceBuilder surfaceBuilder;
		protected E data;
		protected TectonicRegionType trt = TECTONIC_REGION_TYPE_DEFAULT;
		protected PointSourceDistanceCorrection distCorr;
		protected double minMagForDistCorr;

		/**
		 * Initializes a Poisson point source builder for the given location
		 * 
		 * @param loc
		 */
		private AbstractBuilder(Location loc) {
			this.loc = loc;
		}
		
		/**
		 * Sets the {@link TectonicRegionType} for this source
		 * 
		 * @param trt
		 * @return the builder
		 * @see {@link ProbEqkSource#TECTONIC_REGION_TYPE_DEFAULT}
		 */
		public B tectonicRegionType(TectonicRegionType trt) {
			this.trt = trt;
			return castThis();
		}
		
		@SuppressWarnings("unchecked")
		private B castThis() {
			return (B)this;
		}
		
		/**
		 * Sets the data that constitutes this point source
		 * 
		 * @param data
		 * @return the builder
		 */
		public B data(E data) {
			this.data = data;
			return castThis();
		}
		
		/**
		 * Sets the rupture surface builder responsible for creating rupture surfaces for each focal mechanism
		 * 
		 * @param surfaceBuilder
		 * @return
		 */
		public B surfaceBuilder(RuptureSurfaceBuilder surfaceBuilder) {
			this.surfaceBuilder = surfaceBuilder;
			return castThis();
		}
		
		public B truePointSources() {
			return truePointSources(loc.depth);
		}
		
		public B truePointSources(double depth) {
			return surfaceBuilder(truePointSurfaceGenerator(depth));
		}
		
		protected void checkHasSurfBuilder() {
			Preconditions.checkState(surfaceBuilder != null, "Must supply RuptureSurfaceBuilder first");
		}
		
		/**
		 * Sets point source distance correction(s) to be used. This need not be final, and can be updated on the built
		 * source by calling {@link PointSource#setDistCorr(PointSourceDistanceCorrection, double)}.
		 * 
		 * @param distCorr
		 * @param minMagForDistCorr
		 * @return the builder
		 */
		public B distCorr(PointSourceDistanceCorrection distCorr, double minMagForDistCorr) {
			this.distCorr = distCorr;
			this.minMagForDistCorr = minMagForDistCorr;
			return castThis();
		}
		
		protected void checkValidLocAndData() {
			Preconditions.checkState(loc != null, "Location cannot be null");
			Preconditions.checkState(data != null, "Point source data cannot be null");
		}
	}
	
	/**
	 * Initializes a {@link NonPoissonPointSource} builder for the given location and the default tectonic regime
	 * ({@link ProbEqkSource#TECTONIC_REGION_TYPE_DEFAULT}).
	 * 
	 * @param loc point source location
	 * @return builder
	 */
	public static NonPoissonBuilder nonPoissonBuilder(Location loc) {
		return new NonPoissonBuilder(loc);
	}
	/**
	 * Initializes a {@link PoissonPointSource} builder for the given location and tectonic regime
	 * 
	 * @param loc point source location
	 * @param trt tectonic regime
	 * @return builder
	 */
	public static NonPoissonBuilder nonPoissonBuilder(Location loc, TectonicRegionType trt) {
		return new NonPoissonBuilder(loc).tectonicRegionType(trt);
	}
	
	public static class NonPoissonBuilder extends AbstractBuilder<NonPoissonPointSourceData, NonPoissonBuilder> {
		
		/**
		 * Initializes a Poisson point source builder for the given location
		 * 
		 * @param loc
		 */
		private NonPoissonBuilder(Location loc) {
			super(loc);
		}
		
		/**
		 * Builds point source data for the given magnitude, probability, and focal mechanism. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param magnitude
		 * @param prob
		 * @param mech
		 * @return the builder
		 */
		public NonPoissonBuilder forMagProbAndFocalMech(double magnitude, double prob, FocalMechanism mech) {
			checkHasSurfBuilder();
			return data(new RupListNonPoissonPointSourceData(loc,
					List.of(new NonPoissonPointRupture(magnitude, prob, mech)), surfaceBuilder));
		}
		
		/**
		 * Builds point source data for the given magnitudes, probabilities, and focal mechanisms. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param magnitudes
		 * @param probs
		 * @param mechs
		 * @return the builder
		 */
		public NonPoissonBuilder forMagProbAndFocalMech(List<Double> magnitudes, List<Double> probs, List<FocalMechanism> mechs) {
			checkHasSurfBuilder();
			Preconditions.checkState(magnitudes.size() == probs.size());
			Preconditions.checkState(magnitudes.size() == mechs.size());
			List<NonPoissonPointRupture> rups = new ArrayList<>(magnitudes.size());
			for (int i=0; i<magnitudes.size(); i++)
				rups.add(new NonPoissonPointRupture(magnitudes.get(i), probs.get(i), mechs.get(i)));
			return data(new RupListNonPoissonPointSourceData(loc, rups, surfaceBuilder));
		}
		
		/**
		 * Builds a {@link PoissonPointSource} for the given inputs.
		 * @return {@link PoissonPointSource} implementation
		 * @throws IllegalStateException if the location, point source data, or duration have not been set
		 */
		@SuppressWarnings("unchecked")
		public NonPoissonPointSource build() {
			checkValidLocAndData();
			
			return new NonPoissonPointSource(loc, trt, data, distCorr, minMagForDistCorr);
		}
	}
	
	/**
	 * Initializes a {@link PoissonPointSource} builder for the given location and the default tectonic regime
	 * ({@link ProbEqkSource#TECTONIC_REGION_TYPE_DEFAULT}).
	 * 
	 * <p>The typical flow to build a point source is to give the initial (but updatable) forecast duration via
	 * {@link PoissonBuilder#duration(double)}, set the distance corrections via {@link PoissonBuilder#distCorr(PointSourceDistanceCorrection, double)},
	 * set the rate data (e.g., from an MFD), and set the {@link RuptureSurfaceBuilder} that builds rupture surfaces. TODO update
	 * 
	 * @param loc point source location
	 * @return builder
	 */
	public static PoissonBuilder poissonBuilder(Location loc) {
		return new PoissonBuilder(loc);
	}
	/**
	 * Initializes a {@link PoissonPointSource} builder for the given location and tectonic regime
	 * 
	 * @param loc point source location
	 * @param trt tectonic regime
	 * @return builder
	 */
	public static PoissonBuilder poissonBuilder(Location loc, TectonicRegionType trt) {
		return new PoissonBuilder(loc).tectonicRegionType(trt);
	}
	
	public static class PoissonBuilder extends AbstractBuilder<PoissonPointSourceData, PoissonBuilder> {
		
		private double duration = Double.NaN;

		/**
		 * Initializes a Poisson point source builder for the given location
		 * 
		 * @param loc
		 */
		private PoissonBuilder(Location loc) {
			super(loc);
		}
		
		/**
		 * Sets the duration (in years) for this source, used when converting rates to Poissoin probabilities.
		 * This need not be final, and can be updated on the built source by calling {@link PoissonPointSource#setDuration(double)}.
		 * 
		 * @param duration
		 * @return the builder
		 */
		public PoissonBuilder duration(double duration) {
			Preconditions.checkState(Double.isFinite(duration) && duration > 0d, "Duration must be finite and >0");
			this.duration = duration;
			return this;
		}
		
		/**
		 * Builds point source data for the given magnitude, rate, and focal mechanism. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param mfd
		 * @param mech
		 * @return the builder
		 */
		public PoissonBuilder forMagRateAndFocalMech(double magnitude, double rate, FocalMechanism mech) {
			checkHasSurfBuilder();
			return data(dataForMagRate(loc, magnitude, rate, mech, surfaceBuilder));
		}
		
		/**
		 * Builds point source data for the given MFD and focal mechanism. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param mfd
		 * @param mech
		 * @return the builder
		 */
		public PoissonBuilder forMFDAndFocalMech(IncrementalMagFreqDist mfd, FocalMechanism mech) {
			checkHasSurfBuilder();
			return data(dataForMFD(loc, mfd, mech, surfaceBuilder));
		}
		
		/**
		 * Builds point source data for the given MFD and mechanism weights. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param mfd
		 * @param mechWeights
		 * @param surfaceBuilder
		 * @return the builder
		 */
		public <E> PoissonBuilder forMFDsAndFocalMechs(IncrementalMagFreqDist mfd, Map<FocalMechanism, Double> mechWeights) {
			checkHasSurfBuilder();
			return data(dataForMFDs(loc, mfd, mechWeights, surfaceBuilder));
		}
		
		/**
		 * Builds point source data for the given mechanism-specific MFDs. Must first set the
		 * {@link RuptureSurfaceBuilder} via {@link #surfaceBuilder(RuptureSurfaceBuilder)} or
		 * {@link PointSource#truePointSurfaceGenerator(double)}.
		 * 
		 * @param mfd
		 * @param mechWeights
		 * @param surfaceBuilder
		 * @return the builder
		 */
		public <E> PoissonBuilder forMFDsAndFocalMechs(Map<FocalMechanism, IncrementalMagFreqDist> mfds) {
			checkHasSurfBuilder();
			return data(dataForMFDs(loc, mfds, surfaceBuilder));
		}
		
		/**
		 * This enables distance-dependent supersampling of point sources. The cell represented by this point source will
		 * be divided up into a supersampled grid cell with at least <code>samplesPerKM</code> samples per kilometer.
		 * 
		 * <p>Three sampling levels are supported, each with decreasing computational demands:
		 * 
		 * <p>Full supersampling, up to the supplied <code>fullDist</code>, uses the full set of supersampled grid nodes
		 * and is most expensive but most accurate, especially nearby.
		 * 
		 * <p>Border sampling, up to the supplied <code>borderDist</code>, uses just the exterior grid nodes from
		 * the supersampled region, and is best when the site is a little further away and just sensitive to the size
		 * of the grid cell without integrating over the entire cell.
		 * 
		 * <p>Corder sampling, up to the supplied <code>cornerDist</code>, uses the corners of the grid cell as a crude
		 * approximation. This is fast (only 4 locations) and most appropriate at larger distances.
		 * 
		 * @param gridCell cell that this grid node represents
		 * @param supersampleSettings supersampling settings
		 */
		public PoissonBuilder siteAdaptiveSupersampled(Region gridCell, GridCellSupersamplingSettings supersampleSettings) {
			Preconditions.checkState(data != null, "Must set point source data first");
			data = new GridCellSuperSamplingPoissonPointSourceData(data, loc,
					gridCell, supersampleSettings);
			return this;
		}
		
		/**
		 * This enables distance-dependent supersampling of point sources. The cell represented by this point source will
		 * be divided up into a supersampled grid cell with at least <code>samplesPerKM</code> samples per kilometer.
		 * 
		 * <p>Three sampling levels are supported, each with decreasing computational demands:
		 * 
		 * <p>Full supersampling, up to the supplied <code>fullDist</code>, uses the full set of supersampled grid nodes
		 * and is most expensive but most accurate, especially nearby.
		 * 
		 * <p>Border sampling, up to the supplied <code>borderDist</code>, uses just the exterior grid nodes from
		 * the supersampled region, and is best when the site is a little further away and just sensitive to the size
		 * of the grid cell without integrating over the entire cell.
		 * 
		 * <p>Corder sampling, up to the supplied <code>cornerDist</code>, uses the corners of the grid cell as a crude
		 * approximation. This is fast (only 4 locations) and most appropriate at larger distances.
		 * 
		 * @param gridCell cell that this grid node represents
		 * @param targetSpacingKM target sample spacing (km)
		 * @param fullDist site-to-center distance (km) below which we should use the full resampled grid node
		 * @param borderDist site-to-center distance (km) below which we should use just the exterior of the resampled grid node
		 * @param cornerDist site-to-center distance (km) below which we should use all 4 corners of the grid cell
		 * @param applyToFinite if false, will only apply to point surfaces (not finite surfaces)
		 */
		public PoissonBuilder siteAdaptiveSupersampled(Region gridCell, double targetSpacingKM,
				double fullDist, double borderDist, double cornerDist, boolean applyToFinite) {
			Preconditions.checkState(data != null, "Must set point source data first");
			data = new GridCellSuperSamplingPoissonPointSourceData(data, loc,
					gridCell, targetSpacingKM, fullDist, borderDist, cornerDist, applyToFinite);
			return this;
		}
		
		/**
		 * Builds a {@link PoissonPointSource} for the given inputs.
		 * @return {@link PoissonPointSource} implementation
		 * @throws IllegalStateException if the location, point source data, or duration have not been set
		 */
		@SuppressWarnings("unchecked")
		public PoissonPointSource build() {
			checkValidLocAndData();
			
			Preconditions.checkState(Double.isFinite(duration) && duration > 0d, "Must set duration");
			
			return new PoissonPointSource(loc, trt, duration, data, distCorr, minMagForDistCorr);
		}
	}

}
