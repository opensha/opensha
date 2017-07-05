package scratch.UCERF3.logicTree;

import java.io.Serializable;

/**
 * Interface for providing branch weights for logic tree branches. Can be implemented for custom
 * branch weighting schemes.
 * 
 * @author kevin
 *
 */
public interface BranchWeightProvider extends Serializable {
	
	/**
	 * Returns the weight for the given branch. Will not necessarily be normalized.
	 * @param branch
	 * @return
	 */
	public double getWeight(LogicTreeBranch branch);

}
