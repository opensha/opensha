package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;

public interface ExclusionaryLogicTreeNode extends LogicTreeNode {
	
	public BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);

}
