package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.apache.commons.text.WordUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum RupsThroughCreepingSect implements LogicTreeNode {
	
	INCLUDE(true, 0.5d),
	EXCLUDE(false, 0.5d);
	
	private boolean include;
	private double weight;

	private RupsThroughCreepingSect(boolean include, double weight) {
		this.include = include;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return WordUtils.capitalizeFully(name());
	}

	@Override
	public String getName() {
		return getShortName();
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName()+"ThruCreep";
	}
	
	public boolean isInclude() {
		return include;
	}
	
	public boolean isExclude() {
		return !include;
	}

}
