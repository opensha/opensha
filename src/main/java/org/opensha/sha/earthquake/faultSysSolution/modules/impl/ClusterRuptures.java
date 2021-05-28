package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ClusterRuptures extends RupSetModule implements JSON_BackedModule {
	
	private List<ClusterRupture> clusterRuptures;

	public ClusterRuptures() {
		super(null);
	}
	
	public ClusterRuptures(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
		super(rupSet);
		Preconditions.checkState(clusterRuptures.size() == rupSet.getNumRuptures());
		this.clusterRuptures = clusterRuptures;
	}

	@Override
	public String getName() {
		return "Cluster Ruptures";
	}

	@Override
	public String getJSON_FileName() {
		return "cluster_ruptures.json";
	}

	@Override
	public Gson buildGson() {
		return ClusterRupture.buildGson(getRupSet().getFaultSectionDataList(), false);
	}
	
	public List<ClusterRupture> getClusterRuptures() {
		return clusterRuptures;
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		// TODO make JSON more compact
		Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
		gson.toJson(clusterRuptures, listType, out);
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
		List<ClusterRupture> ruptures = gson.fromJson(in, listType);
		Preconditions.checkState(ruptures.size() == getRupSet().getNumRuptures());
		this.clusterRuptures = ruptures;
	}

}