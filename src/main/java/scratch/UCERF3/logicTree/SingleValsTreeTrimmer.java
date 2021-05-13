package scratch.UCERF3.logicTree;

public class SingleValsTreeTrimmer implements TreeTrimmer {
	
	private LogicTreeBranchNode<?>[] values;
	
	public SingleValsTreeTrimmer(LogicTreeBranchNode<?>... values) {
		this.values = values;
	}

	@Override
	public boolean isTreeValid(LogicTreeBranch branch) {
		for (LogicTreeBranchNode<?> val : values) {
			LogicTreeBranchNode<?> oVal = branch.getValueUnchecked((Class<? extends LogicTreeBranchNode<?>>) val.getClass());
			if (oVal == null || !val.equals(oVal))
				return false;
		}
		return true;
	}

}
