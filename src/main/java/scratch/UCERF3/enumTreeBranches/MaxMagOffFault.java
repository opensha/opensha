package scratch.UCERF3.enumTreeBranches;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;

import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum MaxMagOffFault implements U3LogicTreeBranchNode<MaxMagOffFault>, MaxMagOffFaultBranchNode {
	
	MAG_7p3(7.3, 0.1d, 0.1d),
	MAG_7p6(7.6, 0.8d, 0.8d),
	MAG_7p9(7.9, 0.1d, 0.1d),
	
	
	// old mags kept for compatibility (for now)
	// TODO: remove when UCERF3.2 or solutions no longer need plotting/loading
	@Deprecated
	MAG_7p2(7.2, 0.0d, 0.0d),
	@Deprecated
	MAG_8p0(8.0, 0.0d, 0.0d);

	private double mmax;
	private double charWeight, grWeight;

	private MaxMagOffFault(double mmax, double charWeight, double grWeight) {
		this.mmax = mmax;
		this.charWeight = charWeight;
		this.grWeight = grWeight;
	}

	@Override
	public String getName() {
		String name = (float)mmax+"";
		if (!name.contains("."))
			name += ".0";
		return name;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		if (im == null || im.isCharacteristic())
			return charWeight;
		else
			return grWeight;
	}

	public double getMaxMagOffFault() {
		return mmax;
	}

	@Override
	public String encodeChoiceString() {
		return "MMaxOff"+getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "MMax Off Fault";
	}
	
	@Override
	public String getShortBranchLevelName() {
		return "MMaxOFf";
	}
}
