package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;

/**
 * Identifies rupture exemptions when applying a transition probability model as a segmentation constraint.
 * A segmentation constraint bounds the rate crossing a transition by a probability times a reference participation
 * rate. Rupture discretization can make that bound incompatible with any positive participation: for example,
 * if the smallest allowed rupture spans two subsections separated by an internal bend, an endpoint may have no
 * rupture that stops before the bend. Without an exemption, its crossing rate equals its participation rate,
 * so a bound {@code J <= p R} with {@code p < 1} forces both rates to zero.
 *
 * <p>The minimum-rupture policies provide a baseline rupture option at internal transitions. One looks for an
 * eligible non-crossing rupture entirely within the same parent for each side of each transition. The other
 * looks for an eligible rupture that can carry rate by itself under all original segmentation bounds, including
 * bounds elsewhere on its path. This handles cases where the local stopping ruptures cross different bends
 * and each needs the other to build rate. Each policy exempts eligible ruptures with the smallest total
 * subsection count from the applicable side's crossing-rate numerator. Their participation still contributes
 * to its reference rate. Between-parent transitions remain constrained.
 *
 * <p>These are exemptions in segmentation enforcement, not changes to the physical transitions returned by
 * {@link TransitionProbabilityCalc} or to their probabilities. The inversion constraint and segmentation MFD
 * adjustments share this calculation; a rupture exempt on only one side remains constrained on the other side.
 * {@link Policy#NONE} preserves enforcement without exemptions.
 *
 * <p>Instances are snapshots of the supplied rupture set and eligibility mask. Rupture IDs and
 * {@link ClusterRupture} identities refer to that set; rebuild after subsetting or remapping its ruptures.
 */
public class SegmentationRuptureExemptions {

	public enum Policy {
		NONE,
		/**
		 * For each side of an internal bend, look for an eligible rupture that uses the endpoint subsection,
		 * stays on the same parent fault, and avoids this bend. A rupture can qualify even if it crosses
		 * another penalized bend. If no rupture qualifies, exempt all eligible ruptures with the fewest
		 * subsections at that endpoint from this side's bound.
		 */
		MINIMUM_INTERNAL_IF_NO_ALTERNATIVE,
		/**
		 * For each subsection, look for an eligible rupture that uses it and can carry positive rate with
		 * every other rupture at zero. Check all original segmentation bounds before applying exemptions,
		 * including bounds at other bends or jumps along the rupture. If no rupture qualifies, exempt all
		 * eligible ruptures with the fewest subsections from the internal bend bounds on that subsection's side.
		 */
		MINIMUM_INTERNAL_IF_NO_UNCONSTRAINED_RUPTURE
	}

	/** Implemented by MFD adjustments that can honor the same exemptions as the inversion constraint. */
	public interface Configurable {
		void setRuptureExemptions(Policy policy, BitSet eligibleRuptures);
	}

	private final Map<IDPairing, BitSet> exempt;
	private final Map<ClusterRupture, Integer> ruptureIDs;
	private final BitSet eligible;

	private SegmentationRuptureExemptions(Map<IDPairing, BitSet> exempt, Map<ClusterRupture, Integer> ruptureIDs,
			BitSet eligible) {
		this.exempt = exempt;
		this.ruptureIDs = ruptureIDs;
		this.eligible = eligible;
	}

	/** Returns current rupture IDs allowed by minimum-magnitude and binary rupture-exclusion rules. */
	public static BitSet eligibleRuptures(FaultSystemRupSet rupSet, BinaryRuptureProbabilityCalc exclusions) {
		BitSet eligible = new BitSet(rupSet.getNumRuptures());
		ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
		EvenlyDiscretizedFunc refMFD = minMags == null ? null : FaultSysTools.initEmptyMFD(rupSet);
		ClusterRuptures ruptures = rupSet.requireModule(ClusterRuptures.class);
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			if ((minMags == null || !minMags.isRupBelowSectMinMag(r, refMFD))
					&& (exclusions == null || exclusions.isRupAllowed(ruptures.get(r), false))) eligible.set(r);
		return eligible;
	}

