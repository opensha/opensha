package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.List;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.MergedSolutionCreator.MergedRupSetMappings;

public interface MergeableSolutionModule<E extends OpenSHA_Module> extends OpenSHA_Module {
	
	/**
	 * 
	 * 
	 * @param mergedSolution merged solution
	 * @param mappings mappings from the input rupture set section and rupture indexes to the merged section and rupture indexes
	 * @param originalModules list of original models with size equal to {@link MergedRupSetMappings#getNumInputRupSets()}
	 * and in the same order.
	 * @return merged module across the given original modules, or null if impossible
	 */
	public E getForMergedSolution(FaultSystemSolution mergedSolution, MergedRupSetMappings mappings, List<E> originalModules);

}
