package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureCollection.FeatureCollectionAdapter;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;

/**
 * Regions of interest, useful to view things like MFDs in particular regions that aren't explicitly constrained.
 * 
 * @author kevin
 *
 */
public class RegionsOfInterest implements JSON_TypeAdapterBackedModule<FeatureCollection>,
BranchAverageableModule<RegionsOfInterest> {
	
	private List<Region> regions;
	private List<IncrementalMagFreqDist> regionalMFDs;
	private List<TectonicRegionType> regionalTRTs;

	@SuppressWarnings("unused") // for deserialization
	private RegionsOfInterest() {}
	
	public RegionsOfInterest(Region... regions) {
		this(List.of(regions));
	}
	
	public RegionsOfInterest(List<Region> regions) {
		this(regions, null);
	}
	
	public RegionsOfInterest(List<Region> regions, List<IncrementalMagFreqDist> regionalMFDs) {
		this(regions, regionalMFDs, null);
	}
	
	public RegionsOfInterest(List<Region> regions, List<IncrementalMagFreqDist> regionalMFDs, List<TectonicRegionType> regionalTRTs) {
		Preconditions.checkState(!regions.isEmpty(), "Must supply at least 1 region");
		this.regions = regions;
		Preconditions.checkState(regionalMFDs == null || regionalMFDs.size() == regions.size(),
				"If regional MFDs are supplied, there must be exactly one for each region");
		this.regionalMFDs = regionalMFDs;
		Preconditions.checkState(regionalTRTs == null || regionalTRTs.size() == regions.size(),
				"If regional TRTs are supplied, there must be exactly one for each region");
		this.regionalTRTs = regionalTRTs;
	}

	@Override
	public String getFileName() {
		return "regions_of_interest.json";
	}

	@Override
	public String getName() {
		return "Regions of Interest";
	}

	@Override
	public Type getType() {
		return FeatureCollection.class;
	}
	
	public static final String MFD_PROPERTY_NAME = "MFD";
	public static final String TRT_PROPERTY_NAME = "TectonicRegionType";

	@Override
	public FeatureCollection get() {
		List<Feature> features = new ArrayList<>();
		for (int i=0; i<regions.size(); i++) {
			Region region = regions.get(i);
			Feature feature = region.toFeature();
			if (regionalMFDs != null) {
				IncrementalMagFreqDist mfd = regionalMFDs.get(i);
				if (mfd != null)
					feature.properties.set(MFD_PROPERTY_NAME, mfd);
			}
			if (regionalTRTs != null) {
				TectonicRegionType trt = regionalTRTs.get(i);
				if (trt != null)
					feature.properties.set(TRT_PROPERTY_NAME, trt.name());
			}
			features.add(feature);
		}
		return new FeatureCollection(features);
	}

	@Override
	public void set(FeatureCollection features) {
		Preconditions.checkState(!features.features.isEmpty(), "Must supply at least 1 region");
		regions = new ArrayList<>();
		regionalMFDs = null;
		for (Feature feature : features) {
			Region region = Region.fromFeature(feature);
			IncrementalMagFreqDist mfd = feature.properties.get("MFD", null);
			if (mfd != null) {
				if (regionalMFDs == null)
					regionalMFDs = new ArrayList<>();
				while (regionalMFDs.size() < regions.size())
					regionalMFDs.add(null);
				regionalMFDs.add(mfd);
			}
			String trtName = feature.properties.getString(TRT_PROPERTY_NAME, null);
			if (trtName != null) {
				TectonicRegionType trt = TectonicRegionType.valueOf(trtName);
				if (regionalTRTs == null)
					regionalTRTs = new ArrayList<>();
				while (regionalTRTs.size() < regions.size())
					regionalTRTs.add(null);
				regionalTRTs.add(trt);
			}
			regions.add(region);
		}
		if (regionalMFDs != null)
			while (regionalMFDs.size() < regions.size())
				regionalMFDs.add(null);
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {
		builder.registerTypeAdapter(getType(), new FeatureCollectionAdapter());
	}
	
	/**
	 * @return immutable view of regions
	 */
	public List<Region> getRegions() {
		return ImmutableList.copyOf(regions);
	}
	
	/**
	 * @return immutable view of MFDs if they exist, or null
	 */
	public List<IncrementalMagFreqDist> getMFDs() {
		return regionalMFDs == null ? null : Collections.unmodifiableList(regionalMFDs);
	}
	
	/**
	 * @return immutable view of TRTs if they exist, or null
	 */
	public List<TectonicRegionType> getTRTs() {
		return regionalTRTs == null ? null : Collections.unmodifiableList(regionalTRTs);
	}
	
	public boolean areRegionsIdenticalTo(RegionsOfInterest o) {
		if (regions.size() != o.regions.size())
			return false;
		for (int i=0; i<regions.size(); i++)
			if (!regions.get(i).equalsRegion(o.regions.get(i)))
				return false;
		return true;
	}

	@Override
	public AveragingAccumulator<RegionsOfInterest> averagingAccumulator() {
		
		return new AveragingAccumulator<>() {
			
			private List<Region> regions;
			private List<IncrementalMagFreqDist> regionalMFDs;
			private List<String> mfdNames;
			private List<String> mfdBoundNames;
			private List<TectonicRegionType> regionalTRTs;
			private double weightSum = 0d;
			
			@Override
			public Class<RegionsOfInterest> getType() {
				return RegionsOfInterest.class;
			}

			@Override
			public void process(RegionsOfInterest module, double relWeight) {
				if (regions == null) {
					regions = new ArrayList<>(module.regions);
					if (module.regionalMFDs != null) {
						regionalMFDs = new ArrayList<>();
						mfdNames = new ArrayList<>();
						mfdBoundNames = new ArrayList<>();
						for (IncrementalMagFreqDist mfd : module.regionalMFDs) {
							if (mfd == null || relWeight == 0d) {
								regionalMFDs.add(null);
								mfdNames.add(null);
								mfdBoundNames.add(null);
							} else {
								regionalMFDs.add(InversionTargetMFDs.Precomputed.buildSameSize(mfd));
								if (mfd.getName() != null && !mfd.getName().isBlank() && !mfd.getName().equals(mfd.getDefaultName()))
									mfdNames.add(mfd.getName());
								else
									mfdNames.add(null);
								if (mfd instanceof UncertainBoundedIncrMagFreqDist) {
									UncertainBoundedIncrMagFreqDist bounded = (UncertainBoundedIncrMagFreqDist)mfd;
									String boundName = bounded.getBoundName();
									if (boundName != null && !boundName.isBlank() && !boundName.equals(bounded.getDefaultBoundName()))
										mfdBoundNames.add(boundName);
									else
										mfdBoundNames.add(null);
								} else {
									mfdBoundNames.add(null);
								}
							}
						}
					} else {
						regionalMFDs = null;
					}
					if (module.regionalTRTs != null) {
						regionalTRTs = new ArrayList<>();
						for (TectonicRegionType trt : module.regionalTRTs)
							regionalTRTs.add(trt);
					} else {
						regionalMFDs = null;
					}
				}
				Preconditions.checkState(regions.size() == module.regions.size());
				if (regionalMFDs != null)
					if (module.regionalMFDs == null)
						regionalMFDs = null;
				if (regionalTRTs != null)
					if (module.regionalTRTs == null)
						regionalTRTs = null;
				for (int r=0; r<regions.size(); r++) {
					Preconditions.checkState(regions.get(r).equalsRegion(module.regions.get(r)));
					if (regionalMFDs != null) {
						if (module.regionalMFDs == null) {
							regionalMFDs = null;
						} else {
							IncrementalMagFreqDist mfd = module.regionalMFDs.get(r);
							IncrementalMagFreqDist runningMFD = regionalMFDs.get(r);
							if (mfd == null) {
								regionalMFDs.set(r, null);
							} else if (runningMFD != null) {
								regionalMFDs.set(r, InversionTargetMFDs.Precomputed.averageInWeighted(
										runningMFD, mfd, "Regional MFD", relWeight));
								if (mfdNames.get(r) != null && !mfdNames.get(r).equals(mfd.getName()))
									mfdNames.set(r, null);
								if (mfdBoundNames.get(r) != null && mfd instanceof UncertainBoundedIncrMagFreqDist
										&& !mfdBoundNames.get(r).equals(((UncertainBoundedIncrMagFreqDist)mfd).getBoundName()))
									mfdBoundNames.set(r, null);
							}
						}
					}
					if (regionalTRTs != null) {
						if (module.regionalTRTs == null) {
							regionalTRTs = null;
						} else {
							TectonicRegionType prevTRT = regionalTRTs.get(r);
							TectonicRegionType newTRT = module.regionalTRTs.get(r);
							if (!Objects.equal(prevTRT, newTRT))
								regionalTRTs.set(r, null);
						}
					}
				}
				weightSum += relWeight;
			}

			@Override
			public RegionsOfInterest getAverage() {
				boolean hasNonNullMFD = false;
				if (regionalMFDs != null)
					for (IncrementalMagFreqDist mfd : regionalMFDs)
						hasNonNullMFD |= mfd != null;
				if (hasNonNullMFD) {
					for (int r=0; r<regionalMFDs.size(); r++) {
						IncrementalMagFreqDist mfd = regionalMFDs.get(r);
						if (mfd != null) {
							if (mfdNames.get(r) != null)
								mfd.setName(mfdNames.get(r));
							if (mfdBoundNames.get(r) != null && mfd instanceof UncertainBoundedIncrMagFreqDist)
								((UncertainBoundedIncrMagFreqDist)mfd).setBoundName(mfdBoundNames.get(r));
							InversionTargetMFDs.Precomputed.scaleToTotWeight(mfd, weightSum);
						}
					}
				} else {
					regionalMFDs = null;
				}
				boolean hasNonNullTRT = false;
				if (regionalTRTs != null)
					for (TectonicRegionType trt : regionalTRTs)
						hasNonNullTRT |= trt != null;
				if (!hasNonNullTRT)
					regionalTRTs = null;
				return new RegionsOfInterest(regions, regionalMFDs, regionalTRTs);
			}
			
		};
	}

}
