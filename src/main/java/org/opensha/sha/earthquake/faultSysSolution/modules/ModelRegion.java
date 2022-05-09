package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;

import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;

public class ModelRegion implements JSON_TypeAdapterBackedModule<Feature>,
AverageableModule.ConstantAverageable<ModelRegion>, BranchAverageableModule<ModelRegion>  {
	
	private Region region;
	
	@SuppressWarnings("unused") // for serialization
	private ModelRegion() {}
	
	public ModelRegion(Region region) {
		Preconditions.checkNotNull(region);
		this.region = region;
	}

	@Override
	public String getFileName() {
		return "model_region.json";
	}

	@Override
	public String getName() {
		return "Model Region";
	}

	@Override
	public Type getType() {
		return Feature.class;
	}

	@Override
	public Feature get() {
		return region.toFeature();
	}
	
	public Region getRegion() {
		return region;
	}

	@Override
	public void set(Feature value) {
		this.region = Region.fromFeature(value);
	}
	
	public void set(Region region) {
		this.region = region;
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {}

	@Override
	public Class<ModelRegion> getAveragingType() {
		return ModelRegion.class;
	}

	@Override
	public boolean isIdentical(ModelRegion module) {
		return module == this || module.region.equalsRegion(region);
	}

}
