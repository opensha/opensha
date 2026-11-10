package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.faultSurface.FaultTrace;

/**
 * Physical crossing rates grouped by subsection endpoints for segmentation reporting. Each rupture contributes
 * once per pair. A separate discovery model allows a comparison solution to be evaluated at the same internal
 * boundaries even if its own probability model defines only jumps. Enforcement diagnostics use endpoint
 * participation as the reference, matching the default segmentation constraint; they are not a substitute for
 * evaluating an inversion configuration with custom reference weights or probability aggregation.
 */
public class SegmentationTransitionRates {

	private final FaultSystemSolution solution;
	private final Map<IDPairing, BitSet> crossings = new TreeMap<>();
	private final Map<IDPairing, Double> probabilities;
	private final SegmentationRuptureExemptions exemptions;

	public SegmentationTransitionRates(FaultSystemSolution solution, TransitionProbabilityCalc<?> probabilityModel,
			SegmentationRuptureExemptions exemptions, TransitionProbabilityCalc<?>... discoveryModels) {
		this.solution = solution;
		this.exemptions = exemptions;
		ClusterRuptures ruptures = solution.getRupSet().requireModule(ClusterRuptures.class);
		probabilities = probabilityModel == null ? Collections.emptyMap()
				: TransitionProbabilityUtils.averageProbabilities(ruptures, probabilityModel);
		List<TransitionProbabilityCalc<?>> discovery = new ArrayList<>();
		if (probabilityModel != null) discovery.add(probabilityModel);
		for (TransitionProbabilityCalc<?> model : discoveryModels)
			if (model != null && !discovery.contains(model)) discovery.add(model);
		for (int r=0; r<ruptures.size(); r++) {
			ClusterRupture rupture = ruptures.get(r);
			Set<IDPairing> pairs = new HashSet<>();
			for (Transition jump : rupture.getJumpsIterable()) pairs.add(TransitionProbabilityUtils.pair(jump));
			for (TransitionProbabilityCalc<?> discoveryModel : discovery)
				for (Transition transition : discoveryModel.getTransitions(rupture))
					pairs.add(TransitionProbabilityUtils.pair(transition));
			for (IDPairing pair : pairs) crossings.computeIfAbsent(pair, key -> new BitSet()).set(r);
		}
	}

	public Set<IDPairing> getPairs() { return Collections.unmodifiableSet(crossings.keySet()); }

	public boolean isInternal(IDPairing pair) {
		FaultSystemRupSet set = solution.getRupSet();
		return set.getFaultSectionData(pair.getID1()).getParentSectionId()
				== set.getFaultSectionData(pair.getID2()).getParentSectionId();
	}

	/** Mean model probability over actual rupture contexts, independent of solution rates and magnitude cutoff. */
	public double probability(IDPairing pair) { return probabilities.getOrDefault(pair, Double.NaN); }

	public double crossingRate(IDPairing pair, double minMag) { return sum(crossings.get(pair), minMag); }

	public double participationRate(int section, double minMag) {
		double rate = 0d;
		for (int r : solution.getRupSet().getRupturesForSection(section))
			if (solution.getRupSet().getMagForRup(r) >= minMag) rate += solution.getRateForRup(r);
		return rate;
	}

	public double exemptRate(int from, int to, double minMag) {
		return exemptions == null ? 0d : sum(exemptions.getExemptRuptures(from, to), minMag);
	}

	public double passthrough(IDPairing pair, double minMag,
			RateCombiner combiner) {
		double reference = combiner.combine(participationRate(pair.getID1(), minMag),
				participationRate(pair.getID2(), minMag));
		return reference > 0d ? crossingRate(pair, minMag)/reference : Double.NaN;
	}

	private double sum(BitSet ruptures, double minMag) {
		double rate = 0d;
		if (ruptures != null)
			for (int r=ruptures.nextSetBit(0); r>=0; r=ruptures.nextSetBit(r+1))
				if (solution.getRupSet().getMagForRup(r) >= minMag) rate += solution.getRateForRup(r);
		return rate;
	}

	/** Midpoint of the closest trace endpoints; contiguous subsections place the marker at their shared boundary. */
	public Location boundaryLocation(IDPairing pair) {
		FaultTrace first = solution.getRupSet().getFaultSectionData(pair.getID1()).getFaultTrace();
		FaultTrace second = solution.getRupSet().getFaultSectionData(pair.getID2()).getFaultTrace();
		Location a = first.first(), b = second.first();
		double closest = Double.POSITIVE_INFINITY;
		for (Location x : new Location[] {first.first(), first.last()})
			for (Location y : new Location[] {second.first(), second.last()}) {
				double distance = LocationUtils.horzDistanceFast(x, y);
				if (distance < closest) { closest = distance; a = x; b = y; }
			}
		return closest == 0d ? a : LocationUtils.location(a, LocationUtils.azimuthRad(a, b), 0.5*closest);
	}

	/** Fields are shared by CSV and GeoJSON; undefined ratios/model probabilities are omitted. */
	public FeatureProperties properties(IDPairing pair, double minMag) {
		FeatureProperties props = new FeatureProperties();
		props.set("transition_type", isInternal(pair) ? "internal" : "jump");
		props.set("first_id", pair.getID1());
		props.set("second_id", pair.getID2());
		props.set("first_name", solution.getRupSet().getFaultSectionData(pair.getID1()).getSectionName());
		props.set("second_name", solution.getRupSet().getFaultSectionData(pair.getID2()).getSectionName());
		props.set("minimum_magnitude", minMag);
		double crossing = crossingRate(pair, minMag), probability = probability(pair);
		props.set("physical_crossing_rate", crossing);
		putFinite(props, "model_probability", probability);
		for (int side=0; side<2; side++) {
			int from = side == 0 ? pair.getID1() : pair.getID2(), to = side == 0 ? pair.getID2() : pair.getID1();
			String prefix = side == 0 ? "first_" : "second_";
			double reference = participationRate(from, minMag), exempt = exemptRate(from, to, minMag);
			double charged = Math.max(0d, crossing-exempt);
			props.set(prefix+"endpoint_reference_rate", reference);
			props.set(prefix+"exempt_crossing_rate", exempt);
			props.set(prefix+"constrained_crossing_rate", charged);
			putFinite(props, prefix+"physical_passthrough", reference > 0d ? crossing/reference : Double.NaN);
			putFinite(props, prefix+"constrained_passthrough", reference > 0d ? charged/reference : Double.NaN);
			putFinite(props, prefix+"endpoint_bound_residual", charged-probability*reference);
		}
		return props;
	}

	private static void putFinite(FeatureProperties props, String name, double value) {
		if (Double.isFinite(value)) props.set(name, value);
	}
}
