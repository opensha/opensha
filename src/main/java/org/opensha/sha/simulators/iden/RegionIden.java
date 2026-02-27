package org.opensha.sha.simulators.iden;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.geo.Region;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.SimulatorElement;

import com.google.common.base.Preconditions;

public class RegionIden extends AbstractRuptureIdentifier {
	
	private Region region;
	private BitSet checked;
	private BitSet insides;
	
	public RegionIden(Region region) {
		this.region = region;
		checked = new BitSet();
		insides = new BitSet();
	}

	@Override
	public synchronized boolean isMatch(SimulatorEvent event) {
		for (EventRecord rec : event) {
			int[] ids = rec.getElementIDs();
			List<SimulatorElement> elems = null;
			for (int i=0; i<ids.length; i++) {
				int id = ids[i];
				boolean inside;
				if (checked.get(id)) {
					inside = insides.get(id);
				} else {
					if (elems == null) {
						elems = rec.getElements();
						Preconditions.checkState(elems.size() == ids.length);
					}
					SimulatorElement elem = elems.get(i);
					Preconditions.checkState(elem.getID() == id);
					doCacheForElem(elem);
					inside = insides.get(id);
				}
				if (inside)
					return true;
			}
		}
		return false;
	}
	
	private void doCacheForElem(SimulatorElement elem) {
		checked.set(elem.getID());
		insides.set(elem.getID(), region.contains(elem.getCenterLocation()));
	}
	
	public synchronized void cacheForElement(SimulatorElement elem) {
		doCacheForElem(elem);
	}
	
	public synchronized void cacheForElements(Collection<SimulatorElement> elems) {
		for (SimulatorElement elem : elems)
			doCacheForElem(elem);
	}

	@Override
	public String getName() {
		return "RegionIden: "+region.getName();
	}

}
