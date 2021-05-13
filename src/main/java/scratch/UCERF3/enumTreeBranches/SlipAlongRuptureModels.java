package scratch.UCERF3.enumTreeBranches;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum SlipAlongRuptureModels implements LogicTreeBranchNode<SlipAlongRuptureModels> {
	// DO NOT RENAME THESE - they are used in rupture set files
	
	CHAR(		"Characteristic",	"Char",	0d),	// "Characteristic (Dsr=Ds)"
	UNIFORM(	"Uniform",			"Uni",	0.5d),	// "Uniform/Boxcar (Dsr=Dr)"
	WG02(		"WGCEP-2002",		"WG02",	0d),	// "WGCEP-2002 model (Dsr prop to Vs)"
	TAPERED(	"Tapered Ends",		"Tap",	0.5d),
	MEAN_UCERF3("Mean UCERF3 Dsr",	"MeanU3Dsr", 0d);	// "Mean UCERF3"
	
	private String name, shortName;
	private double weight;
	
	private SlipAlongRuptureModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		return weight;
	}

	@Override
	public String encodeChoiceString() {
		return "Dsr"+getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Slip Along Rupture Model (Dsr)";
	}
}