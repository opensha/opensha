package scratch.UCERF3.logicTree;

public class SingleValsTreeTrimmer implements TreeTrimmer {
	
	private U3LogicTreeBranchNode<?>[] values;
	
	public SingleValsTreeTrimmer(U3LogicTreeBranchNode<?>... values) {
		this.values = values;
	}

	@Override
	public boolean isTreeValid(U3LogicTreeBranch branch) {
		for (U3LogicTreeBranchNode<?> val : values) {
			U3LogicTreeBranchNode<?> oVal = branch.getValueUnchecked((Class<? extends U3LogicTreeBranchNode<?>>) val.getClass());
			if (oVal == null || !val.equals(oVal))
				return false;
		}
		return true;
	}

}
