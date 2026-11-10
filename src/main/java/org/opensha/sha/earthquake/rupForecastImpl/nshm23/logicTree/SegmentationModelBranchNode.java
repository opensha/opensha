package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.TransitionProbabilityUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc.GenericTransitionProbCalcAdapter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;

import com.google.gson.annotations.JsonAdapter;

/** Segmentation branch node for transitions of type T, including internal fault-section boundaries. */
public interface SegmentationModelBranchNode<T extends Transition> extends ExclusionaryLogicTreeNode {
	
	public TransitionProbabilityCalc<T> getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);

	/** Excludes pairs whose mean probability across crossing ruptures is zero. */
	public default BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		TransitionProbabilityCalc<T> segModel = getModel(rupSet, branch);
		if (segModel == null) return null;
		Set<IDPairing> excluded = new HashSet<>();
		// A zero in only one rupture context does not imply a zero averaged bound.
		for (Map.Entry<IDPairing, Double> entry : TransitionProbabilityUtils.averageProbabilities(
				rupSet.requireModule(ClusterRuptures.class), segModel).entrySet())
			if (entry.getValue() == 0d) excluded.add(entry.getKey());
		return excluded.isEmpty() ? null : new TransitionExclusionModel(segModel, excluded);
	}

	/** Excludes rupture paths traversing a pair with zero mean passage probability. */
	public static class TransitionExclusionModel implements BinaryRuptureProbabilityCalc {
		@JsonAdapter(GenericTransitionProbCalcAdapter.class)
		private final TransitionProbabilityCalc<?> model;
		private final Set<IDPairing> excluded;

		public TransitionExclusionModel(TransitionProbabilityCalc<?> model, Set<IDPairing> excluded) {
			this.model = model;
			this.excluded = new HashSet<>(excluded);
		}

		@Override public String getName() { return model.getName()+" zero-probability transitions"; }
		@Override public boolean isDirectional(boolean splayed) { return false; }
		@Override public boolean isRupAllowed(ClusterRupture rupture, boolean verbose) {
			for (Transition transition : model.getTransitions(rupture))
				if (excluded.contains(TransitionProbabilityUtils.pair(transition))) return false;
			return true;
		}
	}

}
