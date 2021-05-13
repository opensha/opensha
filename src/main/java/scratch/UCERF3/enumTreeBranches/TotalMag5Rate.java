package scratch.UCERF3.enumTreeBranches;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum TotalMag5Rate implements LogicTreeBranchNode<TotalMag5Rate> {
	// new rates
	RATE_6p5(6.5,	0.1d),
	RATE_7p9(7.9,	0.6d),
	RATE_9p6(9.6,	0.3d),
	
	// old rates kept for compatibility (for now)
	// TODO: remove when UCERF3.2 or solutions no longer need plotting/loading
	@Deprecated
	RATE_7p6(7.6,	0d),
	@Deprecated
	RATE_8p7(8.7,	0d),
	@Deprecated
	RATE_10p0(10.0,	0d);
	
	private double rate;
	private double weight;
	
	private TotalMag5Rate(double rate, double weight) {
		this.rate = rate;
		this.weight = weight;
	}

	@Override
	public String getName() {
		String name = (float)rate+"";
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
		return weight;
	}
	
	public double getRateMag5() {
		return rate;
	}

	@Override
	public String encodeChoiceString() {
		return "M5Rate"+getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Total Mag 5 Rate";
	}

}
