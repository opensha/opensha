package org.opensha.sha.earthquake.faultSysSolution.inversion;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;

/**
 * Interface for a factory that can build {@link FaultSystemRupSet} and {@link InversionConfiguration}'s for a given
 * {@link LogicTreeBranch}.
 * 
 * @author kevin
 *
 */
public interface InversionConfigurationFactory {
	
	/**
	 * Builds a {@link FaultSystemRupSet} for the given {@link LogicTreeBranch}
	 * 
	 * @param branch
	 * @param threads number of threads to use when building
	 * @return rupture set for this branch
	 */
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads);
	
	/**
	 * Configures an inversion for the given rupture set and logic tree branch
	 * 
	 * @param rupSet
	 * @param branch
	 * @param threads number of threads to use
	 * @return
	 */
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads);
	
	/**
	 * This can be used to supply a custom {@link SolutionProcessor} instance that will be used when building
	 * and loading solutions from a {@link SolutionLogicTree}. Default implementation returns null.
	 * 
	 * @return
	 */
	public default SolutionProcessor getSolutionLogicTreeProcessor() {
		return null;
	};

}
