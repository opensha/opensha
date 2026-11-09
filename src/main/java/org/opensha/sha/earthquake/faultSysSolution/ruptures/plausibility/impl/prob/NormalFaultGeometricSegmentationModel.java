package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.SubsectionTransition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.RakeType;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.GenericJumpProbCalcAdapter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.NormalFaultGeometricSegmentationProb.CoefficientSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.NormalFaultGeometricSegmentationProb.Probabilities;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Measurements;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;

/**
 * Geometric segmentation for normal-to-normal transitions, including internal
 * bends. Normal uses OpenSHA's RakeType.NORMAL classification (rake within 10
 * degrees of -90). Rakes come from the supplied subsections, so NSHM23 callers
 * should use geologic-deformation-model subsections when selecting by geologic rake.
 *
 * <p>Other mechanisms and mixed-mechanism jumps use the optional background jump
 * model. Its normal-to-normal probabilities are replaced, not multiplied by the
 * geometric probabilities. NSHM23 callers must build the background without its
 * hardcoded Wasatch rules, including rules affecting Wasatch-to-other jumps.
 * With no background, other mechanisms contribute no transitions.
 *
 * <p>Geometric probabilities are symmetric and fixed per subsection pair. The
 * transient cache checks section object identity so remapped/cloned rupture sets
 * rebuild their entries. Construct a new model after editing geometry in place.
 */
public class NormalFaultGeometricSegmentationModel implements TransitionProbabilityCalc<Transition> {

	private final CoefficientSet coefficients;
	@JsonAdapter(GenericJumpProbCalcAdapter.class)
	private final JumpProbabilityCalc backgroundModel;
	private final double minBendAngleDegrees;
	// Null means unrestricted. This keeps the serialized configuration free of Infinity.
	private final Double maxPerpendicularDeviationDegrees;
	private transient ConcurrentMap<IDPairing, CachedPair> cache;

	public NormalFaultGeometricSegmentationModel(CoefficientSet coefficients, double minBendAngleDegrees) {
		this(coefficients, minBendAngleDegrees, Double.POSITIVE_INFINITY, null);
	}

	public NormalFaultGeometricSegmentationModel(CoefficientSet coefficients, double minBendAngleDegrees,
			double maxPerpendicularDeviationDegrees) {
		this(coefficients, minBendAngleDegrees, maxPerpendicularDeviationDegrees, null);
	}

	public NormalFaultGeometricSegmentationModel(CoefficientSet coefficients, double minBendAngleDegrees,
			JumpProbabilityCalc backgroundModel) {
		this(coefficients, minBendAngleDegrees, Double.POSITIVE_INFINITY, backgroundModel);
	}

	public NormalFaultGeometricSegmentationModel(CoefficientSet coefficients, double minBendAngleDegrees,
			double maxPerpendicularDeviationDegrees, JumpProbabilityCalc backgroundModel) {
		new FaultTraceSegmentationGeometry(minBendAngleDegrees, maxPerpendicularDeviationDegrees);
		this.backgroundModel = backgroundModel;
		this.coefficients = Preconditions.checkNotNull(coefficients);
		this.minBendAngleDegrees = minBendAngleDegrees;
		this.maxPerpendicularDeviationDegrees = Double.isInfinite(maxPerpendicularDeviationDegrees)
				? null : maxPerpendicularDeviationDegrees;
	}

	@Override
	public String getName() {
		return "Normal-fault geometric segmentation, "+coefficients+", minimum bend "+minBendAngleDegrees+" deg"
				+(backgroundModel == null ? "" : ", background "+backgroundModel.getName());
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return backgroundModel != null && backgroundModel.isDirectional(splayed);
	}

	/** Warms the cache for transitions present in this rupture set, with its current section IDs. */
	public void precompute(FaultSystemRupSet rupSet) {
		for (ClusterRupture rupture : rupSet.requireModule(ClusterRuptures.class))
			getTransitions(rupture);
	}

