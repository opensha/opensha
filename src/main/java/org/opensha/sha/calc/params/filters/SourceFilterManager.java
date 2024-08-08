package org.opensha.sha.calc.params.filters;

import java.util.ArrayList;
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
		return enabledFilterList;
	}
	
	public void setEnabled(SourceFilters filter, boolean enabled) {
		enabledFilterList = null;
		filterEnableds[filter.ordinal()] = enabled;
		if (enabled && filterInstances[filter.ordinal()] == null)
			filterInstances[filter.ordinal()] = filter.initFilter();
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