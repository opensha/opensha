package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureCollection.FeatureCollectionAdapter;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;

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
AverageableModule.ConstantAverageable<RegionsOfInterest>, BranchAverageableModule<RegionsOfInterest> {
	
	private List<Region> regions;

	@SuppressWarnings("unused") // for deserialization
	private RegionsOfInterest() {}
	
	public RegionsOfInterest(Region... regions) {
		this(List.of(regions));
	}
	
	public RegionsOfInterest(List<Region> regions) {
		Preconditions.checkState(!regions.isEmpty(), "Must supply at least 1 region");
		this.regions = regions;
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

	@Override
	public FeatureCollection get() {
		List<Feature> features = new ArrayList<>();
		for (Region region : regions)
			features.add(region.toFeature());
		return new FeatureCollection(features);
	}

	@Override
	public void set(FeatureCollection features) {
		Preconditions.checkState(!features.features.isEmpty(), "Must supply at least 1 region");
		regions = new ArrayList<>();
		for (Feature feature : features)
			regions.add(Region.fromFeature(feature));
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {
		builder.registerTypeAdapter(getType(), new FeatureCollectionAdapter());
	}
	
	/**
	 * 
	 * @return immutable view of regions
	 */
	public List<Region> getRegions() {
		return ImmutableList.copyOf(regions);
	}

	@Override
	public Class<RegionsOfInterest> getAveragingType() {
		return RegionsOfInterest.class;
	}

	@Override
	public boolean isIdentical(RegionsOfInterest module) {
		if (regions.size() != module.regions.size())
			return false;
		for (int r=0; r<regions.size(); r++)
			if (!regions.get(r).equalsRegion(module.regions.get(r)))
				return false;
		return true;
	}

}
