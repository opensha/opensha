package scratch.UCERF3.logicTree;

public class UniformBranchWeightProvider implements BranchWeightProvider {

	@Override
	public double getWeight(LogicTreeBranch branch) {
		return 1;
	}

}
