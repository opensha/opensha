package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.util.BitSet;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;

/**
 * Interface for a factory for which ruptures are excluded from the inversion on a branch-specific basis
 * 
 * @author kevin
 *
 */
public interface ExclusionaryInversionConfigurationFactory extends InversionConfigurationFactory {
	
	public BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			ClusterRuptures cRups);
	
	public default BitSet getInncludedRups(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			ClusterRuptures cRups) {
		return getInncludedRups(rupSet, getExclusionModel(rupSet, branch, cRups), cRups);
	}
	
	public default BitSet getInncludedRups(FaultSystemRupSet rupSet, BinaryRuptureProbabilityCalc exclusionModel,
			ClusterRuptures cRups) {
		BitSet includedRups = exclusionModel == null ? null : new BitSet(rupSet.getNumRuptures());
		if (exclusionModel != null) {
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				if (exclusionModel.isRupAllowed(cRups.get(r), false))
					includedRups.set(r);
		}
		return includedRups;
	}

}
