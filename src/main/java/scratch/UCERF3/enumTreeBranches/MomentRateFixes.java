package scratch.UCERF3.enumTreeBranches;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum MomentRateFixes implements LogicTreeBranchNode<MomentRateFixes> {
	// TODO set weights for GR
	APPLY_IMPLIED_CC(		"Apply Implied Coupling Coefficient",		"ApplyCC",			0.0d,	0.5d),
	RELAX_MFD(				"Relaxed (weak) MFD Constraint Weights",	"RelaxMFD",			0.0d,	0.0d),
	APPLY_CC_AND_RELAX_MFD(	"Apply Implied CC and Relax MFD",			"ApplyCCRelaxMFD",	0.0d,	0.0d),
	NONE(					"No Moment Rate Fixes",						"NoFix",			1.0d,	0.5d);
	
	private String name, shortName;
	private double charWeight, grWeight;

	private MomentRateFixes(String name, String shortName, double charWeight, double grWeight) {
		this.name = name;
		this.shortName = shortName;
		this.charWeight = charWeight;
		this.grWeight = grWeight;
	}
	
	public boolean isRelaxMFD() {
		return this == RELAX_MFD || this == APPLY_CC_AND_RELAX_MFD;
	}
	
	public boolean isApplyCC() {
		return this == APPLY_IMPLIED_CC || this == APPLY_CC_AND_RELAX_MFD;
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
	public double getRelativeWeight(InversionModels im) {
		if (im.isCharacteristic())
			return charWeight;
		else
			return grWeight;
	}

	@Override
	public String encodeChoiceString() {
		return getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Moment Rate Fixes";
	}

}
