package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensha.sha.simulators.stiffness.patches.RectangularPatch;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;
import org.opensha.sha.simulators.stiffness.patches.PatchLocation;
import org.opensha.sha.simulators.stiffness.patches.StiffnessPatch;
import org.opensha.sha.simulators.stiffness.patches.TriangularPatch;

public class SelfStiffnessCapTest {
	private static final double LAMBDA = 30000, MU = 30000, FRICTION = .5;

	private static FaultSection section(int id) {
		var section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setSectionName("cap test " + id);
		FaultTrace trace = new FaultTrace(section.getSectionName());
		trace.add(new Location(34, -118, 5));
		trace.add(new Location(34.1, -118, 5));
		section.setFaultTrace(trace);
		section.setAveDip(45);
		section.setAveRake(0);
		section.setAveUpperDepth(5);
		section.setAveLowerDepth(10);
		section.setDipDirection(90);
		return section;
	}

	private static PatchLocation location(StiffnessPatch patch) {
		return new PatchLocation(patch, new Location(34, -118, -patch.center[2] / 1000),
				new Location[patch.corner.length]);
	}

	private static PatchLocation triangle(double area, double[] center, double rake) {
		double side = Math.sqrt(2 * area), x = center[0] - side / 3, y = center[1] - side / 3, z = center[2];
		return location(
				new TriangularPatch(new double[][] { { x, y, z }, { x + side, y, z }, { x, y + side, z } }, rake, 0));
	}

	private static double rawCFF(PatchLocation source, PatchLocation receiver) {
		double[] stress = StiffnessCalc.calcStiffness(LAMBDA, MU, source.patch, receiver.patch);
		return StiffnessCalc.calcCoulombStress(stress[1], stress[0], FRICTION);
	}

	/** Real kernels with controlled patch placement; no geometry projection involved. */
	private static final class Calculator extends SubSectStiffnessCalculator {
		final List<PatchLocation> source, receiver;
		int selfCalculations;
		Calculator(double cap, PatchGenerationStrategy strategy,
				List<PatchLocation> source, List<PatchLocation> receiver) {
			super(List.of(section(0), section(1)), 2, LAMBDA, MU, FRICTION, strategy, cap);
			this.source = source;
			this.receiver = receiver;
		}
		@Override
		public List<PatchLocation> buildPatches(FaultSection section) {
			return section.getSectionId() == 0 ? source : receiver;
		}
		@Override
		public double[] calcSelfStiffness(List<PatchLocation> patches) {
			selfCalculations++;
			return super.calcSelfStiffness(patches);
		}
	}

	@Test
	public void unequalAreasClipBothSignsWithoutChangingSigmaOrTau() {
		var receiver = triangle(2e6, new double[] { 9000, 12000, -7000 }, 0);
		boolean positive = false, negative = false;
		for (double ratio : new double[] { .25, 1, 4 })
			for (double rake : new double[] { 0, 180 }) {
				var source = triangle(receiver.patch.area * ratio, new double[] { 0, 0, -5000 }, rake);
				var calc = new Calculator(.75, PatchGenerationStrategy.TRIANGULAR, List.of(source), List.of(receiver));
				var raw = calc.calcStiffnessDistribution(calc.source, calc.receiver, null);
				var capped = calc.calcStiffnessDistribution(calc.source, calc.receiver, new double[] { -1e-10 });
				double cff = raw.get(StiffnessType.CFF)[0][0];
				double limit = .75e-10 * ratio;
				assertTrue("Expected active clipping", Math.abs(cff) > limit);
				positive |= cff > 0;
				negative |= cff < 0;
				assertEquals(Math.copySign(limit, cff), capped.get(StiffnessType.CFF)[0][0], 1e-24);
				for (var type : new StiffnessType[] { StiffnessType.SIGMA, StiffnessType.TAU })
					assertEquals(raw.get(type)[0][0], capped.get(type)[0][0], 0);
			}
		assertTrue("Both clipping directions must be exercised", positive && negative);
	}

