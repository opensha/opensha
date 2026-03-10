package org.opensha.sha.earthquake.faultSysSolution.util;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

public interface MaxMagOffFaultBranchNode extends LogicTreeNode {
	
	public double getMaxMagOffFault();
	
	public TectonicRegionType getTectonicRegime();

}
