package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.LogicTreeNode;

public interface RupsThroughCreepingSectBranchNode extends LogicTreeNode {
	
	public default boolean isIncludeRupturesThroughCreepingSect() {
		return !isExcludeRupturesThroughCreepingSect();
	}
	
	public boolean isExcludeRupturesThroughCreepingSect();

}
