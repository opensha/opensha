package scratch.UCERF3.logicTree;

import java.io.Serializable;

import org.opensha.commons.logicTree.BranchWeightProvider;

/**
 * Interface for providing branch weights for logic tree branches. Can be implemented for custom
 * branch weighting schemes.
 * 
 * @author kevin
 *
 */
public interface U3BranchWeightProvider extends Serializable, BranchWeightProvider {
	
	/**
	 * Returns the weight for the given branch. Will not necessarily be normalized.
	 * @param branch
	 * @return
	 */
	public double getWeight(U3LogicTreeBranch branch);

}
