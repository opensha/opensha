package scratch.UCERF3.enumTreeBranches;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum SlipAlongRuptureModels implements LogicTreeBranchNode<SlipAlongRuptureModels> {
	// DO NOT RENAME THESE - they are used in rupture set files
	
	CHAR(		"Characteristic",	"Char",	0d) { // "Characteristic (Dsr=Ds)"

		@Override
		public SlipAlongRuptureModel getModel(FaultSystemRupSet rupSet) {
			throw new RuntimeException("SlipModelType.CHAR_SLIP_MODEL not yet supported");
		}
		
	},
	UNIFORM(	"Uniform",			"Uni",	0.5d) { // "Uniform/Boxcar (Dsr=Dr)"

		@Override
		public SlipAlongRuptureModel.Uniform getModel(FaultSystemRupSet rupSet) {
			return new SlipAlongRuptureModel.Uniform(rupSet);
		}
		
	},
	WG02(		"WGCEP-2002",		"WG02",	0d) { // "WGCEP-2002 model (Dsr prop to Vs)"

		@Override
		public SlipAlongRuptureModel.WG02 getModel(FaultSystemRupSet rupSet) {
			return new SlipAlongRuptureModel.WG02(rupSet);
		}
		
	},
	TAPERED(	"Tapered Ends",		"Tap",	0.5d) { // "Characteristic (Dsr=Ds)"

		@Override
		public SlipAlongRuptureModel.Tapered getModel(FaultSystemRupSet rupSet) {
			return new SlipAlongRuptureModel.Tapered(rupSet);
		}
		
	},
	MEAN_UCERF3("Mean UCERF3 Dsr",	"MeanU3Dsr", 0d) { // "Mean UCERF3"

		@Override
		public SlipAlongRuptureModel.AVG_UCERF3 getModel(FaultSystemRupSet rupSet) {
			return new SlipAlongRuptureModel.AVG_UCERF3(rupSet);
		}
		
	};
	
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
	
	@Override
	public String getShortBranchLevelName() {
		return "Dsr";
	}
	
	public abstract SlipAlongRuptureModel getModel(FaultSystemRupSet rupSet);
	
	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;

	public static double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
			SlipAlongRuptureModels slipModelType,
			double[] sectArea, double[] sectMoRate, double aveSlip) {
		return slipModelType.getModel(rupSet).calcSlipOnSectionsForRup(rthRup, sectArea, sectMoRate, aveSlip);
	}
}