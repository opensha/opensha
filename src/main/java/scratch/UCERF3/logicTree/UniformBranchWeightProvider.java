package scratch.UCERF3.logicTree;

public class UniformBranchWeightProvider implements BranchWeightProvider {

	@Override
	public double getWeight(U3LogicTreeBranch branch) {
		return 1;
	}

}
