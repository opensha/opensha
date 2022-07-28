package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;

public interface SegmentationModelBranchNode extends LogicTreeNode {
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);

}
