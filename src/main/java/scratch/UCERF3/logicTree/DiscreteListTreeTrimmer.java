package scratch.UCERF3.logicTree;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

public class DiscreteListTreeTrimmer implements TreeTrimmer,
		Iterable<LogicTreeBranch> {
	
	List<LogicTreeBranch> branches;
	
	public DiscreteListTreeTrimmer(List<LogicTreeBranch> branches) {
		this.branches = branches;
	}

	@Override
	public Iterator<LogicTreeBranch> iterator() {
		return branches.iterator();
	}

	@Override
	public boolean isTreeValid(LogicTreeBranch branch) {
		return branches.contains(branch);
	}

}
