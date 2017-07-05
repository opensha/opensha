package scratch.UCERF3.logicTree;

public class APrioriBranchWeightProvider implements BranchWeightProvider {

	@Override
	public double getWeight(LogicTreeBranch branch) {
		return branch.getAprioriBranchWt();
	}

}
