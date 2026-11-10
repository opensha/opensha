package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

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
 * <p>The optional minimum-rupture policy provides a baseline rupture option at such internal transitions.
 * For each endpoint independently, it checks for an eligible non-crossing rupture entirely within the same parent
 * fault. If none exists, all eligible ruptures using that endpoint with the smallest total subsection count are
 * exempt from that side's crossing-rate numerator. Their participation still contributes to its reference rate,
 * and larger crossing ruptures remain constrained. Between-parent transitions are never exempt. A non-crossing
 * rupture that leaves the parent does not establish a local stopping option: its jump constraints can combine
 * with the bend constraint to force zero rate even though a non-crossing alternative technically exists.
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
		 * For internal transitions with no eligible non-crossing rupture entirely within the same parent,
		 * exempt all minimum-subsection-count ruptures independently on each side.
		 */
		MINIMUM_INTERNAL_IF_NO_ALTERNATIVE
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

	/** A null eligibility mask means all ruptures. The result must be rebuilt for a remapped rupture set. */
	public static SegmentationRuptureExemptions build(FaultSystemRupSet rupSet, TransitionProbabilityCalc<?> model,
			Policy policy, BitSet eligibleRuptures) {
		BitSet eligible = eligibleRuptures == null ? new BitSet() : (BitSet)eligibleRuptures.clone();
		if (eligibleRuptures == null) eligible.set(0, rupSet.getNumRuptures());
		Preconditions.checkArgument(eligible.length() <= rupSet.getNumRuptures(), "Invalid eligible rupture IDs");
		Map<IDPairing, BitSet> exempt = new HashMap<>();
		Map<ClusterRupture, Integer> ids = new IdentityHashMap<>();
		ClusterRuptures ruptures = rupSet.requireModule(ClusterRuptures.class);
		Map<IDPairing, BitSet> crossings = new HashMap<>();
		BitSet withinParent = new BitSet();
		for (int r=0; r<ruptures.size(); r++) {
			ClusterRupture rupture = ruptures.get(r);
			ids.put(rupture, r);
			if (policy == Policy.NONE || !eligible.get(r)) continue;
			if (isWithinSingleParent(rupSet, r)) withinParent.set(r);
			for (Transition transition : model.getTransitions(rupture)) {
				if (transition.getFromSection().getParentSectionId() != transition.getToSection().getParentSectionId())
					continue;
				crossings.computeIfAbsent(TransitionProbabilityUtils.pair(transition), key -> new BitSet()).set(r);
			}
		}
		for (Map.Entry<IDPairing, BitSet> entry : crossings.entrySet()) {
			IDPairing pair = entry.getKey();
			for (IDPairing side : new IDPairing[] {pair, pair.getReversed()}) {
				BitSet using = new BitSet();
				for (int r : rupSet.getRupturesForSection(side.getID1())) if (eligible.get(r)) using.set(r);
				BitSet nonCrossing = (BitSet)using.clone();
				nonCrossing.andNot(entry.getValue());
				// Jump-through alternatives can themselves require this crossing rate to be zero.
				// Only a rupture contained in this parent establishes a local stopping option.
				nonCrossing.and(withinParent);
				if (using.isEmpty() || !nonCrossing.isEmpty()) continue;
				int minimum = Integer.MAX_VALUE;
				BitSet smallest = new BitSet();
				for (int r=using.nextSetBit(0); r>=0; r=using.nextSetBit(r+1)) {
					int count = rupSet.getSectionsIndicesForRup(r).size();
					if (count < minimum) { minimum = count; smallest.clear(); }
					if (count == minimum) smallest.set(r);
				}
				exempt.put(side, smallest);
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
