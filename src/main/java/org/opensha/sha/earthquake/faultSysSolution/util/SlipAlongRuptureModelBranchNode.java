package org.opensha.sha.earthquake.faultSysSolution.util;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

public interface SlipAlongRuptureModelBranchNode extends LogicTreeNode {
	
	public abstract SlipAlongRuptureModel getModel();

}
