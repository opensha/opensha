package scratch.UCERF3.logicTree;

import java.util.List;

abstract class LogicalAndOrTrimmer implements TreeTrimmer {
	
	private boolean and;
	private TreeTrimmer[] trimmers;
	
	LogicalAndOrTrimmer(boolean and, List<TreeTrimmer> trimmers) {
		this(and, trimmers.toArray(new TreeTrimmer[0]));
	}
	
	LogicalAndOrTrimmer(boolean and, TreeTrimmer... trimmers) {
		this.and = and;
		this.trimmers = trimmers;
	}

	@Override
	public boolean isTreeValid(LogicTreeBranch branch) {
		if (and) {
			for (TreeTrimmer trimmer : trimmers)
				if (!trimmer.isTreeValid(branch))
					return false;
			return true;
		} else {
			// OR
			for (TreeTrimmer trimmer : trimmers)
				if (trimmer.isTreeValid(branch))
					return true;
			return false;
		}
	}

}
