package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_SlipAlongRuptureModels implements SlipAlongRuptureModelBranchNode {
	UNIFORM(	"Uniform",			"Uni",	1d, new SlipAlongRuptureModel.Uniform()), // "Uniform/Boxcar (Dsr=Dr)"
	TAPERED(	"Tapered Ends",		"Tap",	0d, new SlipAlongRuptureModel.Tapered()); // "Characteristic (Dsr=Ds)"
	
	private String name;
	private String shortName;
	private double weight;
	private SlipAlongRuptureModel model;
	
	private NSHM23_SlipAlongRuptureModels(String name, String shortName, double weight, SlipAlongRuptureModel model) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.model = model;
		
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return "Dsr"+getShortName();
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
	public SlipAlongRuptureModel getModel() {
		return model;
	}

}
