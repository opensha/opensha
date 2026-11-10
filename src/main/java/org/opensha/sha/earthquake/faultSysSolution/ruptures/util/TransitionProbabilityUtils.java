package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;

import com.google.common.base.Preconditions;

/** Shared endpoint grouping for transition-based MFD adjustments and exclusions. */
public class TransitionProbabilityUtils {

	public static IDPairing pair(Transition transition) {
		int from = transition.getFromSection().getSectionId();
		int to = transition.getToSection().getSectionId();
		return new IDPairing(Math.min(from, to), Math.max(from, to));
	}

	/** Worst probability among the transitions actually traversed by a rupture. */
	public static <T extends Transition> double worstProbability(
			ClusterRupture rupture, TransitionProbabilityCalc<T> model) {
		double worst = 1d;
		for (T transition : model.getTransitions(rupture))
			worst = Math.min(worst, model.calcTransitionProbability(rupture, transition, false));
		return worst;
	}

	/** Arithmetic mean across crossing ruptures, counting each unordered pair once per rupture. */
	public static <T extends Transition> Map<IDPairing, Double> averageProbabilities(
			Iterable<ClusterRupture> ruptures, TransitionProbabilityCalc<T> model) {
		Map<IDPairing, Double> sums = new TreeMap<>();
		Map<IDPairing, Integer> counts = new HashMap<>();
		for (ClusterRupture rupture : ruptures) {
			Preconditions.checkArgument(!model.isDirectional(!rupture.splays.isEmpty()),
					"Transition averaging requires non-directional probabilities");
			Map<IDPairing, Double> probabilities = new HashMap<>();
			for (T transition : model.getTransitions(rupture)) {
				double p = model.calcTransitionProbability(rupture, transition, false);
				Preconditions.checkArgument(Double.isFinite(p) && p >= 0d && p <= 1d, "Invalid probability: %s", p);
				IDPairing pair = pair(transition);
				Double previous = probabilities.putIfAbsent(pair, p);
				Preconditions.checkArgument(previous == null || Math.abs(previous-p) <= 1e-12,
						"Conflicting duplicate probabilities for %s", pair);
			}
			for (Map.Entry<IDPairing, Double> entry : probabilities.entrySet()) {
				sums.merge(entry.getKey(), entry.getValue(), Double::sum);
				counts.merge(entry.getKey(), 1, Integer::sum);
			}
		}
		sums.replaceAll((pair, sum) -> sum/counts.get(pair));
		return sums;
	}
}
