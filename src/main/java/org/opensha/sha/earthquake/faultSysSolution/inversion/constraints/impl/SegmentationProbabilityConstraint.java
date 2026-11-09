package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.SectParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc.GenericTransitionProbCalcAdapter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Relative-rate segmentation inequalities for between-parent jumps or within-parent
 * transitions. For each side of each transition, constrains the total crossing rate
 * J <= p * sum_s(w_s R_s), where R_s is subsection participation rate and the
 * reference weights sum to one. This does not enforce products along rupture paths.
 *
 * <p>This initial implementation requires non-directional probabilities that are
 * constant across all occurrences of an unordered subsection pair. Multiple features
 * at the same boundary should be combined by the calculator into one probability.
 * Rupture-dependent probabilities (including differing jump distances for the same
 * endpoints) are rejected rather than silently averaged. Unlike the legacy
 * {@link JumpProbabilityConstraint}, there is no equality/alternative-normalization
 * or proxy-slip variant.
 *
 * <p>Reference weights and transition membership are rebuilt on
 * {@link #setRuptureSet(FaultSystemRupSet)}. Calculators and weighting policies must
 * use the supplied rupture/rupture set's current section IDs; policies should not
 * retain IDs from an earlier, unremapped rupture set.
 */
public class SegmentationProbabilityConstraint extends InversionConstraint {

	private static final double MAX_WEIGHT_SCALAR = 1e5;
	private static final double PROB_TOLERANCE = 1e-12;

	private transient FaultSystemRupSet rupSet;
	private transient List<Row> rows;

	@JsonAdapter(GenericTransitionProbCalcAdapter.class)
	private TransitionProbabilityCalc<?> probabilityCalc;
	@JsonAdapter(ReferenceRateWeightingAdapter.class)
	private ReferenceRateWeighting referenceRateWeighting;
	// Snapshot used only for misfit normalization, not as the reference rate in the inequality.
	private double[] estimatedParticipationRates;

	public SegmentationProbabilityConstraint(double weight, FaultSystemRupSet rupSet,
			TransitionProbabilityCalc<?> probabilityCalc) {
		this(weight, rupSet, probabilityCalc, new EndpointReferenceRateWeighting(), null);
	}

	public SegmentationProbabilityConstraint(double weight, FaultSystemRupSet rupSet,
			TransitionProbabilityCalc<?> probabilityCalc, ReferenceRateWeighting referenceRateWeighting) {
		this(weight, rupSet, probabilityCalc, referenceRateWeighting, null);
	}

	/**
	 * @param rateEstimator optional subsection participation estimates, used to scale
	 *        each row by its estimated weighted reference rate, as in the legacy
	 *        relative-rate constraint. Estimates are copied and must correspond to
	 *        this rupture set's subsection indexing.
	 */
	public SegmentationProbabilityConstraint(double weight, FaultSystemRupSet rupSet,
			TransitionProbabilityCalc<?> probabilityCalc, ReferenceRateWeighting referenceRateWeighting,
			SectParticipationRateEstimator rateEstimator) {
		super("Segmentation Probability Constraint, "+Preconditions.checkNotNull(probabilityCalc).getName(),
				"SegProb", weight, true);
		this.probabilityCalc = Preconditions.checkNotNull(probabilityCalc);
		this.referenceRateWeighting = Preconditions.checkNotNull(referenceRateWeighting);
		if (rateEstimator != null)
			estimatedParticipationRates = rateEstimator.estimateSectParticRates().clone();
		setRuptureSet(rupSet);
	}

	/**
	 * Supplies relative, nonnegative subsection weights for the departing side of a
	 * transition. The constraint normalizes their sum to one. This can select the
	 * endpoint, an entire parent, or a distance-weighted neighborhood. Implementations
	 * must be deterministic, use current section IDs, and be Gson serializable.
	 * For internal boundaries, policies can use both endpoint IDs to choose the
	 * appropriate side of the boundary.
	 */
	public interface ReferenceRateWeighting {
		Map<Integer, Double> getSectionWeights(FaultSystemRupSet rupSet, int fromSectionID, int toSectionID);
	}

	/** The legacy reference: participation rate of the departing subsection alone. */
	public static class EndpointReferenceRateWeighting implements ReferenceRateWeighting {

		@Override
		public Map<Integer, Double> getSectionWeights(FaultSystemRupSet rupSet, int fromSectionID, int toSectionID) {
			return Collections.singletonMap(fromSectionID, 1d);
		}
	}

	@Override
	public synchronized void setRuptureSet(FaultSystemRupSet rupSet) {
		Preconditions.checkNotNull(rupSet).requireModule(ClusterRuptures.class);
		if (estimatedParticipationRates != null) {
			Preconditions.checkArgument(estimatedParticipationRates.length == rupSet.getNumSections(),
					"Participation estimates must match rupture set section indexing");
			for (double rate : estimatedParticipationRates)
				Preconditions.checkArgument(Double.isFinite(rate) && rate >= 0d,
						"Invalid participation rate estimate: %s", rate);
		}
		this.rupSet = rupSet;
		rows = null;
	}

	private synchronized void initRows() {
		if (rows != null)
			return;
		Preconditions.checkNotNull(rupSet, "Must set rupture set before using constraint");
		Map<IDPairing, Crossing> crossings = new TreeMap<>();
		collectCrossings(probabilityCalc, crossings);
		List<Row> newRows = new ArrayList<>();
		for (Crossing crossing : crossings.values()) {
			newRows.add(buildRow(crossing, crossing.first, crossing.second));
			newRows.add(buildRow(crossing, crossing.second, crossing.first));
		}
		// Match legacy jump ordering for ordinary single-stranded jump models.
		newRows.sort((a, b) -> {
			int cmp = Integer.compare(rupSet.getFaultSectionData(a.from).getParentSectionId(),
					rupSet.getFaultSectionData(b.from).getParentSectionId());
			if (cmp == 0) cmp = Integer.compare(a.from, b.from);
			if (cmp == 0) cmp = Integer.compare(rupSet.getFaultSectionData(a.to).getParentSectionId(),
					rupSet.getFaultSectionData(b.to).getParentSectionId());
			if (cmp == 0) cmp = Integer.compare(a.to, b.to);
			return cmp;
		});
		rows = newRows;
	}

	private <T extends Transition> void collectCrossings(TransitionProbabilityCalc<T> calc,
			Map<IDPairing, Crossing> crossings) {
		ClusterRuptures ruptures = rupSet.requireModule(ClusterRuptures.class);
		for (int r=0; r<ruptures.size(); r++) {
			ClusterRupture rupture = ruptures.get(r);
			Preconditions.checkArgument(!calc.isDirectional(!rupture.splays.isEmpty()),
					"Initial segmentation constraint requires non-directional probabilities");
			Set<Integer> ruptureSections = new HashSet<>(rupSet.getSectionsIndicesForRup(r));
			for (T transition : calc.getTransitions(rupture)) {
				int from = transition.getFromSection().getSectionId();
				int to = transition.getToSection().getSectionId();
				Preconditions.checkArgument(from != to && ruptureSections.contains(from) && ruptureSections.contains(to),
						"Transition %s -> %s must connect distinct sections in rupture %s", from, to, r);
				RuptureTreeNavigator navigator = rupture.getTreeNavigator();
				FaultSection beforeTo = navigator.getPredecessor(rupSet.getFaultSectionData(to));
				FaultSection beforeFrom = navigator.getPredecessor(rupSet.getFaultSectionData(from));
				Preconditions.checkArgument((beforeTo != null && beforeTo.getSectionId() == from)
						|| (beforeFrom != null && beforeFrom.getSectionId() == to),
						"Transition %s -> %s is not traversed in rupture %s", from, to, r);
				double probability = calc.calcTransitionProbability(rupture, transition, false);
				Preconditions.checkArgument(Double.isFinite(probability) && probability >= 0d && probability <= 1d,
						"Invalid transition probability: %s", probability);
				int first = Math.min(from, to);
				int second = Math.max(from, to);
				// Store both traversal directions under the same ordered subsection pair.
				IDPairing key = new IDPairing(first, second);
				Crossing crossing = crossings.get(key);
				if (crossing == null) {
					crossing = new Crossing(first, second, probability);
					crossings.put(key, crossing);
				} else {
					Preconditions.checkArgument(Math.abs(crossing.probability-probability) <= PROB_TOLERANCE,
							"Rupture-dependent probabilities for transition %s -> %s: %s != %s",
							first, second, crossing.probability, probability);
				}
				// Count each physical crossing once, including when enumerated in reverse.
				crossing.ruptures.add(r);
			}
		}
	}

	private Row buildRow(Crossing crossing, int from, int to) {
		Map<Integer, Double> weights = new TreeMap<>(Preconditions.checkNotNull(
				referenceRateWeighting.getSectionWeights(rupSet, from, to)));
		double sum = 0d;
		for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
			Preconditions.checkArgument(entry.getKey() >= 0 && entry.getKey() < rupSet.getNumSections(),
					"Invalid reference subsection ID: %s", entry.getKey());
			double value = entry.getValue();
			Preconditions.checkArgument(Double.isFinite(value) && value >= 0d,
					"Invalid reference weight: %s", value);
			sum += value;
		}
		Preconditions.checkArgument(Double.isFinite(sum) && sum > 0d, "Reference weights must have positive finite sum");
		final double total = sum;
		weights.replaceAll((id, value) -> value/total);
		return new Row(from, to, crossing, weights);
	}

	@Override
	public int getNumRows() {
		initRows();
		return rows.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		initRows();
		Preconditions.checkArgument(Double.isFinite(weight) && weight >= 0d
				&& Double.isFinite(weight*MAX_WEIGHT_SCALAR), "Invalid constraint weight: %s", weight);
		long count = 0;
		for (Row row : rows) {
			double probability = row.crossing.probability;
			double scalar = weight;
			if (estimatedParticipationRates != null) {
				double estimate = 0d;
				for (Map.Entry<Integer, Double> entry : row.weights.entrySet())
					estimate += entry.getValue()*estimatedParticipationRates[entry.getKey()];
				scalar = estimate > 0d ? scalar/estimate : weight*MAX_WEIGHT_SCALAR;
			}
			// Match legacy probability/rate normalization, including finite handling of p=0.
			scalar = probability > 0d ? Math.min(weight*MAX_WEIGHT_SCALAR, scalar/probability)
					: weight*MAX_WEIGHT_SCALAR;
			Map<Integer, Double> referenceContributions = new TreeMap<>();
			for (Map.Entry<Integer, Double> entry : row.weights.entrySet()) {
				if (entry.getValue() == 0d) continue;
				for (int rupture : rupSet.getRupturesForSection(entry.getKey()))
					referenceContributions.merge(rupture, entry.getValue(), Double::sum);
			}
			for (int rupture : row.crossing.ruptures)
				referenceContributions.putIfAbsent(rupture, 0d);
			for (Map.Entry<Integer, Double> entry : referenceContributions.entrySet()) {
				double coefficient = ((row.crossing.ruptures.contains(entry.getKey()) ? 1d : 0d)
						-probability*entry.getValue())*scalar;
				setA(A, startRow, entry.getKey(), coefficient);
				if (coefficient != 0d) count++;
			}
			d[startRow++] = 0d;
		}
		return count;
	}

	private static class Crossing {
		final int first, second;
		final double probability;
		final Set<Integer> ruptures = new HashSet<>();

		Crossing(int first, int second, double probability) {
			this.first = first;
			this.second = second;
			this.probability = probability;
		}
	}

	private static class Row {
		final int from, to;
		final Crossing crossing;
		final Map<Integer, Double> weights;

		Row(int from, int to, Crossing crossing, Map<Integer, Double> weights) {
			this.from = from;
			this.to = to;
			this.crossing = crossing;
			this.weights = weights;
		}
	}

	public static class ReferenceRateWeightingAdapter extends TypeAdapter<ReferenceRateWeighting> {
		private final Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, ReferenceRateWeighting value) throws IOException {
			out.beginObject();
			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);
			out.endObject();
		}

		@Override
		public ReferenceRateWeighting read(JsonReader in) throws IOException {
			in.beginObject();
			Preconditions.checkState(in.nextName().equals("type"));
			Class<? extends ReferenceRateWeighting> type;
			try {
				type = Class.forName(in.nextString()).asSubclass(ReferenceRateWeighting.class);
			} catch (ClassNotFoundException | ClassCastException e) {
				throw new IOException(e);
			}
			Preconditions.checkState(in.nextName().equals("data"));
			ReferenceRateWeighting value = gson.fromJson(in, type);
			in.endObject();
			return value;
		}
	}
}
