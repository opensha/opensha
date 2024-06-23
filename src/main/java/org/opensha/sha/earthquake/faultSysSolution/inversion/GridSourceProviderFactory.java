package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.IOException;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;

/**
 * Factory for building gridded seismicity branches where there is a logic tree of gridded seismicity branches for each fault-based
 * inversion branch.
 */
public interface GridSourceProviderFactory {

	/**
	 * Builds a grid source provider for the given solution and full logic tree branch. The supplied branch will contain
	 * both fault and gridded seismicity branch levels.
	 * 
	 * @param sol
	 * @param fullBranch
	 * @return
	 * @throws IOException
	 */
	public GridSourceProvider buildGridSourceProvider(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException;
	
	/**
	 * Logic tree of grid source-specific branch levels that should be combined with the fault-based logic tree.
	 * 
	 * @param faultTree fault-based logic tree
	 * @return
	 */
	public LogicTree<?> getGridSourceTree(LogicTree<?> faultTree);
	
	/**
	 * Will be called once for every fault branch before building grid source providers; can be useful if there are any
	 * modules that need to be attached to the solution that can be done once for each fault branch instead of once for each
	 * fault+grid branch combination, e.g., fault grid associations.
	 * 
	 * @param sol
	 * @param faultBranch
	 */
	public default void preGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		// do nothing
	}
}
