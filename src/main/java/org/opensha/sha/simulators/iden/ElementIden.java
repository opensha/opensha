package org.opensha.sha.simulators.iden;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

/**
 * This is the simplest rupture identifier implementation - it defines a match as any rupture that includes
 * the given section .
 * @author kevin
 *
 */
public class ElementIden extends AbstractRuptureIdentifier {
	
	private String name;
	private List<Integer> elementIDs;
	private BitSet elemSet;
	
	public ElementIden(String name, int elementID) {
		this(name, Lists.newArrayList(elementID));
	}
	
	public ElementIden(String name, int... elementIDs) {
		this(name, Ints.asList(elementIDs));
	}
	
	public ElementIden(String name, List<Integer> elementIDs) {
		setElementID(elementIDs);
		this.name = name;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		for (EventRecord rec : event)
			for (int elementID : rec.getElementIDs())
				if (elemSet.get(elementID))
					return true;
		return false;
	}
	
	public List<Integer> getElementIDs() {
		return elementIDs;
	}

	public void setElementID(int elementID) {
		this.elementIDs = Lists.newArrayList(elementID);
		elemSet.clear();
		elemSet.set(elementID);
	}

	public void addElementID(int elementID) {
		this.elementIDs.add(elementID);
		elemSet.set(elementID);
	}
	
	public boolean removeElementID(int elementID) {
		if (elemSet.get(elementID)) {
			elemSet.clear(elementID);
			int ind = elementIDs.indexOf(elementID);
			this.elementIDs.remove(ind);
			return true;
		}
		return false;
	}

	public void setElementID(List<Integer> elementIDs) {
		this.elementIDs = elementIDs;
		int maxID = 0;
		for (Integer id : elementIDs)
			maxID = Integer.max(id, maxID);
		this.elemSet = new BitSet(maxID+1);
		for (Integer id : elementIDs)
			this.elemSet.set(id);
		this.elementIDs = elementIDs;
	}

	@Override
	public String getName() {
		return name;
	}

}