	/**
	 * Builds the rupture exemptions for each side of a transition under the selected policy. If the policy
	 * checks whether a rupture can carry rate on its own, this overload uses mean transition probabilities
	 * and the participation rate of each endpoint subsection as the reference rate.
	 *
	 * @param rupSet rupture set whose subsection and rupture IDs index the result
	 * @param model segmentation model that supplies the transitions and probabilities
	 * @param policy rule for granting exemptions
	 * @param eligibleRuptures ruptures to consider, or null to consider all ruptures
	 * @return exemptions for this rupture set, which must be rebuilt if its IDs are remapped
	 */
	public static SegmentationRuptureExemptions build(FaultSystemRupSet rupSet, TransitionProbabilityCalc<?> model,
			Policy policy, BitSet eligibleRuptures) {
		return build(rupSet, model, policy, eligibleRuptures, null);
	}

	/**
	 * Builds the rupture exemptions for each side of a transition under the selected policy. The policy
	 * considers only eligible ruptures when looking for alternatives and choosing which ruptures to exempt.
	 *
	 * <p>Some policies check whether a rupture can carry positive rate with all other rupture rates at zero.
	 * For example, suppose a bend allows at most 80% of the participation at an endpoint subsection to cross
	 * it. If the only active rupture crosses the bend, 100% of the participation crosses it, regardless of how
	 * small the rupture rate is. A second rupture that uses the endpoint and stops before the bend can
	 * supply enough participation to satisfy the bound. If that second rupture crosses a different
	 * penalized bend, both ruptures may need each other's rate. An inversion that starts from zero and
	 * changes one rupture rate at a time can have trouble building that combination. This check identifies
	 * which ruptures can start without a segmentation penalty.
	 *
	 * <p>Callers can supply this check using their own segmentation bounds. For example, an inversion
	 * constraint can use its configured probability aggregation and reference-rate weights. The default
	 * check uses mean transition probabilities and endpoint subsection participation rates.
	 *
	 * @param rupSet rupture set whose subsection and rupture IDs index the result
	 * @param model segmentation model that supplies the transitions and probabilities
	 * @param policy rule for granting exemptions
	 * @param eligibleRuptures ruptures to consider, or null to consider all ruptures
	 * @param findConstrainedRuptures optional calculation returning the IDs of ruptures that would violate
	 *        an original segmentation bound before exemptions if they carried rate alone. Called only when
	 *        the policy needs this check. Pass null to use the default calculation.
	 * @return exemptions for this rupture set, which must be rebuilt if its IDs are remapped
	 */
	public static SegmentationRuptureExemptions build(FaultSystemRupSet rupSet, TransitionProbabilityCalc<?> model,
			Policy policy, BitSet eligibleRuptures, Supplier<BitSet> findConstrainedRuptures) {
		BitSet eligible = eligibleRuptures == null ? new BitSet() : (BitSet)eligibleRuptures.clone();
		if (eligibleRuptures == null) eligible.set(0, rupSet.getNumRuptures());
		Preconditions.checkArgument(eligible.length() <= rupSet.getNumRuptures(), "Invalid eligible rupture IDs");
		Map<IDPairing, BitSet> exempt = new HashMap<>();
		Map<ClusterRupture, Integer> ids = new IdentityHashMap<>();
		ClusterRuptures ruptures = rupSet.requireModule(ClusterRuptures.class);
		Map<IDPairing, BitSet> crossings = new HashMap<>();
		BitSet withinParent = new BitSet();
		BitSet sectionsWithUnconstrainedRupture = new BitSet();
		boolean checkWholeRupture = policy == Policy.MINIMUM_INTERNAL_IF_NO_UNCONSTRAINED_RUPTURE;
		BitSet constrainedRuptures = checkWholeRupture && findConstrainedRuptures != null
				? Preconditions.checkNotNull(findConstrainedRuptures.get()) : null;
		Map<IDPairing, Double> meanProbabilities = checkWholeRupture && constrainedRuptures == null
				? TransitionProbabilityUtils.averageProbabilities(ruptures, model) : null;
		for (int r=0; r<ruptures.size(); r++) {
			ClusterRupture rupture = ruptures.get(r);
			ids.put(rupture, r);
			if (policy == Policy.NONE || !eligible.get(r)) continue;
			if (!checkWholeRupture && isWithinSingleParent(rupSet, r)) withinParent.set(r);
			boolean constrained = constrainedRuptures != null && constrainedRuptures.get(r);
			for (Transition transition : model.getTransitions(rupture)) {
				IDPairing pair = TransitionProbabilityUtils.pair(transition);
				if (meanProbabilities != null && meanProbabilities.get(pair) < 1d) constrained = true;
				if (transition.getFromSection().getParentSectionId() != transition.getToSection().getParentSectionId())
					continue;
				crossings.computeIfAbsent(pair, key -> new BitSet()).set(r);
			}
			if (checkWholeRupture && !constrained)
				for (int section : rupSet.getSectionsIndicesForRup(r)) sectionsWithUnconstrainedRupture.set(section);
		}
		for (Map.Entry<IDPairing, BitSet> entry : crossings.entrySet()) {
			IDPairing pair = entry.getKey();
			for (IDPairing side : new IDPairing[] {pair, pair.getReversed()}) {
				if (checkWholeRupture && sectionsWithUnconstrainedRupture.get(side.getID1())) continue;
				BitSet using = new BitSet();
				for (int r : rupSet.getRupturesForSection(side.getID1())) if (eligible.get(r)) using.set(r);
				if (!checkWholeRupture) {
					BitSet nonCrossing = (BitSet)using.clone();
					nonCrossing.andNot(entry.getValue());
					// A jump-through alternative can need this crossing to carry rate.
					nonCrossing.and(withinParent);
					if (!nonCrossing.isEmpty()) continue;
				}
				int minimum = Integer.MAX_VALUE;
				BitSet smallest = new BitSet();
				for (int r=using.nextSetBit(0); r>=0; r=using.nextSetBit(r+1)) {
					int count = rupSet.getSectionsIndicesForRup(r).size();
					if (count < minimum) { minimum = count; smallest.clear(); }
					if (count == minimum) smallest.set(r);
				}
				if (checkWholeRupture)
					// The minimum is shared across bends, but only crossing ruptures need an exemption here.
					smallest.and(entry.getValue());
				if (!smallest.isEmpty()) exempt.put(side, smallest);
			}
		}
		return new SegmentationRuptureExemptions(exempt, ids, eligible);
	}

