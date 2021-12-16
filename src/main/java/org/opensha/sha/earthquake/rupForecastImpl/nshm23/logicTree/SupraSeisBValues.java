package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

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
public enum SupraSeisBValues implements LogicTreeNode {
	
	B_0p0(0d, 0.05),
	B_0p3(0.3, 0.1),
	B_0p5(0.5, 0.15),
	B_0p7(0.7, 0.2),
	B_0p8(0.8, 0.25),
	B_0p9(0.9, 0.15),
	B_1p0(1d, 0.1);
	
	public final double bValue;
	public final double weight;

	private SupraSeisBValues(double bValue, double weight) {
		this.bValue = bValue;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public String getName() {
		return "b="+(float)bValue;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return "SupraB"+(float)bValue;
	}
	
	public static void main(String[] args) {
		double totWeight = 0d;
		for (SupraSeisBValues b : values())
			totWeight += b.weight;
		System.out.println("tot weight: "+(float)totWeight);
	}

}
