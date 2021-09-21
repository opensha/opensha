package scratch.UCERF3.enumTreeBranches;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum SlipAlongRuptureModels implements LogicTreeBranchNode<SlipAlongRuptureModels> {
	// DO NOT RENAME THESE - they are used in rupture set files
	
	CHAR(		"Characteristic",	"Char",	0d, null), // "Characteristic (Dsr=Ds)"
	UNIFORM(	"Uniform",			"Uni",	0.5d, new SlipAlongRuptureModel.Uniform()), // "Uniform/Boxcar (Dsr=Dr)"
	WG02(		"WGCEP-2002",		"WG02",	0d, new SlipAlongRuptureModel.WG02()), // "WGCEP-2002 model (Dsr prop to Vs)"
	TAPERED(	"Tapered Ends",		"Tap",	0.5d, new SlipAlongRuptureModel.Tapered()), // "Characteristic (Dsr=Ds)"
	MEAN_UCERF3("Mean UCERF3 Dsr",	"MeanU3Dsr", 0d, new SlipAlongRuptureModel.AVG_UCERF3()); // "Mean UCERF3"
	
	private final String name, shortName;
	private final double weight;
	private final SlipAlongRuptureModel model;
	
	private SlipAlongRuptureModels(String name, String shortName, double weight, SlipAlongRuptureModel model) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.model = model;
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
	
	@Override
	public String getShortBranchLevelName() {
		return "Dsr";
	}
	
	public SlipAlongRuptureModel getModel() {
		if (model == null)
			throw new IllegalStateException("Model not yet implemented: "+getName());
		return model;
	}

	public static double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
			SlipAlongRuptureModels slipModelType,
			double[] sectArea, double[] sectMoRate, double aveSlip) {
		return slipModelType.getModel().calcSlipOnSectionsForRup(rupSet, rthRup, sectArea, sectMoRate, aveSlip);
	}
}