	@Override
	public List<Transition> getTransitions(ClusterRupture rupture) {
		List<Transition> transitions = new ArrayList<>();
		// Keep real Jump objects for the background: its probabilities may use distance.
		for (Jump jump : rupture.getJumpsIterable()) {
			if (isNormal(jump.fromSection) && isNormal(jump.toSection)) {
				CachedPair pair = getPair(jump.fromSection, jump.toSection);
				if (jump.fromSection.getParentSectionId() != jump.toSection.getParentSectionId()
						|| pair.probabilities.bendApplied)
					transitions.add(jump);
			} else if (backgroundModel != null) {
				transitions.add(jump);
			}
		}
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
			for (FaultSection from : cluster.subSects) {
				if (!isNormal(from)) continue;
				for (FaultSection to : navigator.getDescendants(from)) {
					// Between-cluster transitions were handled as jumps above.
					if (isNormal(to) && cluster.subSects.contains(to)
							&& getPair(from, to).probabilities.bendApplied)
						transitions.add(new SubsectionTransition(from, to));
				}
			}
		}
		return transitions;
	}

	@Override
	public double calcTransitionProbability(ClusterRupture rupture, Transition transition, boolean verbose) {
		FaultSection from = transition.getFromSection(), to = transition.getToSection();
		if (!isNormal(from) || !isNormal(to)) {
			if (backgroundModel == null) return 1d;
			Preconditions.checkArgument(transition instanceof Jump, "Background model requires an actual jump");
			return backgroundModel.calcJumpProbability(rupture, (Jump)transition, verbose);
		}
		Probabilities probabilities = getProbabilities(from, to);
		if (verbose)
			System.out.println(transition+": bend="+probabilities.bendProbability+", gap="+probabilities.gapProbability
					+", stepover/offset="+probabilities.stepoverProbability+", product="+probabilities.combinedProbability);
		return probabilities.combinedProbability;
	}

	public Probabilities getProbabilities(FaultSection first, FaultSection second) {
		return getPair(first, second).probabilities;
	}

	public Measurements getMeasurements(FaultSection first, FaultSection second) {
		return getPair(first, second).measurements;
	}

	public static boolean isNormal(FaultSection section) {
		return RakeType.NORMAL.isMatch(section.getAveRake());
	}

	private CachedPair getPair(FaultSection first, FaultSection second) {
		Preconditions.checkArgument(isNormal(first) && isNormal(second), "Expected two normal-fault subsections");
		Preconditions.checkArgument(first.getSectionId() != second.getSectionId(), "Expected distinct subsections");
		FaultSection lower = first.getSectionId() < second.getSectionId() ? first : second;
		FaultSection upper = first.getSectionId() < second.getSectionId() ? second : first;
		IDPairing key = new IDPairing(lower.getSectionId(), upper.getSectionId());
		return getCache().compute(key, (pair, previous) -> {
			if (previous != null && previous.first == lower && previous.second == upper)
				return previous;
			double maxDeviation = maxPerpendicularDeviationDegrees == null
					? Double.POSITIVE_INFINITY : maxPerpendicularDeviationDegrees;
			Measurements measurements = new FaultTraceSegmentationGeometry(minBendAngleDegrees, maxDeviation).measure(lower, upper);
			Probabilities probabilities = new NormalFaultGeometricSegmentationProb(coefficients).calcProbabilities(measurements);
			return new CachedPair(lower, upper, measurements, probabilities);
		});
	}

	private synchronized ConcurrentMap<IDPairing, CachedPair> getCache() {
		if (cache == null)
			cache = new ConcurrentHashMap<>();
		return cache;
	}

	private static class CachedPair {
		final FaultSection first;
		final FaultSection second;
		final Measurements measurements;
		final Probabilities probabilities;

		CachedPair(FaultSection first, FaultSection second, Measurements measurements, Probabilities probabilities) {
			this.first = first;
			this.second = second;
			this.measurements = measurements;
			this.probabilities = probabilities;
		}
	}
}