	private static boolean isWithinSingleParent(FaultSystemRupSet rupSet, int rupture) {
		int parent = rupSet.getFaultSectionData(rupSet.getSectionsIndicesForRup(rupture).get(0)).getParentSectionId();
		for (int section : rupSet.getSectionsIndicesForRup(rupture))
			if (rupSet.getFaultSectionData(section).getParentSectionId() != parent) return false;
		return true;
	}

	public boolean isExempt(int from, int to, int rupture) {
		BitSet exemptions = exempt.get(new IDPairing(from, to));
		return exemptions != null && exemptions.get(rupture);
	}

	public boolean isExempt(int from, int to, ClusterRupture rupture) {
		return isExempt(from, to, ruptureID(rupture));
	}

	public boolean isExemptOnBothSides(Transition transition, ClusterRupture rupture) {
		int from = transition.getFromSection().getSectionId(), to = transition.getToSection().getSectionId();
		int r = ruptureID(rupture);
		return isExempt(from, to, r) && isExempt(to, from, r);
	}

	public boolean isEligible(ClusterRupture rupture) { return eligible.get(ruptureID(rupture)); }
	public Set<IDPairing> getExemptSides() { return Collections.unmodifiableSet(exempt.keySet()); }
	public BitSet getExemptRuptures(int from, int to) {
		BitSet ruptures = exempt.get(new IDPairing(from, to));
		return ruptures == null ? new BitSet() : (BitSet)ruptures.clone();
	}

	private int ruptureID(ClusterRupture rupture) {
		return Preconditions.checkNotNull(ruptureIDs.get(rupture), "Rupture is not from the configured rupture set");
	}
}
