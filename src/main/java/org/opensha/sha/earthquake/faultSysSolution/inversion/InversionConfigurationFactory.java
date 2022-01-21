package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.File;
import java.io.IOException;

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
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException;
	
	/**
	 * Updates a {@link FaultSystemRupSet} for the given {@link LogicTreeBranch}, which may involve swapping out
	 * modules and/or updating fault section data/slip rates.
	 * <p>
	 * The passed in rupture set should not be modified, and this method should not assume that anything beyond the
	 * rupture list is correct for the given branch (just that it is generally  compatible). If you simply want to
	 * attach modules related to a given branch without rebuilding it, see {@link #getSolutionLogicTreeProcessor()}.
	 * 
	 * @param branch
	 * @return
	 * @throws IOException
	 */
	public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) throws IOException;
	
	/**
	 * Configures an inversion for the given rupture set and logic tree branch
	 * 
	 * @param rupSet
	 * @param branch
	 * @param threads number of threads to use
	 * @return
	 */
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) throws IOException;
	
	/**
	 * This can be used to supply a custom {@link SolutionProcessor} instance that will be used when building
	 * and loading solutions from a {@link SolutionLogicTree}. Default implementation returns null.
	 * 
	 * Unlike {@link #updateRuptureSetForBranch(FaultSystemRupSet, LogicTreeBranch)}, this processor should just attach
	 * extra modules related to a branch, not swapping out any already existing data
	 * 
	 * @return
	 */
	public default SolutionProcessor getSolutionLogicTreeProcessor() {
		return null;
	};
	
	/**
	 * Sets the cache directory, useful for storing cache files to accelerate rupture building
	 * 
	 * @param cacheDir
	 */
	public default void setCacheDir(File cacheDir) {};
	
	/**
	 * @param autoCache if true, cache files will automatically be written out if caches change after rupture set
	 * building
	 */
	public default void setAutoCache(boolean autoCache) {};
	
	/**
	 * Writes any relevant cache data for later reuse, default implementation does nothing.
	 */
	public default void writeCache() {};

}
