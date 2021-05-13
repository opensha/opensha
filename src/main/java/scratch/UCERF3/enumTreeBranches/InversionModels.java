package scratch.UCERF3.enumTreeBranches;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum InversionModels implements LogicTreeBranchNode<InversionModels> {
	
	// TODO set weights
	CHAR_CONSTRAINED(	"Characteristic (Constrained)",			"CharConst",	1d),
	GR_CONSTRAINED(		"Gutenberg-Richter (Constrained)",		"GRConst",		1d),
	CHAR_UNCONSTRAINED(		"Unconstrained (Unconstrained)",	"CharUnconst",	1d),
	GR_UNCONSTRAINED(	"Unconstrained (Unconstrained)",		"GRUnconst",	1d);
	
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
}
