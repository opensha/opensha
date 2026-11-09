package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SegmentationProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.SubsectionTransition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Transition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.NormalFaultGeometricSegmentationProb.CoefficientSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;

public class NormalFaultGeometricSegmentationModelTest {

	private List<FaultSection> sections;
	private FaultSystemRupSet rupSet;
	private List<ClusterRupture> ruptures;

	@Before
	public void setUp() {
		sections = Arrays.asList(section(0, 10, "Wasatch A", 0, 0, 0, 5),
				section(1, 10, "Wasatch A", 0, 5, 0, 10), section(2, 10, "Wasatch A", 0, 10, 3, 15),
				section(3, 11, "Wasatch B", 5, 17, 5, 22), section(4, 11, "Wasatch B", 5, 22, 5, 27),
				section(5, 20, "Other fault", 10, 15, 10, 20), section(6, 20, "Other fault", 10, 20, 10, 25));
		sections.get(5).setAveRake(0d);
		sections.get(6).setAveRake(0d);
		List<List<Integer>> ids = Arrays.asList(Arrays.asList(0, 1), Arrays.asList(0, 1, 2),
				Arrays.asList(2, 3, 4), Arrays.asList(5, 6), Arrays.asList(2, 5, 6), Arrays.asList(0, 1, 2, 3, 4));
		ruptures = new ArrayList<>();
		for (List<Integer> ruptureIDs : ids) {
			List<FaultSection> ruptureSections = new ArrayList<>();
			for (int id : ruptureIDs) ruptureSections.add(sections.get(id));
			ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(ruptureSections, new SectionDistanceAzimuthCalculator(sections)));
		}
		double[] mags = new double[ids.size()];
		Arrays.fill(mags, 6d);
		rupSet = FaultSystemRupSet.builderForClusterRups(sections, ruptures).rupMags(mags).build();
	}

	@Test
	public void testTransitionsAndMechanismScope() {
		NormalFaultGeometricSegmentationModel model = model();
		assertTrue(model.getTransitions(ruptures.get(0)).isEmpty());
		assertEquals(Arrays.asList(new SubsectionTransition(sections.get(1), sections.get(2))), model.getTransitions(ruptures.get(1)));
		assertTrue(model.getTransitions(ruptures.get(3)).isEmpty());
		assertTrue(model.getTransitions(ruptures.get(4)).isEmpty());
		assertEquals(1d, model.calcRuptureProb(ruptures.get(3), false), 0d);
		assertEquals(1d, model.calcTransitionProbability(ruptures.get(4), new SubsectionTransition(sections.get(2), sections.get(5)), false), 0d);
		assertEquals(2, model.getTransitions(ruptures.get(5)).size());
	}

	@Test
	public void testCanonicalCacheAndRuptureReversal() {
		NormalFaultGeometricSegmentationModel model = model();
		assertSame(model.getProbabilities(sections.get(1), sections.get(2)), model.getProbabilities(sections.get(2), sections.get(1)));
		assertEquals(model.calcRuptureProb(ruptures.get(5), false), model.calcRuptureProb(ruptures.get(5).reversed(), false), 0d);
		assertSame(model.getProbabilities(sections.get(2), sections.get(3)), model.getProbabilities(sections.get(3), sections.get(2)));
	}

	@Test
	public void testCacheRebuildsWhenSectionIDsAreReused() {
		NormalFaultGeometricSegmentationModel model = model();
		assertTrue(model.getProbabilities(sections.get(1), sections.get(2)).bendApplied);
		FaultSection replacement = section(2, 10, "Wasatch A", 0, 10, 0, 15);
		assertFalse(model.getProbabilities(sections.get(1), replacement).bendApplied);
		assertTrue(model.getProbabilities(sections.get(1), sections.get(2)).bendApplied);
	}

	@Test
	public void testInternalBoundaryConstraintAndOutsideColumns() {
		NormalFaultGeometricSegmentationModel model = model();
		SegmentationProbabilityConstraint constraint = new SegmentationProbabilityConstraint(1d, rupSet, model);
		DoubleMatrix2D matrix = encode(constraint, rupSet);
		assertEquals(4, constraint.getNumRows());
		double p = model.getProbabilities(sections.get(1), sections.get(2)).combinedProbability;
		assertEquals(-1d, matrix.get(0, 0), 1e-12); // stops before bend
		assertEquals((1d-p)/p, matrix.get(0, 1), 1e-12); // crosses bend
		for (int row=0; row<matrix.rows(); row++) assertEquals(0d, matrix.get(row, 3), 0d);
	}

	@Test
	public void testSubsetRemappingAndConstraintSerialization() {
		for (CoefficientSet coefficients : CoefficientSet.values()) {
			NormalFaultGeometricSegmentationModel model = new NormalFaultGeometricSegmentationModel(coefficients, 10d);
			model.precompute(rupSet);
			FaultSystemRupSet subset = rupSet.getForRuptureSubSet(Arrays.asList(2)).getForSectionSubSet(Arrays.asList(2, 3, 4));
			model.precompute(subset);
			NormalFaultGeometricSegmentationModel fresh = new NormalFaultGeometricSegmentationModel(coefficients, 10d);
			ClusterRupture rupture = subset.requireModule(ClusterRuptures.class).get(0);
			assertEquals(fresh.calcRuptureProb(rupture, false), model.calcRuptureProb(rupture, false), 0d);
			InversionConstraint original = new SegmentationProbabilityConstraint(1d, subset, model);
			Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class, new InversionConstraint.Adapter(subset)).create();
			String json = gson.toJson(original, InversionConstraint.class);
			assertFalse(json.contains("Infinity"));
			InversionConstraint restored = gson.fromJson(json, InversionConstraint.class);
			DoubleMatrix2D expected = encode(original, subset), actual = encode(restored, subset);
			assertEquals(expected.rows(), actual.rows());
			for (int row=0; row<expected.rows(); row++)
				for (int col=0; col<expected.columns(); col++) assertEquals(expected.get(row, col), actual.get(row, col), 0d);
		}
	}

	@Test
	public void testInternalBoundaryOnSplay() {
		FaultSubsectionCluster root = new FaultSubsectionCluster(sections.subList(3, 5));
		FaultSubsectionCluster branch = new FaultSubsectionCluster(sections.subList(0, 3));
		ClusterRupture rupture = new ClusterRupture(root).take(new Jump(root.startSect, root, branch.startSect, branch, 2d));
		assertFalse(rupture.splays.isEmpty());
		List<Transition> transitions = model().getTransitions(rupture);
		assertEquals(2, transitions.size()); // branch jump and one internal bend
		assertTrue(transitions.contains(new SubsectionTransition(sections.get(1), sections.get(2))));
	}

	@Test
	public void testNormalScopeDoesNotDependOnName() {
		FaultSection first = section(10, 30, "Other normal fault", 0, 0, 0, 5);
		FaultSection second = section(11, 30, "Other normal fault", 0, 5, 3, 10);
		ClusterRupture rupture = ClusterRupture.forOrderedSingleStrandRupture(Arrays.asList(first, second), null);
		assertEquals(1, model().getTransitions(rupture).size());
		assertTrue(model().calcRuptureProb(rupture, false) < 1d);
		first.setAveRake(0d);
		assertTrue(model().getTransitions(rupture).isEmpty());
	}

	@Test
	public void testBackgroundForMixedMechanismsAndReplacementForNormal() {
		JumpProbabilityCalc background = new Shaw07JumpDistProb(1d, 0.1d);
		NormalFaultGeometricSegmentationModel model = new NormalFaultGeometricSegmentationModel(
				CoefficientSet.AUTOMATED_5KM, 10d, background);
		ClusterRupture mixed = ruptures.get(4);
		Jump mixedJump = mixed.getJumpsIterable().iterator().next();
		assertSame(mixedJump, model.getTransitions(mixed).get(0));
		assertEquals(background.calcJumpProbability(mixed, mixedJump, false), model.calcRuptureProb(mixed, false), 0d);
		ClusterRupture normal = ruptures.get(2);
		Jump normalJump = normal.getJumpsIterable().iterator().next();
		assertEquals(model().calcRuptureProb(normal, false), model.calcRuptureProb(normal, false), 0d);
		assertTrue(model.calcRuptureProb(normal, false) > background.calcJumpProbability(normal, normalJump, false));
		assertEquals(model.calcRuptureProb(mixed, false), model.calcRuptureProb(mixed.reversed(), false), 0d);
		sections.get(2).setAveRake(0d);
		sections.get(3).setAveRake(0d);
		assertEquals(background.calcJumpProbability(normal, normalJump, false), model.calcRuptureProb(normal, false), 0d);
	}

	@Test
	public void testBackgroundConstraintSerialization() {
		NormalFaultGeometricSegmentationModel model = new NormalFaultGeometricSegmentationModel(
				CoefficientSet.MANUAL_60K, 10d, new Shaw07JumpDistProb(1d, 3d));
		InversionConstraint constraint = new SegmentationProbabilityConstraint(1d, rupSet, model);
		Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class, new InversionConstraint.Adapter(rupSet)).create();
		InversionConstraint restored = gson.fromJson(gson.toJson(constraint, InversionConstraint.class), InversionConstraint.class);
		DoubleMatrix2D expected = encode(constraint, rupSet), actual = encode(restored, rupSet);
		assertEquals(expected.rows(), actual.rows());
		for (int row=0; row<expected.rows(); row++)
			for (int col=0; col<expected.columns(); col++) assertEquals(expected.get(row, col), actual.get(row, col), 0d);
	}

	@Test
	public void testNormalRakeBoundaries() {
		FaultSection section = sections.get(0);
		for (double rake : new double[] {-100d, -90d, -80d}) {
			section.setAveRake(rake);
			assertTrue(NormalFaultGeometricSegmentationModel.isNormal(section));
		}
		for (double rake : new double[] {-101d, -79d, 0d, 90d, Double.NaN}) {
			section.setAveRake(rake);
			assertFalse(NormalFaultGeometricSegmentationModel.isNormal(section));
		}
	}

	private static NormalFaultGeometricSegmentationModel model() {
		return new NormalFaultGeometricSegmentationModel(CoefficientSet.AUTOMATED_5KM, 10d);
	}

	private static DoubleMatrix2D encode(InversionConstraint constraint, FaultSystemRupSet set) {
		DoubleMatrix2D matrix = new SparseDoubleMatrix2D(constraint.getNumRows(), set.getNumRuptures());
		constraint.encode(matrix, new double[matrix.rows()], 0);
		return matrix;
	}

	private static FaultSection section(int id, int parent, String name, double... xy) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setParentSectionId(parent);
		section.setSectionName(name+", subsection "+id);
		section.setParentSectionName(name);
		section.setAveDip(50d);
		section.setAveRake(-90d);
		section.setAveUpperDepth(0d);
		section.setAveLowerDepth(15d);
		section.setAveSlipRate(1d);
		FaultTrace trace = new FaultTrace(name);
		Location origin = new Location(34, -112);
		for (int i=0; i<xy.length; i+=2)
			trace.add(LocationUtils.location(origin, Math.atan2(xy[i], xy[i+1]), Math.hypot(xy[i], xy[i+1])));
		section.setFaultTrace(trace);
		return section;
	}
}
