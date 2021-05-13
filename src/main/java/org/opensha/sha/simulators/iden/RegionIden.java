package org.opensha.sha.simulators.iden;

import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.geo.Region;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.SimulatorElement;

import com.google.common.collect.Maps;

public class RegionIden extends AbstractRuptureIdentifier {
	
	private Region region;
	private ConcurrentMap<SimulatorElement, Boolean> insideCache;
	
	public RegionIden(Region region) {
		this.region = region;
		insideCache = Maps.newConcurrentMap();
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		for (SimulatorElement elem: event.getAllElements()) {
			Boolean inside = insideCache.get(elem);
			if (inside == null) {
				inside = region.contains(elem.getCenterLocation());
				insideCache.putIfAbsent(elem, inside);
			}
			if (inside)
				return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return "RegionIden: "+region.getName();
	}

}
