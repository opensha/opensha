package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint.EndpointReferenceRateWeighting;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint.ProbabilityAggregation;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.SubsectionTransition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.TransitionProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationRuptureExemptions.Policy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;

public class SegmentationRuptureExemptionsTest {

	private FaultSystemRupSet rupSet;
	private AllTransitions model = new AllTransitions();

	@Before
	public void setUp() {
		List<FaultSection> sections = new ArrayList<>();
		for (int s=0; s<4; s++) {
			FaultSectionPrefData section = new FaultSectionPrefData();
			section.setSectionId(s);
			section.setParentSectionId(s < 3 ? 10 : 20);
			section.setParentSectionName("Parent "+section.getParentSectionId());
			section.setSectionName("Subsection "+s);
			section.setAveDip(90d);
			section.setAveUpperDepth(0d);
			section.setAveLowerDepth(10d);
			section.setAveSlipRate(1d);
			FaultTrace trace = new FaultTrace(section.getSectionName());
			trace.add(new Location(34+s*0.01, -118));
			trace.add(new Location(34+(s+1)*0.01, -118));
			section.setFaultTrace(trace);
			sections.add(section);
		}
		List<ClusterRupture> ruptures = new ArrayList<>();
		for (List<Integer> ids : Arrays.asList(Arrays.asList(0, 1), Arrays.asList(0, 1, 2), Arrays.asList(1, 2),
				Arrays.asList(1, 0), Arrays.asList(2), Arrays.asList(0), Arrays.asList(2, 3))) {
			List<FaultSection> participating = new ArrayList<>();
			for (int id : ids) participating.add(sections.get(id));
			ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(participating, null));
		}
		rupSet = FaultSystemRupSet.builderForClusterRups(sections, ruptures)
				.rupMags(new double[] {6.3, 6.6, 6.3, 6.3, 6.0, 5.0, 6.3}).build();
	}

	private BitSet eligible() {
		BitSet eligible = new BitSet();
		eligible.set(0, rupSet.getNumRuptures());
		eligible.clear(5); // The lone non-crossing option at endpoint 0 is unavailable.
		return eligible;
	}

	@Test
	public void testSideSpecificMinimumTiesAndPhysicalProbabilities() {
		SegmentationRuptureExemptions exemptions = build(eligible());
		assertEquals(1, exemptions.getExemptSides().size());
		assertEquals(bits(0, 3), exemptions.getExemptRuptures(0, 1));
		assertFalse(exemptions.isExempt(0, 1, 1)); // larger crossing remains constrained
		assertFalse(exemptions.isExempt(1, 0, 0)); // this endpoint can stop via rupture 2
		assertFalse(exemptions.isExempt(2, 3, 6)); // never exempt a between-parent jump
		assertFalse(exemptions.isExempt(3, 2, 6));
		ClusterRupture rupture = rupSet.requireModule(ClusterRuptures.class).get(0);
		assertEquals(0.5, model.calcRuptureProb(rupture, false), 0d);
	}

	@Test
	public void testAvailableNonCrossingOptionDisablesExemption() {
		assertTrue(build(null).getExemptSides().isEmpty());
		assertTrue(SegmentationRuptureExemptions.build(rupSet, model, Policy.NONE, eligible()).getExemptSides().isEmpty());
	}

	@Test
	public void testMinimumUsesEligibleRupturesNotFixedSize() {
		SegmentationRuptureExemptions exemptions = build(bits(1));
		assertTrue(exemptions.isExempt(0, 1, 1)); // only a three-subsection rupture is eligible
		assertTrue(exemptions.isExempt(1, 0, 1));
	}

	@Test
	public void testJumpThroughAlternativeDoesNotPreventExemption() {
		addJumpThroughRuptures();
		BitSet eligible = bits(0, 1, 3, 7, 8);
		SegmentationRuptureExemptions exemptions = build(eligible);
		assertEquals(bits(0, 3), exemptions.getExemptRuptures(0, 1));
		assertEquals(bits(0, 3), exemptions.getExemptRuptures(1, 0));
		assertFalse(exemptions.isExempt(0, 1, 1)); // larger crossing still charged
		assertFalse(exemptions.isExempt(3, 0, 7)); // jump-through rupture remains subject to jump bounds
		assertFalse(exemptions.isExempt(0, 4, 7));
		DoubleMatrix2D matrix = encode(constraint(eligible));
		for (int row=0; row<matrix.rows(); row++)
			assertTrue("Minimum local rupture must permit positive rate", matrix.get(row, 0) <= 0d);

		eligible.set(2); // [1,2] is a genuine local stopping option only for endpoint 1.
		exemptions = build(eligible);
		assertEquals(bits(0, 3), exemptions.getExemptRuptures(0, 1));
		assertTrue(exemptions.getExemptRuptures(1, 0).isEmpty());
	}

	private void addJumpThroughRuptures() {
		List<FaultSection> sections = new ArrayList<>(rupSet.getFaultSectionDataList());
		FaultSection extra = sections.get(3).clone();
		extra.setSectionId(4);
		extra.setParentSectionId(30);
		extra.setSectionName("Subsection 4");
		sections.add(extra);
		List<ClusterRupture> ruptures = new ArrayList<>();
		for (ClusterRupture rupture : rupSet.requireModule(ClusterRuptures.class)) ruptures.add(rupture);
		for (int endpoint : new int[] {0, 1})
			ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(
					List.of(sections.get(3), sections.get(endpoint), extra), null));
		double[] mags = Arrays.copyOf(rupSet.getMagForAllRups(), ruptures.size());
		mags[7] = mags[8] = 6.6;
		rupSet = FaultSystemRupSet.builderForClusterRups(sections, ruptures).rupMags(mags).build();
	}

	@Test
	public void testMagnitudeAndBinaryExclusionsDetermineAvailability() {
		rupSet.addModule(ModSectMinMags.instance(rupSet, new double[] {6d, 6d, 6d, 6d}));
		BitSet eligible = SegmentationRuptureExemptions.eligibleRuptures(rupSet, null);
		assertFalse(eligible.get(5));
		assertEquals(bits(0, 3), build(eligible).getExemptRuptures(0, 1));
		eligible = SegmentationRuptureExemptions.eligibleRuptures(rupSet, new BinaryRuptureProbabilityCalc() {
			@Override public String getName() { return "Exclude all two-subsection ruptures"; }
			@Override public boolean isDirectional(boolean splayed) { return false; }
			@Override public boolean isRupAllowed(ClusterRupture rupture, boolean verbose) {
				return rupture.getTotalNumSects() != 2;
			}
		});
		assertEquals(bits(1), build(eligible).getExemptRuptures(0, 1));
	}

	@Test
	public void testConstraintNumeratorAndDenominatorAreSideSpecific() {
		DoubleMatrix2D matrix = encode(constraint(eligible()));
		assertEquals(-1d, matrix.get(0, 0), 1e-12); // minimum: reference contribution retained, crossing omitted
		assertEquals(-1d, matrix.get(0, 3), 1e-12); // tied minimum, reversed traversal
		assertEquals(1d, matrix.get(0, 1), 1e-12); // larger crossing still constrained
		assertEquals(1d, matrix.get(1, 0), 1e-12); // opposite side retains the original crossing
		assertEquals(-1d, matrix.get(1, 2), 1e-12); // non-crossing alternative on opposite side
	}

	@Test
	public void testTwoSubsectionFaultCanHavePositiveRate() {
		BitSet eligible = bits(0, 1, 3);
		DoubleMatrix2D matrix = encode(constraint(eligible));
		for (int side=0; side<2; side++) {
			assertTrue(matrix.get(side, 0) < 0d);
			assertTrue(matrix.get(side, 3) < 0d);
			assertTrue(matrix.get(side, 1) > 0d);
		}
	}

	@Test
	public void testSerializationRebuildsExemptions() {
		InversionConstraint original = constraint(eligible());
		Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class,
				new InversionConstraint.Adapter(rupSet)).create();
		InversionConstraint restored = gson.fromJson(gson.toJson(original, InversionConstraint.class),
				InversionConstraint.class);
		DoubleMatrix2D expected = encode(original), actual = encode(restored);
		assertEquals(expected.rows(), actual.rows());
		for (int row=0; row<expected.rows(); row++)
			for (int col=0; col<expected.columns(); col++) assertEquals(expected.get(row, col), actual.get(row, col), 0d);
	}

	@Test
	public void testSubsetRemappingRebuildsMinimumAndSides() {
		FaultSystemRupSet subset = rupSet.getForRuptureSubSet(List.of(1)).getForSectionSubSet(List.of(0, 1, 2));
		SegmentationRuptureExemptions exemptions = SegmentationRuptureExemptions.build(subset, model,
				Policy.MINIMUM_INTERNAL_IF_NO_ALTERNATIVE, null);
		assertTrue(exemptions.isExempt(0, 1, 0));
		assertTrue(exemptions.isExempt(1, 0, 0));
	}

	private SegmentationRuptureExemptions build(BitSet eligible) {
		return SegmentationRuptureExemptions.build(rupSet, model, Policy.MINIMUM_INTERNAL_IF_NO_ALTERNATIVE, eligible);
	}

	private SegmentationProbabilityConstraint constraint(BitSet eligible) {
		return new SegmentationProbabilityConstraint(1d, rupSet, model, new EndpointReferenceRateWeighting(), null,
				ProbabilityAggregation.AVERAGE, Policy.MINIMUM_INTERNAL_IF_NO_ALTERNATIVE, eligible);
	}

	private DoubleMatrix2D encode(InversionConstraint constraint) {
		DoubleMatrix2D matrix = new SparseDoubleMatrix2D(constraint.getNumRows(), rupSet.getNumRuptures());
		constraint.encode(matrix, new double[matrix.rows()], 0);
		return matrix;
	}

	private static BitSet bits(int... ids) {
		BitSet result = new BitSet();
		for (int id : ids) result.set(id);
		return result;
	}

	public static class AllTransitions implements TransitionProbabilityCalc<SubsectionTransition> {
		@Override public String getName() { return "All test transitions"; }
		@Override public boolean isDirectional(boolean splayed) { return false; }
		@Override public List<SubsectionTransition> getTransitions(ClusterRupture rupture) {
			List<SubsectionTransition> transitions = new ArrayList<>();
			for (FaultSection from : rupture.buildOrderedSectionList())
				for (FaultSection to : rupture.getTreeNavigator().getDescendants(from))
					transitions.add(new SubsectionTransition(from, to));
			return transitions;
		}
		@Override public double calcTransitionProbability(ClusterRupture rupture, SubsectionTransition t, boolean verbose) {
			return 0.5;
		}
	}
}