	@Test
	public void subdividingSourceSharesItsClippingAllowance() {
		var parent = triangle(2e6, new double[] { 0, 0, -5000 }, 0);
		double[][] v = parent.patch.corner, mid = new double[3][3];
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				mid[i][j] = (v[i][j] + v[(i + 1) % 3][j]) / 2;
		List<PatchLocation> children = new ArrayList<>();
		for (double[][] vertices : new double[][][] { { v[0], mid[0], mid[2] }, { mid[0], v[1], mid[1] },
				{ mid[2], mid[1], v[2] }, { mid[0], mid[1], mid[2] } })
			children.add(location(new TriangularPatch(vertices, 0, 0)));
		var receiver = triangle(2e6, new double[] { 9000, 12000, -7000 }, 0);
		var calc = new Calculator(1, PatchGenerationStrategy.TRIANGULAR, children, List.of(receiver));
		double parentRaw = rawCFF(parent, receiver), childRaw = 0;
		double parentCap = calc.calcStiffnessDistribution(List.of(parent), calc.receiver, new double[] { -1e-10 })
				.get(StiffnessType.CFF)[0][0];
		double[] childCaps = calc.calcStiffnessDistribution(children, calc.receiver, new double[] { -1e-10 })
				.get(StiffnessType.CFF)[0];
		double total = 0;
		for (int i = 0; i < children.size(); i++) {
			double raw = rawCFF(children.get(i), receiver);
			assertTrue("Same-sign far-field contributions must all saturate",
					raw * parentRaw > 0 && Math.abs(raw) > 1e-10);
			childRaw += raw;
			total += childCaps[i];
			assertEquals(Math.abs(parentCap) / 4, Math.abs(childCaps[i]), 1e-23);
		}
		assertEquals("Uncapped source superposition", parentRaw, childRaw, 1e-12);
		assertEquals("Fixed receiver and saturated same-sign sources", parentCap, total, 1e-23);
	}

	@Test
	public void rectangularLimitsRetainTheirOriginalBehavior() {
		var receiver = location(new RectangularPatch(new double[] { 9000, 12000, -7000 }, 2000, 2000,
				new FocalMechanism(0, 45, 0)));
		for (double side : new double[] { 1000, 2000, 4000 })
			for (double rake : new double[] { 0, 180 }) {
				var source = location(new RectangularPatch(new double[] { 0, 0, -5000 }, side, side,
						new FocalMechanism(0, 45, rake)));
				var calc = new Calculator(.75, PatchGenerationStrategy.STIRLING_FILL_OVERLAP, List.of(source), List.of(receiver));
				double raw = rawCFF(source, receiver);
				assertTrue(Math.abs(raw) > .75e-10);
				double actual = calc.calcStiffnessDistribution(calc.source, calc.receiver, new double[] { -1e-10 })
						.get(StiffnessType.CFF)[0][0];
				assertEquals(Math.copySign(.75e-10, raw), actual, 0);
			}
	}

	@Test
	public void disabledCapDoesNotCalculateSelfStiffness() {
		var source = triangle(1e6, new double[] { 0, 0, -5000 }, 0);
		var receiver = triangle(2e6, new double[] { 200, 300, -6000 }, 0);
		var calc = new Calculator(0, PatchGenerationStrategy.TRIANGULAR, List.of(source), List.of(receiver));
		var result = calc.calcStiffnessDistribution(0, 1);
		assertEquals(rawCFF(source, receiver), result.get(StiffnessType.CFF)[0][0], 0);
		assertEquals(0, calc.selfCalculations);
	}

