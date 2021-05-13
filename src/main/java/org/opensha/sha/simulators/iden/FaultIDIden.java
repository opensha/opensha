package org.opensha.sha.simulators.iden;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

public class FaultIDIden extends AbstractRuptureIdentifier {
	
	private String name;
	private HashSet<Integer> elementIDs;
	
	public FaultIDIden(String name, List<SimulatorElement> elems, int faultID) {
		this(name, elems, Lists.newArrayList(faultID));
	}
	
	public FaultIDIden(String name, List<SimulatorElement> elems, int... faultIDs) {
		this(name, elems, Ints.asList(faultIDs));
	}
	
	public FaultIDIden(String name, List<SimulatorElement> elems, List<Integer> faultIDs) {
		this.name = name;
		elementIDs = new HashSet<Integer>(getElemIDs(elems, faultIDs));
	}
	
	private static List<Integer> getElemIDs(List<SimulatorElement> elems, Collection<Integer> faultIDs) {
		faultIDs = new HashSet<Integer>(faultIDs);
		List<Integer> elemIDs = Lists.newArrayList();
		for (SimulatorElement elem : elems) {
			if (faultIDs.contains(elem.getFaultID()))
				elemIDs.add(elem.getID());
		}
		return elemIDs;
	}
	
	@Override
	public boolean isMatch(SimulatorEvent event) {
		return isElementMatch(event);
	}
	
	private boolean isElementMatch(SimulatorEvent event) {
		for (int elementID : event.getAllElementIDs())
			if (elementIDs.contains(elementID))
				return true;
		return false;
	}

	@Override
	public String getName() {
		return name;
	}

}
