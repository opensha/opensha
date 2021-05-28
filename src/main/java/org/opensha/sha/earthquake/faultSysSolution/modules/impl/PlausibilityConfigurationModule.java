package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.io.IOException;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class PlausibilityConfigurationModule extends RupSetModule implements JSON_BackedModule {
	
	private PlausibilityConfiguration config;

	public PlausibilityConfigurationModule() {
		super(null);
	}
	
	public PlausibilityConfigurationModule(FaultSystemRupSet rupSet, PlausibilityConfiguration config) {
		super(rupSet);
		this.config = config;
	}
	
	@Override
	public String getName() {
		return "Plausibility Configuration";
	}

	@Override
	public String getJSON_FileName() {
		return "plausibility.json";
	}
	
	public PlausibilityConfiguration getConfiguration() {
		return config;
	}

	@Override
	public Gson buildGson() {
		return PlausibilityConfiguration.buildGson(getRupSet().getFaultSectionDataList());
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		gson.toJson(config, PlausibilityConfiguration.class, out);
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		PlausibilityConfiguration config = gson.fromJson(in, PlausibilityConfiguration.class);
	}

}