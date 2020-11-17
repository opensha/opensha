package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.HashMap;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Extension of {@link ClusterRupture} that can store a payload of data for plausibility filters.
 * This allows filters to speed up their checks during rupture building if results from the prior (smaller)
 * version of this rupture can inform how to proceed at this step.
 * 
 * @author kevin
 *
 */
public class FilterDataClusterRupture extends ClusterRupture {
	
	private Map<PlausibilityFilter, Object> filterData;

	public FilterDataClusterRupture(FaultSubsectionCluster cluster) {
		super(cluster);
	}
	
	private FilterDataClusterRupture(FaultSubsectionCluster[] clusters, ImmutableSet<Jump> internalJumps,
			ImmutableMap<Jump, ClusterRupture> splays, UniqueRupture unique, UniqueRupture internalUnique) {
		super(clusters, internalJumps, splays, unique, internalUnique);
	}

	public synchronized void addFilterData(PlausibilityFilter filter, Object data) {
		if (filterData == null)
			filterData = new HashMap<>();
		filterData.put(filter, data);
	}
	
	public synchronized boolean removeFilterData(PlausibilityFilter filter) {
		if (filterData == null)
			return false;
		return filterData.remove(filter) != null;
	}
	
	public synchronized Object getFilterData(PlausibilityFilter filter) {
		if (filterData == null)
			return null;
		return filterData.get(filter);
	}

	@Override
	public synchronized FilterDataClusterRupture take(Jump jump) {
		ClusterRupture orig = super.take(jump);
		
		FilterDataClusterRupture ret = new FilterDataClusterRupture(orig.clusters, orig.internalJumps,
				orig.splays, orig.unique, orig.internalUnique);
		if (filterData != null)
			ret.filterData = new HashMap<>(filterData);
		
		return ret;
	}

}
