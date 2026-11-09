package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.io.IOException;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Passage probabilities for transitions traversed by a rupture, including boundaries
 * within a parent fault section. The default rupture probability is the product of
 * the transition probabilities, assuming independent passage through each feature.
 * Local transition-rate constraints do not, by themselves, enforce this path product.
 *
 * @param <T> transition type evaluated by this calculator
 */
public interface TransitionProbabilityCalc<T extends Transition> extends RuptureProbabilityCalc {

	/**
	 * Returns the transitions actually traversed by this rupture, including its splays.
	 * Each physical transition must be returned once, oriented along the rupture tree.
	 */
	Iterable<T> getTransitions(ClusterRupture rupture);

	/** Returns the conditional passage probability, in [0, 1]. */
	double calcTransitionProbability(ClusterRupture rupture, T transition, boolean verbose);

	@Override
	default double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
		double prob = 1d;
		for (T transition : getTransitions(rupture)) {
			double transitionProb = calcTransitionProbability(rupture, transition, verbose);
			if (verbose)
				System.out.println(getName()+": "+transition+", P="+transitionProb);
			prob *= transitionProb;
			if (prob == 0d && !verbose)
				break;
		}
		return prob;
	}

	/** Serializes the concrete calculator type, retaining generic transition implementations. */
	class GenericTransitionProbCalcAdapter extends TypeAdapter<TransitionProbabilityCalc<?>> {
		private final RuptureProbabilityCalc.GenericRupProbCalcAdapter delegate =
				new RuptureProbabilityCalc.GenericRupProbCalcAdapter();

		@Override
		public void write(JsonWriter out, TransitionProbabilityCalc<?> value) throws IOException {
			delegate.write(out, value);
		}

		@Override
		public TransitionProbabilityCalc<?> read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			RuptureProbabilityCalc calc = delegate.read(in);
			Preconditions.checkState(calc instanceof TransitionProbabilityCalc,
					"Not a transition probability calculator: %s", calc);
			return (TransitionProbabilityCalc<?>)calc;
		}
	}
}
