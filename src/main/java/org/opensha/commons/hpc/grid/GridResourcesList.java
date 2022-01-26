package org.opensha.commons.hpc.grid;

import java.io.Serializable;
import java.util.ArrayList;

public class GridResourcesList implements Serializable {
	
	private ArrayList<ResourceProvider> resourceProviders;
	private ArrayList<SubmitHost> submitHosts;
	private ArrayList<StorageHost> storageHosts;
	
	public GridResourcesList(ArrayList<ResourceProvider> resourceProviders, ArrayList<SubmitHost> submitHosts,
			ArrayList<StorageHost> storageHosts) {
		this.resourceProviders = resourceProviders;
		this.submitHosts = submitHosts;
		this.storageHosts = storageHosts;
	}

	public ArrayList<ResourceProvider> getResourceProviders() {
		return resourceProviders;
	}

	public ArrayList<SubmitHost> getSubmitHosts() {
		return submitHosts;
	}

	public ArrayList<StorageHost> getStorageHosts() {
		return storageHosts;
	}

}
