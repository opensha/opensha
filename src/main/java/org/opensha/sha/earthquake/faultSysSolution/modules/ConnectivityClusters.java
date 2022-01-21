package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ConnectivityClusters implements SubModule<FaultSystemRupSet>,
JSON_TypeAdapterBackedModule<List<ConnectivityCluster>> {
	
	private FaultSystemRupSet rupSet;
	private List<ConnectivityCluster> clusters;
	
	public static ConnectivityClusters build(FaultSystemRupSet rupSet) {
		return new ConnectivityClusters(rupSet, ConnectivityCluster.build(rupSet));
	}

	public ConnectivityClusters(FaultSystemRupSet rupSet, List<ConnectivityCluster> clusters) {
		this.rupSet = rupSet;
		this.clusters = clusters;
	}
	
	@SuppressWarnings("unused") // used in deserialization
	private ConnectivityClusters() {};

	@Override
	public String getName() {
		return "Connectivity Clusters";
	}

	@Override
	public List<ConnectivityCluster> get() {
		return clusters;
	}

	@Override
	public void set(List<ConnectivityCluster> clusters) {
		Preconditions.checkState(this.clusters == null, "Clusters already initialized, should not call set() twice");
		this.clusters = clusters;
	}
	
	public int size() {
		return clusters.size();
	}
	
	public ConnectivityCluster get(int index) {
		return clusters.get(index);
	}
	
	public List<ConnectivityCluster> getSorted(Comparator<ConnectivityCluster> comp) {
		List<ConnectivityCluster> sorted = new ArrayList<>(clusters);
		Collections.sort(sorted, comp);
		return sorted;
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(parent));
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(newParent));
		return new ConnectivityClusters(newParent, clusters);
	}

	@Override
	public String getFileName() {
		return "connectivity_clusters.json";
	}

	@Override
	public Type getType() {
		return TypeToken.getParameterized(List.class, ConnectivityCluster.class).getType();
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {
		// do nothing, default serialization will work
	}

}
