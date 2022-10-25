package scratch.UCERF3.enumTreeBranches;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum InversionModels implements U3LogicTreeBranchNode<InversionModels> {
	
	CHAR_CONSTRAINED(	"Characteristic (Constrained)",			"CharConst",	1d),
	GR_CONSTRAINED(		"Gutenberg-Richter (Constrained)",		"GRConst",		0d),
	CHAR_UNCONSTRAINED(		"Unconstrained (Unconstrained)",	"CharUnconst",	0d),
	GR_UNCONSTRAINED(	"Unconstrained (Unconstrained)",		"GRUnconst",	0d);
	
	private String name, shortName;
	private double weight;
	
	private InversionModels(String name, String shortName, double weight) {
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
	
	public static InversionModels getTypeForName(String name) {
		if (name == null) throw new NullPointerException();
		for (InversionModels inv:InversionModels.values()) {
			if (inv.name.equals(name) || inv.name().equals(name) || inv.shortName.equals(name)) return inv;
		}
		throw new IllegalArgumentException("InversionModels name does not exist");
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		return weight;
	}

	@Override
	public String encodeChoiceString() {
		return getShortName();
	}
	
	/**
	 * 
	 * @return true if this is a characteristic branch (either constrained or unconstrained)
	 */
	public boolean isCharacteristic() {
		return this == CHAR_CONSTRAINED || this == CHAR_UNCONSTRAINED;
	}
	
	/**
	 * 
	 * @return true if this is a GR branch (either constrained or unconstrained)
	 */
	public boolean isGR() {
		return !isCharacteristic();
	}
	
	/**
	 * @return true if this is a constrained branch (either characteristic or GR)
	 */
	public boolean isConstrained() {
		return this == CHAR_CONSTRAINED || this == GR_CONSTRAINED;
	}
	
	@Override
	public String getBranchLevelName() {
		return "Inversion Model";
	}
	
	@Override
	public String getShortBranchLevelName() {
		return "IM";
	}
}
