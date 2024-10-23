package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.PointSource.PoissonPointSourceData;
import org.opensha.sha.earthquake.util.GridCellSuperSamplingPoissonPointSourceData;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public abstract class PointSource extends ProbEqkSource {
	
	private Location loc;
	private PointSurface pointSurf; // lazily initialized, used for getSourceSurface()
	private LocationList sourceLocs; // lazily initialized, used for getAllSourceLocs()
	protected WeightedList<PointSourceDistanceCorrection> distCorrs;

	public PointSource(Location loc) {
		this(loc, TECTONIC_REGION_TYPE_DEFAULT, null);
	}

	public PointSource(Location loc, TectonicRegionType tectonicRegionType, WeightedList<PointSourceDistanceCorrection> distCorrs) {
		this.loc = loc;
		this.setTectonicRegionType(tectonicRegionType);
		this.setDistCorrs(distCorrs);
	}

	public WeightedList<PointSourceDistanceCorrection> getDistCorrs() {
		return distCorrs;
	}

	public void setDistCorrs(WeightedList<PointSourceDistanceCorrection> distCorrs) {
		if (distCorrs != null && !(distCorrs instanceof WeightedList.Unmodifiable<?>))
			distCorrs = new WeightedList.Unmodifiable<>(distCorrs);
		this.distCorrs = distCorrs;
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
	
	public static abstract class PoissonPointSource extends PointSource {

		private double duration;

		private PoissonPointSource(Location loc, double duration) {
			super(loc);
			this.duration = duration;
		}

		private PoissonPointSource(Location loc, TectonicRegionType tectonicRegionType,
				double duration, WeightedList<PointSourceDistanceCorrection> distCorrs) {
			super(loc, tectonicRegionType, distCorrs);
			this.duration = duration;
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
		
	}
	
	public static interface PoissonPointSourceData {
		
		public int getNumRuptures();
		
		public double getMagnitude(int rupIndex);
		
		public double getAveRake(int rupIndex);
		
		public double getRate(int rupIndex);
		
		public RuptureSurface getSurface(int rupIndex);
		
		public boolean isFinite(int rupIndex);
		
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex);
		
	}
	
	public static class PoissonPointSourceImpl extends PoissonPointSource {
		
		private PoissonPointSourceData data;
		
		private int numRuptures;
		private short[] dataIndexes;
		private short[] corrIndexes;

		public PoissonPointSourceImpl(Location loc, TectonicRegionType tectonicRegionType,
				double duration, PoissonPointSourceData data,
				WeightedList<PointSourceDistanceCorrection> distCorrs) {
			super(loc, tectonicRegionType, duration, distCorrs);
			this.data = data;
			updateCountAndIndexes();
		}
		
		@Override
		public void setDistCorrs(WeightedList<PointSourceDistanceCorrection> distCorrs) {
			super.setDistCorrs(distCorrs);
			if (data != null) // will be null during the super constructor
				updateCountAndIndexes();
		}

		private void updateCountAndIndexes() {
			int dataRupCount = data.getNumRuptures();
			if (distCorrs == null || distCorrs.size() == 1) {
				this.numRuptures = dataRupCount;
				this.dataIndexes = null;
				this.corrIndexes = null;
			} else {
				// we have multiple distance corrections per rupture and need to pre-store the indexes
				// we can't just calculate indexes because some ruptures may be finite and not use the distance corrections
				Preconditions.checkState(dataRupCount < Short.MAX_VALUE); // should never be the case, but good to check
				int numCorrs = distCorrs.size();
				Preconditions.checkState(numCorrs > 1); // mostly to make sure it's not zero
				Preconditions.checkState(numCorrs < Short.MAX_VALUE); // should never be the case, but good to check
				// rupture count if there are no finite ruptures; finite ruptures don't have distance corrections
				int numIfNoFinite = dataRupCount * numCorrs;
				// this might be larger than we need, but that's ok (we'll only read up to numRuptures)
				dataIndexes = new short[numIfNoFinite];
				corrIndexes = new short[numIfNoFinite];
				int index = 0;
				for (short d=0; d<dataRupCount; d++) {
					if (data.isFinite(d)) {
						// finite surface: just 1 rupture instance (no distance correction)
						dataIndexes[index] = d;
						corrIndexes[index] = -1;
						index++;
					} else {
						// point surface: 1 rupture instance for each distance correction
						for (short i=0; i<numCorrs; i++) {
							dataIndexes[index] = d;
							corrIndexes[index] = i;
							index++;
						}
					}
				}
				this.numRuptures = index;
				// fill in any extra room in the arrays with no data flags
				for (int i=index; i<numIfNoFinite; i++) {
					dataIndexes[i] = -1;
					corrIndexes[i] = -1;
				}
			}
		}

		@Override
		public int getNumRuptures() {
			Preconditions.checkState(numRuptures > 0);
			return numRuptures;
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			// this is the index in the upstream data object, which may not equal or rupture index in the case where
			// we have multiple distance corrections per rupture
			int dataIndex;
			PointSourceDistanceCorrection distCorr;
			double rate;
			if (dataIndexes == null) {
				// simple case: no distance corrections, or 1 per rupture
				dataIndex = nRupture;
				if (distCorrs == null || data.isFinite(dataIndex)) {
					distCorr = null;
				} else {
					Preconditions.checkState(distCorrs.size() == 1);
					distCorr = distCorrs.getValue(0);
				}
				rate = data.getRate(dataIndex);
			} else {
				dataIndex = dataIndexes[nRupture];
				int corrIndex = corrIndexes[nRupture];
				rate = data.getRate(dataIndex);
				if (corrIndex < 0) {
					// finite rupture, no correction
					distCorr = null;
				} else {
					Preconditions.checkState(corrIndex < distCorrs.size());
					distCorr = distCorrs.getValue(corrIndex);
					rate *= distCorrs.getWeight(corrIndex);
				}
			}
			double mag = data.getMagnitude(dataIndex);
			double rake = data.getAveRake(dataIndex);
			RuptureSurface surf = data.getSurface(dataIndex);
			if (distCorr != null) {
				Preconditions.checkState(surf instanceof PointSurface,
						"Surface for rupture %s with data index %s was labeled as non-finite, but is not a PointSurface: %s",
						nRupture, dataIndex, surf);
				if (distCorrs.size() > 1) {
					// paranoid for thread-safety: copy the surface in case it's being cached and shared for multiple
					// ruptures instances since we'll be setting different corrections on the same original data index
					RuptureSurface copy = surf.copyShallow();
					Preconditions.checkState(copy.getClass().equals(surf.getClass()),
							"Shallow copy of surface with type %s is of a different type: %s",
							surf.getClass(), copy.getClass());
					surf = copy;
				}
				((PointSurface)surf).setDistanceCorrection(distCorr, mag);
			}
			Location hypo = data.getHypocenter(getLocation(), surf, dataIndex);
			
			double prob = rateToProb(rate, getDuration());
			return new ProbEqkRupture(mag, rake, prob, surf, hypo);
		}
		
	}
	
	public static interface SiteAdaptivePoissonPointSourceData extends PoissonPointSourceData {
		
		public PoissonPointSourceData getForSite(Site site);
		
	}
	
	private static class SiteAdaptivePoissonPointSourceImpl extends PoissonPointSourceImpl implements SiteAdaptiveSource {

		private SiteAdaptivePoissonPointSourceData data;

		public SiteAdaptivePoissonPointSourceImpl(Location loc, TectonicRegionType tectonicRegionType, double duration,
				SiteAdaptivePoissonPointSourceData data,
				WeightedList<PointSourceDistanceCorrection> distCorrs) {
			super(loc, tectonicRegionType, duration, data, distCorrs);
			this.data = data;
		}

		@Override
		public synchronized ProbEqkSource getForSite(Site site) {
			PoissonPointSourceData dataForSite = data.getForSite(site);
			if (dataForSite == data)
				// it returned itself, nothing for this site
				return this;
			return new PoissonPointSourceImpl(getLocation(), getTectonicRegionType(),
					getDuration(), dataForSite, getDistCorrs());
		}
		
	}
	
	private static class DistanceDependentSiteAdaptiveData implements SiteAdaptivePoissonPointSourceData {
		
		private PoissonPointSourceData standardData;
		private PoissonPointSourceData nearbyData;
		private Location sourceLoc;
		private float distCutoff;

		private DistanceDependentSiteAdaptiveData(PoissonPointSourceData standardData,
				PoissonPointSourceData nearbyData, Location sourceLoc, float distCutoff) {
			this.standardData = standardData;
			this.nearbyData = nearbyData;
			this.sourceLoc = sourceLoc;
			this.distCutoff = distCutoff;
		}

		@Override
		public int getNumRuptures() {
			return standardData.getNumRuptures();
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return standardData.getMagnitude(rupIndex);
		}

		@Override
		public double getAveRake(int rupIndex) {
			return standardData.getAveRake(rupIndex);
		}

		@Override
		public double getRate(int rupIndex) {
			return standardData.getRate(rupIndex);
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			return standardData.getSurface(rupIndex);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			return standardData.isFinite(rupIndex);
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			return standardData.getHypocenter(sourceLoc, rupSurface, rupIndex);
		}

		@Override
		public PoissonPointSourceData getForSite(Site site) {
			if ((float)LocationUtils.horzDistanceFast(sourceLoc, site.getLocation()) <= distCutoff)
				return nearbyData;
			return this;
		}
		
	}
	
	/**
	 * Builds point source data for the given MFD, focal mechanism weights, and {@link FocalMechSurfaceBuilder}
	 * 
	 * @param mfd
	 * @param mechWeights
	 * @param surfaceBuilder
	 * @return the builder
	 */
	public static PoissonPointSourceData dataForMFDandFocalMechs(IncrementalMagFreqDist mfd, Map<FocalMech, Double> mechWeights,
			FocalMechSurfaceBuilder surfaceBuilder) {
		return new MFDandMechPoissonPointSourceData(mfd, mechWeights, surfaceBuilder);
	} 
	
	private static class MFDandMechPoissonPointSourceData implements PoissonPointSourceData {
		
		private IncrementalMagFreqDist mfd;
		private FocalMechSurfaceBuilder surfBuilder;
		private Map<FocalMech, Double> mechWeights;
		
		private short[] mfdIndexes;
		private short[] surfIndexes;
		private FocalMech[] mechs;
		
		private MFDandMechPoissonPointSourceData(IncrementalMagFreqDist mfd, Map<FocalMech, Double> mechWeights,
				FocalMechSurfaceBuilder surfBuilder) {
			this.mechWeights = mechWeights;
			this.surfBuilder = surfBuilder;
			Preconditions.checkState(mfd.size() < Short.MAX_VALUE);
			int numRups = 0;
			List<FocalMech> nonzeroMechs = new ArrayList<>(mechWeights.size());
			double weightSum = 0d;
			for (FocalMech mech : mechWeights.keySet()) {
				double weight = mechWeights.get(mech);
				Preconditions.checkState(weight >= 0d && weight <= 1d, "Bad weight for %s: %s", mech, weight);
				if (weight > 0d) {
					weightSum += weight;
					nonzeroMechs.add(mech);
				}
			}
			Preconditions.checkState(!nonzeroMechs.isEmpty(), "All FocalMech weights are zero?");
			Preconditions.checkState((float)weightSum == 1f, "FocalMech weights don't sum to 1: %s", (float)weightSum);
			boolean anyMultiple = false;
			for (int m=0; m<mfd.size(); m++) {
				if (mfd.getY(m) == 0d)
					continue;
				double mag = mfd.getX(m);
				for (FocalMech mech : nonzeroMechs) {
					int magMechCount = surfBuilder.getNumSurfaces(mag, mech);
					Preconditions.checkState(magMechCount > 0,
							"Surface count is %s for mech=%s and mag=%s", magMechCount, mech, mag);
					numRups += magMechCount;
					anyMultiple |= magMechCount > 1;
				}
			}
			Preconditions.checkState(numRups > 0, "No ruptures for MFD; all zeros?\n%s", mfd);
			mfdIndexes = new short[numRups];
			mechs = new FocalMech[numRups];
			surfIndexes = anyMultiple ? new short[numRups] : null;
			int index = 0;
			for (int m=0; m<mfd.size(); m++) {
				if (mfd.getY(m) == 0d)
					continue;
				double mag = mfd.getX(m);
				for (FocalMech mech : nonzeroMechs) {
					int magMechCount = surfBuilder.getNumSurfaces(mag, mech);
					for (int i=0; i<magMechCount; i++) {
						mfdIndexes[index] = (short)m;
						mechs[index] = mech;
						if (anyMultiple)
							surfIndexes[index] = (short)i;
						index++;
					}
				}
			}
			Preconditions.checkState(index == numRups);
		}

		@Override
		public int getNumRuptures() {
			return mechs.length;
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return mfd.getX(mfdIndexes[rupIndex]);
		}

		@Override
		public double getAveRake(int rupIndex) {
			return mechs[rupIndex].rake();
		}

		@Override
		public double getRate(int rupIndex) {
			double weight = mfd.getY(mfdIndexes[rupIndex]) * mechWeights.get(mechs[rupIndex]);
			if (surfIndexes != null)
				weight *= surfBuilder.getSurfaceWeight(mfd.getX(mfdIndexes[rupIndex]), mechs[rupIndex],
						surfIndexes == null ? 0 : surfIndexes[rupIndex]);
			return weight;
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			return surfBuilder.getSurface(mfd.getX(mfdIndexes[rupIndex]), mechs[rupIndex],
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			return surfBuilder.getHypocenter(sourceLoc, rupSurface);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			return surfBuilder.isSurfaceFinite(mfd.getX(mfdIndexes[rupIndex]), mechs[rupIndex],
					surfIndexes == null ? 0 : surfIndexes[rupIndex]);
		}
	}
	
	public static interface FocalMechSurfaceBuilder {
		public int getNumSurfaces(double magnitude, FocalMech mech);
		
		public RuptureSurface getSurface(double magnitude, FocalMech mech, int surfaceIndex);
		
		public double getSurfaceWeight(double magnitude, FocalMech mech, int surfaceIndex);
		
		public boolean isSurfaceFinite(double magnitude, FocalMech mech, int surfaceIndex);
		
		public default WeightedList<RuptureSurface> getSurfaces(double magnitude, FocalMech mech) {
			int num = getNumSurfaces(magnitude, mech);
			if (num == 1)
				return WeightedList.evenlyWeighted(getSurface(magnitude, mech, 0));
			WeightedList<RuptureSurface> ret = new WeightedList<>(num);
			for (int i=0; i<num; i++)
				ret.add(getSurface(magnitude, mech, i), getSurfaceWeight(magnitude, mech, i));
			Preconditions.checkState(ret.isNormalized(),
					"Surface weights aren't normalized for mag=%s, mech=%s", magnitude, mech);
			return ret;
		}
		
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface);
	}
	
	public static PoissonBuilder poissonBuilder(Location loc, TectonicRegionType trt) {
		return new PoissonBuilder(loc).tectonicRegionType(trt);
	}
	
	public static class PoissonBuilder {
		
		private Location loc;
		private PoissonPointSourceData data;
		private TectonicRegionType trt = TECTONIC_REGION_TYPE_DEFAULT;
		private double duration = Double.NaN;
		private WeightedList<PointSourceDistanceCorrection> distCorrs;

		/**
		 * Initializes a Poisson point source builder for the given location
		 * 
		 * @param loc
		 */
		private PoissonBuilder(Location loc) {
			this.loc = loc;
		}
		
		/**
		 * Sets the {@link TectonicRegionType} for this source
		 * 
		 * @param trt
		 * @return the builder
		 * @see {@link ProbEqkSource#TECTONIC_REGION_TYPE_DEFAULT}
		 */
		public PoissonBuilder tectonicRegionType(TectonicRegionType trt) {
			this.trt = trt;
			return this;
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
		 * Sets the data that constitutes this point source
		 * 
		 * @param data
		 * @return the builder
		 */
		public PoissonBuilder data(PoissonPointSourceData data) {
			this.data = data;
			return this;
		}
		
		/**
		 * Builds point source data for the given MFD, focal mechanism weights, and {@link FocalMechSurfaceBuilder}
		 * 
		 * @param mfd
		 * @param mechWeights
		 * @param surfaceBuilder
		 * @return the builder
		 */
		public PoissonBuilder forMFDandFocalMechs(IncrementalMagFreqDist mfd, Map<FocalMech, Double> mechWeights,
				FocalMechSurfaceBuilder surfaceBuilder) {
			return data(new MFDandMechPoissonPointSourceData(mfd, mechWeights, surfaceBuilder));
		}
		
		/**
		 * Sets point source distance correction(s) to be used. This need not be final, and can be updated on the built
		 * source by calling {@link PointSource#setDistCorrs(WeightedList)}.
		 * 
		 * @param distCorrs
		 * @return the builder
		 */
		public PoissonBuilder distCorrs(PointSourceDistanceCorrections distCorrs) {
			if (distCorrs == null) {
				this.distCorrs = null;
				return this;
			}
			return distCorrs(distCorrs.get());
		}
		
		/**
		 * Sets point source distance correction(s) to be used. This need not be final, and can be updated on the built
		 * source by calling {@link PointSource#setDistCorrs(WeightedList)}.
		 * 
		 * @param distCorrs
		 * @return the builder
		 */
		public PoissonBuilder distCorrs(WeightedList<PointSourceDistanceCorrection> distCorrs) {
			Preconditions.checkState(distCorrs == null || (!distCorrs.isEmpty() && distCorrs.isNormalized()));
			this.distCorrs = distCorrs;
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
		 */
		public PoissonBuilder siteAdaptiveSupersampled(Region gridCell, double targetSpacingKM,
				double fullDist, double borderDist, double cornerDist) {
			Preconditions.checkState(data != null, "Must set point source data first");
			data = new GridCellSuperSamplingPoissonPointSourceData(data, loc,
					gridCell, targetSpacingKM, fullDist, borderDist, cornerDist);
			return this;
		}
		
		/**
		 * Builds a {@link PoissonPointSource} for the given inputs.
		 * @return {@link PoissonPointSource} implementation
		 * @throws IllegalStateException if the location, point source data, or duration have not been set
		 */
		public PoissonPointSource build() {
			Preconditions.checkState(loc != null);
			Preconditions.checkState(data != null);
			
			Preconditions.checkState(Double.isFinite(duration) && duration > 0d, "Must set duration");
			
			if (data instanceof SiteAdaptivePoissonPointSourceData)
				return new SiteAdaptivePoissonPointSourceImpl(loc, trt, duration,
						(SiteAdaptivePoissonPointSourceData)data, distCorrs);
			return new PoissonPointSourceImpl(loc, trt, duration, data, distCorrs);
		}
	}

}
