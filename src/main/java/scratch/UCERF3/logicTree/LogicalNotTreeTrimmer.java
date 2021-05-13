package scratch.UCERF3.logicTree;

/**
 * This returns the opposite value of the given tree trimmer
 * @author kevin
 *
 */
public class LogicalNotTreeTrimmer implements TreeTrimmer {
	
	private TreeTrimmer trimmer;
	
	public LogicalNotTreeTrimmer(TreeTrimmer trimmer) {
		this.trimmer = trimmer;
	}

	@Override
	public boolean isTreeValid(LogicTreeBranch branch) {
		return !trimmer.isTreeValid(branch);
	}

}