	@Test
	public void cappedAggregationAndCacheRoundTripUsePhysicalStressOnce() throws Exception {
		var sources = List.of(triangle(1e6, new double[] { 0, 0, -5000 }, 0),
				triangle(3e6, new double[] { 0, 0, -5000 }, 180));
		var receivers = List.of(triangle(2e6, new double[] { 200, 300, -6000 }, 0),
				triangle(1e6, new double[] { 300, 100, -6000 }, 0));
		var calc = new Calculator(.01, PatchGenerationStrategy.TRIANGULAR, sources, receivers);
		double expected = 0;
		int clipped = 0;
		for (var receiver : receivers) {
			double self = Math.abs(rawCFF(receiver, receiver)), net = 0;
			for (var source : sources) {
				double raw = rawCFF(source, receiver), limit = .01 * self * source.patch.area / receiver.patch.area;
				if (Math.abs(raw) > limit)
					clipped++;
				net += Math.max(-limit, Math.min(limit, raw));
			}
			expected += net * receiver.patch.area / calc.getReferencePatchArea();
		}
		assertTrue("The integration test must exercise clipping", clipped > 0);
		var sum = AggregatedStiffnessCalculator.builder(StiffnessType.CFF, calc)
				.process(AggregationMethod.SUM).process(AggregationMethod.SUM).get();
		assertEquals(expected, sum.calc(calc.getSubSects().get(0), calc.getSubSects().get(1)), 1e-12);
		assertEquals(1, calc.selfCalculations);
		var cache = calc.getAggregationCache(StiffnessType.CFF);
		var file = Files.createTempFile("triangle-capped-stiffness-", ".csv");
		try {
			cache.writeCacheFile(file.toFile());
			cache.clear();
			cache.loadCacheFile(file.toFile());
			assertEquals(expected, sum.calc(calc.getSubSects().get(0), calc.getSubSects().get(1)), 1e-12);
			assertEquals("Cache hit must not recompute self-stiffness", 1, calc.selfCalculations);
			assertNotEquals(cache.getCacheFileName(),
					new Calculator(0, PatchGenerationStrategy.TRIANGULAR, sources, receivers)
							.getAggregationCache(StiffnessType.CFF).getCacheFileName());
		} finally {
			Files.deleteIfExists(file);
		}
		// Stored plausibility configurations must be able to reconstruct a capped triangle calculator.
		var config = new PlausibilityConfiguration(List.of(), 0, null,
				new SectionDistanceAzimuthCalculator(calc.getSubSects()));
		var gson = config.buildGson();
		var restored = gson.fromJson(gson.toJson(calc, SubSectStiffnessCalculator.class),
				SubSectStiffnessCalculator.class);
		assertEquals(PatchGenerationStrategy.TRIANGULAR, restored.getPatchGenerationStrategy());
		assertEquals(.01, restored.getSelfStiffnessCap(), 0);
		assertEquals(cache.getCacheFileName(), restored.getAggregationCache(StiffnessType.CFF).getCacheFileName());
		assertArrayEquals(
				calc.calcStiffnessDistribution(sources, receivers, new double[] { -1, -1 }).get(StiffnessType.CFF)[0],
				restored.calcStiffnessDistribution(sources, receivers, new double[] { -1, -1 })
						.get(StiffnessType.CFF)[0],
				0);
	}

	@Test
	public void challengingSelfStiffnessAgreesWithNearbyNormalLimits() {
		var calc = new Calculator(1, PatchGenerationStrategy.TRIANGULAR, List.of(), List.of());
		for (double dip : new double[] { 0, 45, 89.99, 90 })
			for (double rake : new double[] { -90, 0, 90, 180 })
				for (double aspect : new double[] { 1, 100, 10000 }) {
					var rectangle = new RectangularPatch(new double[] { 0, 0, -5000 }, 2000, 2000 / aspect,
							new FocalMechanism(20, dip, rake));
					var patch = location(new TriangularPatch(
							new double[][] { rectangle.corner[0], rectangle.corner[1], rectangle.corner[2] }, rake,
							20));
					double self = calc.calcSelfStiffness(List.of(patch))[0];
					assertTrue("Negative, finite stress drop", Double.isFinite(self) && self < 0);
					for (double side : new double[] { -1, 1 }) {
						double[] center = patch.patch.center.clone();
						for (int i = 0; i < 3; i++)
							center[i] += side * 1e-5 * 2000 / aspect * patch.patch.nu[i];
						var nearby = location(new RectangularPatch(center, 1, 1, new FocalMechanism(
								Math.toDegrees(patch.patch.strike), Math.toDegrees(patch.patch.dip), rake)));
						assertEquals("Dip/rake/aspect/side=" + dip + "/" + rake + "/" + aspect + "/" + side,
								self, rawCFF(patch, nearby), 1e-8 + 1e-5 * Math.abs(self));
					}
				}
	}
}
