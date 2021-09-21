package scratch.UCERF3.logicTree;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

public class DiscreteListTreeTrimmer implements TreeTrimmer,
		Iterable<U3LogicTreeBranch> {
	
	List<U3LogicTreeBranch> branches;
	
	public DiscreteListTreeTrimmer(List<U3LogicTreeBranch> branches) {
		this.branches = branches;
	}

	@Override
	public Iterator<U3LogicTreeBranch> iterator() {
		return branches.iterator();
	}

	@Override
	public boolean isTreeValid(U3LogicTreeBranch branch) {
		return branches.contains(branch);
	}

}
