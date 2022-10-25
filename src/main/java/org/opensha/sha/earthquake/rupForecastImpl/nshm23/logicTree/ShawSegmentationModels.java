package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum ShawSegmentationModels implements SegmentationModelBranchNode {
	SHAW_R0_1("Shaw & Dieterich (2007) R₀=1", "ShawR₀=1", 1d, 0.0d),
	SHAW_R0_2("Shaw & Dieterich (2007) R₀=2", "ShawR₀=2", 2d, 0.25d),
	SHAW_R0_3("Shaw & Dieterich (2007) R₀=3", "ShawR₀=3", 3d, 0.6d),
	SHAW_R0_4("Shaw & Dieterich (2007) R₀=4", "ShawR₀=4", 4d, 0.15d),
	SHAW_R0_5("Shaw & Dieterich (2007) R₀=5", "ShawR₀=5", 5d, 0.0d),
	SHAW_R0_6("Shaw & Dieterich (2007) R₀=6", "ShawR₀=6", 6d, 0.0d),
	NONE("None", "None", Double.NaN, 0.0d);
	
	private String name;
	private String shortName;
	private double weight;
	private double r0;

	private ShawSegmentationModels(String name, String shortName, double r0, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.r0 = r0;
		this.weight = weight;
	}
	
	public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		return buildShaw(1d, r0, branch);
	}
	
	private static Shaw07JumpDistProb buildShaw(double a, double r0, LogicTreeBranch<?> branch) {
		DistDependSegShift shift = branch == null ? null : branch.getValue(DistDependSegShift.class);
		if (shift != null)
			return Shaw07JumpDistProb.forHorzOffset(a, r0, shift.getShiftKM());
		return new Shaw07JumpDistProb(a, r0);
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return shortName.replace("R₀=", "R0_");
	}
	
	public double getR0() {
		return r0;
	}

}
