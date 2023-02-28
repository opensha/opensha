package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.HashSet;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.HardcodedBinaryJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;

public interface SegmentationModelBranchNode extends LogicTreeNode {
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);
	
	public default BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		JumpProbabilityCalc segModel = getModel(rupSet, branch);
		if (segModel instanceof BinaryJumpProbabilityCalc) {
			return (BinaryJumpProbabilityCalc)segModel;
		} else if (segModel != null && rupSet.hasModule(ClusterRuptures.class)) {
			ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
			// see if it has any zeroes, and if so exclude ruptures that use them from being sampled in the inversion
			HashSet<IDPairing> excluded = new HashSet<>();
			for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
				ClusterRupture rup = cRups.get(rupIndex);
				for (Jump jump : rup.getJumpsIterable()) {
					if (segModel.calcJumpProbability(rup, jump, false) == 0d) {
						IDPairing pair = new IDPairing(jump.fromSection.getSectionId(), jump.toSection.getSectionId());
						excluded.add(pair);
						excluded.add(pair.getReversed());
					}
				}
			}
			if (!excluded.isEmpty())
				return new HardcodedBinaryJumpProb(segModel.getName(), true, excluded, false);
		}
		return null;
	}

}
