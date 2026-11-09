package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.InitialModelParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint.EndpointReferenceRateWeighting;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint.ReferenceRateWeighting;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint.ProbabilityAggregation;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;

public class SegmentationProbabilityConstraintTest {

	private FaultSystemRupSet rupSet;

	@Before
	public void setUp() {
		List<FaultSection> sections = new ArrayList<>();
		for (int s=0; s<5; s++) {
			FaultSectionPrefData section = new FaultSectionPrefData();
			section.setSectionId(s);
			section.setSectionName("Section "+s);
			section.setParentSectionId(s < 3 ? 10 : 20);
			section.setParentSectionName(s < 3 ? "Parent A" : "Parent B");
			section.setAveDip(90d);
			section.setAveRake(0d);
			section.setAveUpperDepth(0d);
			section.setAveLowerDepth(10d);
			section.setAveSlipRate(1d);
			FaultTrace trace = new FaultTrace(section.getSectionName());
			trace.add(new Location(34d+s*0.01, -118d));
			trace.add(new Location(34d+(s+1)*0.01, -118d));
			section.setFaultTrace(trace);
			sections.add(section);
		}
		List<List<Integer>> sectionIDs = Arrays.asList(
				Arrays.asList(0), Arrays.asList(1), Arrays.asList(2),
				Arrays.asList(0, 1), Arrays.asList(1, 2), Arrays.asList(0, 1, 2),
				Arrays.asList(2, 3), Arrays.asList(0, 1, 2, 3, 4),
				Arrays.asList(3), Arrays.asList(4), Arrays.asList(3, 4));
		double[] mags = new double[sectionIDs.size()];
		double[] areas = new double[sectionIDs.size()];
		Arrays.fill(mags, 6d);
		Arrays.fill(areas, 1e8);
		rupSet = new FaultSystemRupSet(sections, sectionIDs, mags, new double[mags.length], areas, null);
		List<ClusterRupture> ruptures = new ArrayList<>();
		for (List<Integer> ids : sectionIDs) {
			List<FaultSection> ruptureSections = new ArrayList<>();
			for (int id : ids) ruptureSections.add(sections.get(id));
			ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(ruptureSections, null));
		}
		rupSet.addModule(ClusterRuptures.instance(rupSet, ruptures));
	}

	@Test
	public void testJumpBridgeAndBinaryCompatibility() {
		ClusterRupture rupture = rupSet.requireModule(ClusterRuptures.class).get(7);
		JumpProbabilityCalc calc = new ConstantJumpCalc(0.25);
		Jump jump = calc.getTransitions(rupture).iterator().next();
		assertSame(jump.fromSection, jump.getFromSection());
		assertSame(jump.toSection, jump.getToSection());
		assertEquals(0.25, calc.calcTransitionProbability(rupture, jump, false), 0d);
		assertEquals(0.25, calc.calcRuptureProb(rupture, false), 0d);
		JumpProbabilityCalc.BinaryJumpProbabilityCalc binary = new JumpProbabilityCalc.NoJumps();
		assertEquals(0d, binary.calcRuptureProb(rupture, false), 0d);
		assertEquals(1d, binary.calcRuptureProb(rupSet.requireModule(ClusterRuptures.class).get(0), false), 0d);
	}

	@Test
	public void testLegacyInequalityEquivalence() {
		double[] initialRates = new double[rupSet.getNumRuptures()];
		for (int r=0; r<initialRates.length; r++) initialRates[r] = 0.01*(r+1);
		InitialModelParticipationRateEstimator estimator = new InitialModelParticipationRateEstimator(rupSet, initialRates);
		for (double probability : new double[] {0d, 0.25, 1d}) {
			for (boolean normalized : new boolean[] {false, true}) {
				JumpProbabilityCalc calc = new ConstantJumpCalc(probability);
				InversionConstraint legacy = new JumpProbabilityConstraint.RelativeRate(3d, true, rupSet,
						calc, normalized ? estimator : null);
				InversionConstraint updated = new SegmentationProbabilityConstraint(3d, rupSet, calc,
						new EndpointReferenceRateWeighting(), normalized ? estimator : null);
				assertMatrixEquals(encode(legacy), encode(updated));
			}
		}
	}

	@Test
	public void testInternalBoundaryCoefficientsAndPathProduct() {
		InternalCalc calc = new InternalCalc(0.5);
		ClusterRupture rupture = rupSet.requireModule(ClusterRuptures.class).get(5);
		assertEquals(0, rupture.getTotalNumJumps());
		assertEquals(0.25, calc.calcRuptureProb(rupture, false), 0d);
		assertEquals(1d, calc.calcRuptureProb(rupSet.requireModule(ClusterRuptures.class).get(0), false), 0d);
		DoubleMatrix2D matrix = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc));
		assertEquals(6, matrix.rows()); // three internal boundaries, two directions each
		assertEquals(-1d, matrix.get(0, 0), 0d); // 0 -> 1: stops at subsection 0
		assertEquals(0d, matrix.get(0, 1), 0d); // subsection 1 alone does not enter this reference rate
		assertEquals(1d, matrix.get(0, 3), 0d); // crosses 0 -> 1
		assertEquals(1d, matrix.get(0, 5), 0d); // crosses both internal boundaries, once each
		assertEquals(-1d, matrix.get(1, 4), 0d); // reverse row: 1 -> 2 rupture does not cross 1 -> 0
	}

	@Test
	public void testWeightedReferenceIncludesRupturesAwayFromEndpoint() {
		DoubleMatrix2D matrix = encode(new SegmentationProbabilityConstraint(1d, rupSet,
				new InternalCalc(0.5), new ParentWeights()));
		// Parent A weights are 1/6, 2/6, 3/6, with scalar 1/p = 2.
		assertEquals(-1d/6d, matrix.get(0, 0), 1e-12);
		assertEquals(-0.5, matrix.get(0, 2), 1e-12); // subsection 2 never reaches endpoint 0
		assertEquals(1.5, matrix.get(0, 3), 1e-12);
		assertEquals(-5d/6d, matrix.get(0, 4), 1e-12);
		assertEquals(1d, matrix.get(0, 5), 1e-12);
	}

	@Test
	public void testSerializationWithCustomWeightsAndRateEstimates() {
		double[] rates = new double[rupSet.getNumRuptures()];
		Arrays.fill(rates, 0.01);
		for (ReferenceRateWeighting policy : Arrays.asList(new EndpointReferenceRateWeighting(), new ParentWeights())) {
			InversionConstraint original = new SegmentationProbabilityConstraint(2d, rupSet,
					new InternalCalc(0.4), policy, new InitialModelParticipationRateEstimator(rupSet, rates));
			Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class,
					new InversionConstraint.Adapter(rupSet)).create();
			InversionConstraint restored = gson.fromJson(gson.toJson(original, InversionConstraint.class), InversionConstraint.class);
			assertTrue(restored instanceof SegmentationProbabilityConstraint);
			assertTrue(restored.isInequality());
			assertEquals(original.getWeight(), restored.getWeight(), 0d);
			assertMatrixEquals(encode(original), encode(restored));
		}
	}

	@Test
	public void testWeightedEstimateNormalization() {
		double[] rates = new double[rupSet.getNumRuptures()];
		rates[0] = 1d;
		rates[1] = 2d;
		rates[2] = 3d;
		DoubleMatrix2D matrix = encode(new SegmentationProbabilityConstraint(1d, rupSet,
				new InternalCalc(0.5), new ParentWeights(), new InitialModelParticipationRateEstimator(rupSet, rates)));
		assertEquals(1.5/(14d/6d), matrix.get(0, 3), 1e-12);
	}

	@Test
	public void testReversedDuplicateTransitionsDoNotDoubleCrossingRate() {
		InternalCalc calc = new InternalCalc(0.5);
		DoubleMatrix2D expected = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc));
		calc.includeReverse = true;
		assertMatrixEquals(expected, encode(new SegmentationProbabilityConstraint(1d, rupSet, calc)));
	}

	@Test
	public void testReversingRupturesPreservesMatrix() {
		InternalCalc calc = new InternalCalc(0.5);
		DoubleMatrix2D expected = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc));
		List<ClusterRupture> reversed = new ArrayList<>();
		for (ClusterRupture rupture : rupSet.requireModule(ClusterRuptures.class))
			reversed.add(rupture.reversed());
		rupSet.addModule(ClusterRuptures.instance(rupSet, reversed));
		assertMatrixEquals(expected, encode(new SegmentationProbabilityConstraint(1d, rupSet, calc)));
	}

	@Test
	public void testSplayInternalBoundariesAreIncludedOnce() {
		InternalCalc calc = new InternalCalc(0.5);
		DoubleMatrix2D expected = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc));
		List<ClusterRupture> ruptures = new ArrayList<>(rupSet.requireModule(ClusterRuptures.class).getAll());
		FaultSubsectionCluster root = new FaultSubsectionCluster(rupSet.getFaultSectionDataList().subList(0, 3));
		FaultSubsectionCluster branch = new FaultSubsectionCluster(rupSet.getFaultSectionDataList().subList(3, 5));
		ClusterRupture splayed = new ClusterRupture(root).take(new Jump(root.subSects.get(1), root,
				branch.startSect, branch, 1d));
		assertFalse(splayed.splays.isEmpty());
		assertEquals(0.125, calc.calcRuptureProb(splayed, false), 0d);
		ruptures.set(7, splayed);
		rupSet.addModule(ClusterRuptures.instance(rupSet, ruptures));
		assertMatrixEquals(expected, encode(new SegmentationProbabilityConstraint(1d, rupSet, calc)));
	}

	@Test
	public void testRuptureSubsetRebuildsMembership() {
		SegmentationProbabilityConstraint constraint = new SegmentationProbabilityConstraint(1d, rupSet, new InternalCalc(0.5));
		assertEquals(6, constraint.getNumRows());
		FaultSystemRupSet subset = rupSet.getForRuptureSubSet(Arrays.asList(1, 2, 4))
				.getForSectionSubSet(Arrays.asList(1, 2));
		constraint.setRuptureSet(subset);
		assertEquals(2, constraint.getNumRows());
		SegmentationProbabilityConstraint fresh = new SegmentationProbabilityConstraint(1d, subset, new InternalCalc(0.5));
		assertMatrixEquals(encode(fresh, subset), encode(constraint, subset));
	}

	@Test
	public void testZeroWeightAndExtremeProbabilitiesAreFinite() {
		for (double weight : new double[] {0d, 1d}) {
			for (double probability : new double[] {0d, Double.MIN_VALUE, 1d}) {
				DoubleMatrix2D matrix = encode(new SegmentationProbabilityConstraint(weight, rupSet, new InternalCalc(probability)));
				for (int i=0; i<matrix.rows(); i++)
					for (int j=0; j<matrix.columns(); j++)
						assertTrue(Double.isFinite(matrix.get(i, j)));
				if (weight == 0d) assertEquals(0, matrix.cardinality());
			}
		}
	}

	@Test
	public void testAggregateRuptureDependentProbabilitiesAndDuplicateEnumeration() {
		InternalCalc calc = new InternalCalc(0.5);
		calc.ruptureDependent = true;
		for (ProbabilityAggregation aggregation : ProbabilityAggregation.values()) {
			DoubleMatrix2D matrix = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc, aggregation));
			double p = aggregation == ProbabilityAggregation.MIN ? 0.25
					: aggregation == ProbabilityAggregation.MAX ? 0.5 : 1d/3d;
			// Boundary 0-1 is crossed by r3 (p=.5), r5 (p=.25), r7 (p=.25).
			for (int r : new int[] {3, 5, 7}) assertEquals((1d-p)/p, matrix.get(0, r), 1e-12);
			assertEquals(-1d, matrix.get(0, 0), 1e-12);
			calc.includeReverse = true;
			calc.duplicateShortRupturesOnly = true;
			assertMatrixEquals(matrix, encode(new SegmentationProbabilityConstraint(1d, rupSet, calc, aggregation)));
			calc.includeReverse = false;
		}
	}

	@Test
	public void testDifferentDistancesShareBoundAndAverageOncePerRupture() {
		useDifferentJumpDistances();
		JumpProbabilityCalc calc = new DistanceJumpCalc(0.8, 0.2);
		SegmentationProbabilityConstraint defaultConstraint = new SegmentationProbabilityConstraint(1d, rupSet, calc);
		assertEquals(ProbabilityAggregation.AVERAGE, defaultConstraint.getProbabilityAggregation());
		for (ProbabilityAggregation aggregation : ProbabilityAggregation.values()) {
			SegmentationProbabilityConstraint constraint = new SegmentationProbabilityConstraint(1d, rupSet, calc, aggregation);
			assertEquals(2, constraint.getNumRows()); // one pair, two sides; distances do not add rows
			DoubleMatrix2D matrix = encode(constraint);
			// One rupture at p=.8 and two at p=.2: average=.4, not the mean of distance-group means (.5).
			double p = aggregation == ProbabilityAggregation.MIN ? 0.2
					: aggregation == ProbabilityAggregation.MAX ? 0.8 : 0.4;
			for (int row=0; row<2; row++)
				for (int r : new int[] {6, 7, 11}) assertEquals((1d-p)/p, matrix.get(row, r), 1e-12);
			assertEquals(-1d, matrix.get(0, 2), 1e-12);
			assertEquals(-1d, matrix.get(1, 8), 1e-12);
			if (aggregation == ProbabilityAggregation.AVERAGE) assertMatrixEquals(matrix, encode(defaultConstraint));
		}
	}

	@Test
	public void testCombinedBoundClosesSeparateDistanceAllowances() {
		useDifferentJumpDistances();
		JumpProbabilityCalc calc = new ConstantJumpCalc(0.3);
		DoubleMatrix2D legacy = encode(new JumpProbabilityConstraint.RelativeRate(1d, true, rupSet, calc));
		DoubleMatrix2D combined = encode(new SegmentationProbabilityConstraint(1d, rupSet, calc));
		assertEquals(4, legacy.rows());
		assertEquals(2, combined.rows());
		double[] rates = new double[rupSet.getNumRuptures()];
		rates[2] = rates[8] = 0.6; // stop on either side; each endpoint's total participation is 1
		rates[6] = 0.2; // distance group 1 crosses at .2
		rates[7] = rates[11] = 0.1; // distance group 2 also crosses at .2
		for (int row=0; row<legacy.rows(); row++) assertTrue(rowMisfit(legacy, row, rates) <= 0d);
		for (int row=0; row<combined.rows(); row++) assertTrue(rowMisfit(combined, row, rates) > 0d);
	}

	@Test
	public void testAggregationIncludesZeroAndOneAndSerializesPolicy() {
		useDifferentJumpDistances();
		JumpProbabilityCalc calc = new DistanceJumpCalc(0d, 1d);
		Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class,
				new InversionConstraint.Adapter(rupSet)).create();
		for (ProbabilityAggregation aggregation : ProbabilityAggregation.values()) {
			SegmentationProbabilityConstraint original = new SegmentationProbabilityConstraint(1d, rupSet, calc, aggregation);
			String json = gson.toJson(original, InversionConstraint.class);
			SegmentationProbabilityConstraint restored = (SegmentationProbabilityConstraint)gson.fromJson(json, InversionConstraint.class);
			assertEquals(aggregation, restored.getProbabilityAggregation());
			assertMatrixEquals(encode(original), encode(restored));
			DoubleMatrix2D matrix = encode(original);
			double expected = aggregation == ProbabilityAggregation.MIN ? 1e5
					: aggregation == ProbabilityAggregation.MAX ? 0d : 0.5; // average=2/3
			for (int r : new int[] {6, 7, 11}) assertEquals(expected, matrix.get(0, r), 1e-12);
			// Older JSON without this option defaults to AVERAGE.
			com.google.gson.JsonObject oldJSON = gson.toJsonTree(original, InversionConstraint.class).getAsJsonObject();
			oldJSON.getAsJsonObject("data").remove("probabilityAggregation");
			SegmentationProbabilityConstraint oldRestored = (SegmentationProbabilityConstraint)gson.fromJson(oldJSON, InversionConstraint.class);
			assertEquals(ProbabilityAggregation.AVERAGE, oldRestored.getProbabilityAggregation());
			assertMatrixEquals(encode(new SegmentationProbabilityConstraint(1d, rupSet, calc)), encode(oldRestored));
		}
	}

	@Test
	public void testSubsetRebuildsProbabilityAverage() {
		InternalCalc calc = new InternalCalc(0.5);
		calc.ruptureDependent = true;
		SegmentationProbabilityConstraint constraint = new SegmentationProbabilityConstraint(1d, rupSet, calc);
		constraint.getNumRows();
		FaultSystemRupSet subset = rupSet.getForRuptureSubSet(Arrays.asList(3));
		constraint.setRuptureSet(subset);
		assertMatrixEquals(encode(new SegmentationProbabilityConstraint(1d, subset, calc), subset), encode(constraint, subset));
		assertEquals(1d, encode(constraint, subset).get(0, 0), 1e-12); // only the p=.5 crossing remains
	}

	private void useDifferentJumpDistances() {
		List<ClusterRupture> ruptures = new ArrayList<>(rupSet.requireModule(ClusterRuptures.class).getAll());
		for (int r : new int[] {6, 7}) {
			ClusterRupture original = ruptures.get(r);
			Jump jump = original.getJumpsIterable().iterator().next();
			ruptures.set(r, new ClusterRupture(jump.fromCluster).take(new Jump(jump.fromSection, jump.fromCluster,
					jump.toSection, jump.toCluster, r == 6 ? 1d : 2d)));
		}
		ruptures.add(ruptures.get(7).reversed());
		double[] mags = new double[ruptures.size()];
		Arrays.fill(mags, 6d);
		rupSet = FaultSystemRupSet.builderForClusterRups(rupSet.getFaultSectionDataList(), ruptures).rupMags(mags).build();
	}

	private static double rowMisfit(DoubleMatrix2D matrix, int row, double[] rates) {
		double sum = 0d;
		for (int r=0; r<rates.length; r++) sum += matrix.get(row, r)*rates[r];
		return sum;
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectDirectionalProbabilities() {
		InternalCalc calc = new InternalCalc(0.5);
		calc.directional = true;
		new SegmentationProbabilityConstraint(1d, rupSet, calc).getNumRows();
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectInvalidProbability() {
		new SegmentationProbabilityConstraint(1d, rupSet, new InternalCalc(Double.NaN)).getNumRows();
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectInvalidReferenceWeights() {
		new SegmentationProbabilityConstraint(1d, rupSet, new InternalCalc(0.5),
				(set, from, to) -> Collections.singletonMap(from, -1d)).getNumRows();
	}

	private DoubleMatrix2D encode(InversionConstraint constraint) {
		return encode(constraint, rupSet);
	}

	private DoubleMatrix2D encode(InversionConstraint constraint, FaultSystemRupSet set) {
		int startRow = 2;
		DoubleMatrix2D matrix = new SparseDoubleMatrix2D(startRow+constraint.getNumRows(), set.getNumRuptures());
		double[] d = new double[matrix.rows()];
		Arrays.fill(d, 123d);
		long count = constraint.encode(matrix, d, startRow);
		assertEquals(123d, d[0], 0d);
		assertEquals(123d, d[1], 0d);
		for (int row=startRow; row<d.length; row++) assertEquals(0d, d[row], 0d);
		if (constraint instanceof SegmentationProbabilityConstraint) assertEquals(matrix.cardinality(), count);
		return matrix.viewPart(startRow, 0, constraint.getNumRows(), set.getNumRuptures());
	}

	private static void assertMatrixEquals(DoubleMatrix2D expected, DoubleMatrix2D actual) {
		assertEquals(expected.rows(), actual.rows());
		assertEquals(expected.columns(), actual.columns());
		for (int row=0; row<expected.rows(); row++)
			for (int col=0; col<expected.columns(); col++)
				assertEquals("row="+row+", col="+col, expected.get(row, col), actual.get(row, col),
						Math.max(1d, Math.abs(expected.get(row, col)))*1e-12);
	}

	public static class ConstantJumpCalc implements JumpProbabilityCalc {
		private double probability;
		ConstantJumpCalc(double probability) { this.probability = probability; }
		@Override public String getName() { return "Constant jump"; }
		@Override public boolean isDirectional(boolean splayed) { return false; }
		@Override public double calcJumpProbability(ClusterRupture rupture, Jump jump, boolean verbose) { return probability; }
	}

	public static class DistanceJumpCalc implements JumpProbabilityCalc {
		private double firstProbability, secondProbability;
		DistanceJumpCalc(double firstProbability, double secondProbability) {
			this.firstProbability = firstProbability;
			this.secondProbability = secondProbability;
		}
		@Override public String getName() { return "Distance-varying jump"; }
		@Override public boolean isDirectional(boolean splayed) { return false; }
		@Override public double calcJumpProbability(ClusterRupture rupture, Jump jump, boolean verbose) {
			return jump.distance == 1d ? firstProbability : secondProbability;
		}
	}

	private static class Boundary implements Transition {
		private final FaultSection from, to;
		Boundary(FaultSection from, FaultSection to) { this.from = from; this.to = to; }
		@Override public FaultSection getFromSection() { return from; }
		@Override public FaultSection getToSection() { return to; }
	}

	public static class InternalCalc implements TransitionProbabilityCalc<Boundary> {
		private double probability;
		private boolean includeReverse, ruptureDependent, directional, duplicateShortRupturesOnly;
		InternalCalc(double probability) { this.probability = probability; }
		@Override public String getName() { return "Internal boundaries"; }
		@Override public boolean isDirectional(boolean splayed) { return directional; }
		@Override public Iterable<Boundary> getTransitions(ClusterRupture rupture) {
			List<Boundary> boundaries = new ArrayList<>();
			for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
				for (FaultSection from : cluster.subSects) {
					for (FaultSection to : rupture.getTreeNavigator().getDescendants(from)) {
						if (from.getParentSectionId() == to.getParentSectionId()) {
							boundaries.add(new Boundary(from, to));
							if (includeReverse && (!duplicateShortRupturesOnly || rupture.getTotalNumSects() == 2))
								boundaries.add(new Boundary(to, from));
						}
					}
				}
			}
			return boundaries;
		}
		@Override public double calcTransitionProbability(ClusterRupture rupture, Boundary boundary, boolean verbose) {
			return ruptureDependent && rupture.getTotalNumSects() > 2 ? probability*0.5 : probability;
		}
	}

	/** Example extension; deliberately uses unequal weights to exercise normalization. */
	public static class ParentWeights implements ReferenceRateWeighting {
		@Override public Map<Integer, Double> getSectionWeights(FaultSystemRupSet set, int from, int to) {
			int parent = set.getFaultSectionData(from).getParentSectionId();
			Map<Integer, Double> weights = new HashMap<>();
			for (FaultSection section : set.getFaultSectionDataList())
				if (section.getParentSectionId() == parent)
					weights.put(section.getSectionId(), section.getSectionId()+1d);
			return weights;
		}
	}
}
