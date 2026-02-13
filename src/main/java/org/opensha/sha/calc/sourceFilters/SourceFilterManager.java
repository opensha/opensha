package org.opensha.sha.calc.sourceFilters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

public class SourceFilterManager {
	static final SourceFilters[] filters = SourceFilters.values();
	private SourceFilter[] filterInstances;
	private boolean[] filterEnableds;
	
	private List<SourceFilter> enabledFilterList = null;
	
	public SourceFilterManager(SourceFilters... enabledByDefault) {
		filterInstances = new SourceFilter[filters.length];
		filterEnableds = new boolean[filters.length];
		
		for (SourceFilters filter : enabledByDefault) {
			filterInstances[filter.ordinal()] = filter.initFilter();
			filterEnableds[filter.ordinal()] = true;
		}
	}
	
	public List<SourceFilter> getEnabledFilters() {
		if (enabledFilterList == null) {
			List<SourceFilter> ret = new ArrayList<>();
			for (int i=0; i<filterInstances.length; i++) {
				if (filterEnableds[i]) {
					Preconditions.checkNotNull(filterInstances[i]);
					ret.add(filterInstances[i]);
				}
			}
			enabledFilterList = ret;
		}
		return Collections.unmodifiableList(enabledFilterList);
	}
	
	public void setEnabled(SourceFilters filter, boolean enabled) {
		enabledFilterList = null;
		filterEnableds[filter.ordinal()] = enabled;
		if (enabled && filterInstances[filter.ordinal()] == null)
			filterInstances[filter.ordinal()] = filter.initFilter();
	}
	
	public void setEnabled(SourceFilter filter, boolean enabled) {
		for (int i=0; i<filterInstances.length; i++) {
			SourceFilter oFilter = filterInstances[i];
			if (oFilter == filter) {
				setEnabled(filters[i], enabled);
				return;
			}
		}
		throw new IllegalArgumentException("Supplied filter does not exist internally: "+filter);
	}
	
	public boolean isEnabled(SourceFilters filter) {
		return filterEnableds[filter.ordinal()];
	}
	
	public <E extends SourceFilter> E getFilterInstance(Class<E> clazz) {
		for (SourceFilters filter : SourceFilters.values()) {
			if (clazz.isAssignableFrom(filter.getFilterClass())) {
				SourceFilter instance = getFilterInstance(filter);
				Preconditions.checkState(clazz.isAssignableFrom(instance.getClass()));
				return clazz.cast(instance);
			}
		}
		throw new IllegalStateException("Unexpected filter class: "+clazz.getName());
	}
	
	public SourceFilter getFilterInstance(SourceFilters filter) {
		if (filterInstances[filter.ordinal()] == null)
			filterInstances[filter.ordinal()] = filter.initFilter();
		return filterInstances[filter.ordinal()];
	}
	
	public double getMaxDistance() {
		double maxDist = Double.POSITIVE_INFINITY;
		if (isEnabled(SourceFilters.FIXED_DIST_CUTOFF))
//			maxDist = fixedDistanceFilter.getMaxDistance();
			maxDist = getFilterInstance(FixedDistanceCutoffFilter.class).getMaxDistance();
		if (isEnabled(SourceFilters.MAG_DIST_CUTOFFS))
			maxDist = Math.min(maxDist, getFilterInstance(MagDependentDistCutoffFilter.class).getMagDistFunc().getMaxX());
		if (isEnabled(SourceFilters.TRT_DIST_CUTOFFS))
			maxDist = Math.min(maxDist, getFilterInstance(TectonicRegionDistCutoffFilter.class).getCutoffs().getLargestCutoffDist());
		return maxDist;
	}
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("[");
		boolean first = true;
		for (int i=0; i<filters.length; i++) {
			if (filterEnableds[i]) {
				if (first) {
					first = false;
				} else {
					str.append(", ");
				}
				str.append(filterInstances[i].toString());
			}
		}
		str.append("]");
		return str.toString();
	}
}