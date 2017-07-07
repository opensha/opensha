package org.opensha.sha.simulators.iden;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

/**
 * This is the simplest rupture identifier implementation - it defines a match as any rupture that includes
 * the given section and is within the specified magnitude range.
 * @author kevin
 *
 */
public class ElementIden extends AbstractRuptureIdentifier {
	
	private String name;
	private List<Integer> elementIDs;
	
	public ElementIden(String name, int elementID) {
		this(name, Lists.newArrayList(elementID));
	}
	
	public ElementIden(String name, int... elementIDs) {
		this(name, Ints.asList(elementIDs));
	}
	
	public ElementIden(String name, List<Integer> elementIDs) {
		this.elementIDs = elementIDs;
		this.name = name;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		for (int elementID : elementIDs)
			if (!Ints.contains(event.getAllElementIDs(), elementID))
				return false;
		return true;
	}
	
	public List<Integer> getElementIDs() {
		return elementIDs;
	}

	public void setElementID(int elementID) {
		this.elementIDs = Lists.newArrayList(elementID);
	}

	public void addElementID(int elementID) {
		this.elementIDs.add(elementID);
	}
	
	public int removeElementID(int elementID) {
		int ind = elementIDs.indexOf(elementID);
		if (ind < 0)
			return -1;
		this.elementIDs.remove(ind);
		return ind;
	}

	public void setElementID(List<Integer> elementIDs) {
		this.elementIDs = elementIDs;
	}

	@Override
	public String getName() {
		return name;
	}

}
