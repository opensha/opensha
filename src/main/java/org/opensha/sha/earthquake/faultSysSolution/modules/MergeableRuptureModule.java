package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.List;

import org.opensha.commons.util.modules.ModuleHelper;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.util.MergedSolutionCreator.MergedRupSetMappings;

@ModuleHelper
public interface MergeableRuptureModule<E extends OpenSHA_Module> extends OpenSHA_Module {
	
	/**
	 * 
	 * 
	 * @param mergedRupSet merged rupture set
	 * @param mappings mappings from the input rupture set section and rupture indexes to the merged section and rupture indexes
	 * @param originalModules list of original models with size equal to {@link MergedRupSetMappings#getNumInputRupSets()}
	 * and in the same order.
	 * @return merged module across the given original modules, or null if impossible
	 */
	public E getForMergedRuptureSet(FaultSystemRupSet mergedRupSet, MergedRupSetMappings mappings, List<E> originalModules);
	
	/**
	 * Helper for a module that is a constant value, where any instance can be copied over in a merging operation
	 * 
	 * @author kevin
	 *
	 * @param <E>
	 */
	@ModuleHelper
	public interface ConstantMergeable<E extends ConstantMergeable<E>> extends MergeableRuptureModule<E> {
		
		/**
		 * Used as a check when averaging to ensure that this really is constant
		 * 
		 * @param module
		 * @return true if identical, false otherwise
		 */
		public boolean isIdentical(E module);

		@Override
		default E getForMergedRuptureSet(FaultSystemRupSet mergedRupSet, MergedRupSetMappings mappings,
				List<E> originalModules) {
			E merged = null;
			for (E orig : originalModules) {
				if (orig == null)
					return null;
				if (merged == null)
					merged = orig;
				else if (!merged.isIdentical(orig))
					return null;
			}
			return merged;
		}
	}

}
