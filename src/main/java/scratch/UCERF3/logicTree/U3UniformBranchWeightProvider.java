package scratch.UCERF3.logicTree;

import org.opensha.commons.logicTree.LogicTreeBranch;

import com.google.common.base.Preconditions;

public class U3UniformBranchWeightProvider implements U3BranchWeightProvider {

	@Override
	public double getWeight(U3LogicTreeBranch branch) {
		return 1;
	}
	
	@Override
	public double getWeight(LogicTreeBranch<?> branch) {
		Preconditions.checkState(branch instanceof U3LogicTreeBranch);
		return getWeight((U3LogicTreeBranch)branch);
	}

}
