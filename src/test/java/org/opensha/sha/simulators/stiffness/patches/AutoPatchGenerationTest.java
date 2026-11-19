package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;
import static org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

public class AutoPatchGenerationTest {
	private static FaultSection approximateSection() {
		FaultTrace upper = new FaultTrace("upper"), lower = new FaultTrace("lower");
		upper.add(SurfaceTestFixtures.location(10, 0, 5));
		upper.add(SurfaceTestFixtures.location(10, 8, 5));
		lower.add(SurfaceTestFixtures.location(14, -2, 9));
		lower.add(SurfaceTestFixtures.location(14, 10, 9));
		return withID(SurfaceTestFixtures.section(new ApproxEvenlyGriddedSurface(upper, lower, 2)), 1);
	}

	private static FaultSection withID(FaultSection section, int id) {
		return (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] { FaultSection.class },
				(proxy, method, args) -> method.getName().equals("getSectionId") ? id : method.invoke(section, args));
	}

	private static SubSectStiffnessCalculator calculator(List<? extends FaultSection> sections,
			PatchGenerationStrategy strategy, double cap) {
		return new SubSectStiffnessCalculator(sections, 2, 30000, 30000, .5, strategy, cap);
	}

	private static void assertSamePatches(List<PatchLocation> expected, List<PatchLocation> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			var a = expected.get(i).patch;
			var b = actual.get(i).patch;
			assertEquals(a.getClass(), b.getClass());
			assertArrayEquals(a.center, b.center, 0);
			assertEquals(a.strike, b.strike, 0);
			assertEquals(a.dip, b.dip, 0);
			assertEquals(a.rake, b.rake, 0);
			assertEquals(a.corner.length, b.corner.length);
			for (int corner = 0; corner < a.corner.length; corner++)
				assertArrayEquals(a.corner[corner], b.corner[corner], 0);
		}
	}

	@Test
	public void omittedAndNullStrategiesDelegateToFillOverlapOrTriangles() {
		var stirling = StirlingSubSectPatchBuilderTest.section(0, 7);
		var interfaceOnly = SurfaceTestFixtures.section(PathologicalSurfaceFixtures.twisted());
		for (FaultSection section : List.of(stirling, interfaceOnly, approximateSection())) {
			var sections = List.of(section);
			var auto = new SubSectStiffnessCalculator(sections, 2, 30000, 30000, .5);
			var nullStrategy = calculator(sections, null, 0);
			var expected = calculator(sections, section == stirling
					? PatchGenerationStrategy.STIRLING_FILL_OVERLAP
					: PatchGenerationStrategy.TRIANGULAR, 0);
			assertEquals(PatchGenerationStrategy.AUTO, auto.getPatchGenerationStrategy());
			assertTrue(auto.isAreaWeighted());
			assertSamePatches(expected.buildPatches(section), auto.buildPatches(section));
			assertSamePatches(expected.buildPatches(section), nullStrategy.buildPatches(section));
		}
	}

	@Test
	public void forcedStrategiesAreHonoredAndBothStirlingModesRejectOtherSurfaces() {
		var stirling = StirlingSubSectPatchBuilderTest.section(0, 7);
		var triangles = calculator(List.of(stirling), PatchGenerationStrategy.TRIANGULAR, 0);
		for (var patch : triangles.buildPatches(stirling))
			assertTrue(patch.patch instanceof TriangularPatch);
		for (var strategy : new PatchGenerationStrategy[] { PatchGenerationStrategy.STIRLING_FILL_OVERLAP,
				PatchGenerationStrategy.STIRLING_CENTERED }) {
			var sections = List.of(stirling, approximateSection());
			var rectangles = calculator(sections, strategy, 0);
			assertFalse(rectangles.isAreaWeighted());
			var error = assertThrows(IllegalStateException.class, () -> rectangles.getPatches(stirling));
			assertTrue(error.getMessage().contains("requires StirlingGriddedSurface"));
		}
	}

	private static boolean isStirling(PatchGenerationStrategy strategy) {
		return strategy == PatchGenerationStrategy.STIRLING_FILL_OVERLAP
				|| strategy == PatchGenerationStrategy.STIRLING_CENTERED;
	}

	private static double aggregate(SubSectStiffnessCalculator calc, FaultSection source, FaultSection receiver,
			AggregationMethod first, AggregationMethod second) {
		return AggregatedStiffnessCalculator.builder(StiffnessType.CFF, calc)
				.process(first).process(second).get().calc(source, receiver);
	}

	@Test
	public void mixedDirectionsUsePhysicalSourceSumsAndReceiverAreasAndRoundTripCaches() throws Exception {
		var sections = List.of(StirlingSubSectPatchBuilderTest.section(0, 7), approximateSection());
		var calc = calculator(sections, PatchGenerationStrategy.AUTO, 0);
		for (int direction = 0; direction < 2; direction++) {
			var source = sections.get(direction);
			var receiver = sections.get(1 - direction);
			var distribution = calc.calcStiffnessDistribution(source, receiver);
			double expectedSum = 0, positive = 0, totalWeight = 0;
			for (int r = 0; r < distribution.receiverPatches.size(); r++) {
				double receiverWeight = distribution.receiverPatches.get(r).patch.area / calc.getReferencePatchArea();
				for (int s = 0; s < distribution.sourcePatches.size(); s++) {
					double stress = distribution.get(StiffnessType.CFF)[r][s];
					double weight = receiverWeight * distribution.sourcePatches.get(s).patch.area
							/ calc.getReferencePatchArea();
					expectedSum += receiverWeight * stress;
					totalWeight += weight;
					if (stress >= 0)
						positive += weight;
				}
			}
			assertEquals(expectedSum, aggregate(calc, source, receiver, SUM, SUM), 1e-11);
			assertEquals(positive / totalWeight, aggregate(calc, source, receiver, FLATTEN, FRACT_POSITIVE), 1e-14);
			var cache = calc.getAggregationCache(StiffnessType.CFF);
			var file = Files.createTempFile("mixed-stiffness-", ".csv");
			try {
				cache.writeCacheFile(file.toFile());
				assertTrue(Files.readString(file).contains("Interaction Weight"));
				cache.clear();
				cache.loadCacheFile(file.toFile());
				assertEquals(expectedSum, aggregate(calc, source, receiver, SUM, SUM), 1e-11);
				assertEquals(positive / totalWeight, aggregate(calc, source, receiver, FLATTEN, FRACT_POSITIVE), 1e-14);
				var legacy = calculator(sections, PatchGenerationStrategy.STIRLING_FILL_OVERLAP, 0);
				assertThrows(java.io.IOException.class,
						() -> legacy.getAggregationCache(StiffnessType.CFF).loadCacheFile(file.toFile()));
			} finally {
				Files.delete(file);
			}
		}
	}

	@Test
	public void allStirlingAutoStatisticsMatchFillOverlapDefinitions() {
		var receiver = StirlingSubSectPatchBuilderTest.section(1, 6.3);
		FaultTrace shifted = new FaultTrace("offset receiver");
		for (var loc : receiver.getFaultTrace())
			shifted.add(new Location(loc.getLatitude(), loc.getLongitude() + .2, loc.getDepth()));
		receiver.setFaultTrace(shifted);
		var sections = List.of(StirlingSubSectPatchBuilderTest.section(0, 7), receiver);
		var stirling = calculator(sections, PatchGenerationStrategy.STIRLING_FILL_OVERLAP, 1);
		var auto = calculator(sections, PatchGenerationStrategy.AUTO, 1);
		for (var first : new AggregationMethod[] { SUM, FLATTEN, MEDIAN, FRACT_POSITIVE }) {
			for (var second : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS) {
				double expected = aggregate(stirling, sections.get(0), sections.get(1), first, second);
				assertEquals(first + "/" + second, expected,
						aggregate(auto, sections.get(0), sections.get(1), first, second),
						1e-12 * Math.max(1, Math.abs(expected)));
			}
		}
	}

	@Test
	public void mixedCffCapsUseSourceToReceiverAreaRatios() {
		var sections = List.of(StirlingSubSectPatchBuilderTest.section(0, 7), approximateSection());
		var calc = calculator(sections, PatchGenerationStrategy.AUTO, 1);
		for (int direction = 0; direction < 2; direction++) {
			var sources = calc.getPatches(sections.get(direction));
			var receivers = calc.getPatches(sections.get(1 - direction));
			double[] self = new double[receivers.size()];
			java.util.Arrays.fill(self, -1e-12);
			var raw = calc.calcStiffnessDistribution(sources, receivers, null).get(StiffnessType.CFF);
			var capped = calc.calcStiffnessDistribution(sources, receivers, self).get(StiffnessType.CFF);
			boolean clipped = false;
			for (int r = 0; r < receivers.size(); r++) {
				for (int s = 0; s < sources.size(); s++) {
					double limit = 1e-12 * sources.get(s).patch.area / receivers.get(r).patch.area;
					assertEquals(Math.max(-limit, Math.min(limit, raw[r][s])), capped[r][s], 1e-24);
					clipped |= Math.abs(raw[r][s]) > limit;
				}
			}
			assertTrue(clipped);
		}
	}

	@Test
	public void cacheNamesAndWeightingDoNotBuildSurfaces() {
		FaultSection delegate = StirlingSubSectPatchBuilderTest.section(0, 7);
		FaultSection section = (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] { FaultSection.class }, (proxy, method, args) -> {
					if (method.getName().equals("getFaultSurface"))
						throw new AssertionError("Cache lookup must not build surfaces");
					return method.invoke(delegate, args);
				});
		var names = new java.util.HashSet<String>();
		for (var strategy : PatchGenerationStrategy.values()) {
			var calc = calculator(List.of(section), strategy, 1);
			assertEquals(!isStirling(strategy), calc.isAreaWeighted());
			String name = calc.getAggregationCache(StiffnessType.CFF).getCacheFileName();
			assertTrue(name.contains("_gen" + strategy + "_v" + strategy.getVersion()));
			assertFalse(name.contains("_geom"));
			assertFalse(name.contains("_align"));
			assertTrue(names.add(name));
		}
		var auto = calculator(List.of(section), PatchGenerationStrategy.AUTO, 1)
				.getAggregationCache(StiffnessType.CFF);
		var triangles = calculator(List.of(section), PatchGenerationStrategy.TRIANGULAR, 1)
				.getAggregationCache(StiffnessType.CFF);
		assertThrows(IllegalStateException.class, () -> auto.copyCacheFrom(triangles));
	}

	@Test
	public void configurationRoundTripsStrategiesAndReadsAlignmentOnlyFiles() throws Exception {
		var sections = List.of(StirlingSubSectPatchBuilderTest.section(0, 7));
		var method = PlausibilityConfiguration.class.getDeclaredMethod("buildGson", List.class);
		method.setAccessible(true);
		var gson = (com.google.gson.Gson) method.invoke(null, sections);
		for (var strategy : PatchGenerationStrategy.values()) {
			var original = calculator(sections, strategy, 1);
			var json = gson.toJsonTree(original).getAsJsonObject();
			assertFalse(json.has("patchAlignment"));
			var restored = gson.fromJson(json, SubSectStiffnessCalculator.class);
			assertEquals(strategy, restored.getPatchGenerationStrategy());
			assertSame(restored, gson.fromJson(json, SubSectStiffnessCalculator.class));
		}
		var object = gson.toJsonTree(calculator(sections, PatchGenerationStrategy.AUTO, 1)).getAsJsonObject();
		object.remove("patchGenerationStrategy");
		assertEquals(PatchGenerationStrategy.AUTO,
				gson.fromJson(object, SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
		object.add("patchGenerationStrategy", com.google.gson.JsonNull.INSTANCE);
		assertEquals(PatchGenerationStrategy.AUTO,
				gson.fromJson(object, SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
		for (boolean centered : new boolean[] { false, true }) {
			object.addProperty("patchAlignment", centered ? "CENTER" : "FILL_OVERLAP");
			var strategy = centered
					? PatchGenerationStrategy.STIRLING_CENTERED
					: PatchGenerationStrategy.STIRLING_FILL_OVERLAP;
			var restored = gson.fromJson(object, SubSectStiffnessCalculator.class);
			assertEquals(strategy, restored.getPatchGenerationStrategy());
			assertSamePatches(calculator(sections, strategy, 1).buildPatches(sections.get(0)),
					restored.buildPatches(sections.get(0)));
			// Explicit current strategies win regardless of JSON field order.
			object.addProperty("patchGenerationStrategy", "TRIANGULAR");
			assertEquals(PatchGenerationStrategy.TRIANGULAR,
					gson.fromJson(object, SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
			object.remove("patchGenerationStrategy");
		}
		object.addProperty("patchGenerationStrategy", "GENERIC");
		assertThrows(com.google.gson.JsonParseException.class,
				() -> gson.fromJson(object, SubSectStiffnessCalculator.class));
		object.addProperty("patchGenerationStrategy", "STIRLING");
		assertThrows(com.google.gson.JsonParseException.class,
				() -> gson.fromJson(object, SubSectStiffnessCalculator.class));
		object.remove("patchGenerationStrategy");
		object.addProperty("patchAlignment", "UNKNOWN");
		assertThrows(com.google.gson.JsonParseException.class,
				() -> gson.fromJson(object, SubSectStiffnessCalculator.class));
	}

	@Test
	public void surfaceRequestsUseCorrectSpacingAndAseismicity() {
		for (var strategy : PatchGenerationStrategy.values()) {
			for (boolean stirling : new boolean[] { false, true }) {
				if (!stirling && isStirling(strategy))
					continue;
				FaultSection delegate = stirling
						? StirlingSubSectPatchBuilderTest.section(0, 7)
						: approximateSection();
				List<Double> requests = new ArrayList<>();
				FaultSection section = (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
						new Class<?>[] { FaultSection.class }, (proxy, method, args) -> {
							if (method.getName().equals("getFaultSurface")) {
								assertEquals(false, args[1]);
								assertEquals(true, args[2]);
								requests.add((double) args[0]);
							}
							return method.invoke(delegate, args);
						});
				var calc = new SubSectStiffnessCalculator(List.of(section), 2, 30000, 30000, .5,
						strategy, 0) {
					@Override
					public boolean isApplyAseisReduction() {
						return true;
					}
				};
				assertFalse(calc.buildPatches(section).isEmpty());
				assertEquals(isStirling(strategy)
						? List.of(.2)
						: stirling && strategy == PatchGenerationStrategy.AUTO ? List.of(2d, .2) : List.of(2d),
						requests);
			}
		}
	}

	@Test
	public void autoRejectsNonGriddedSurfacesClearly() {
		var delegate = StirlingSubSectPatchBuilderTest.section(0, 7);
		var surface = (RuptureSurface) Proxy.newProxyInstance(RuptureSurface.class.getClassLoader(),
				new Class<?>[] { RuptureSurface.class }, (proxy, method, args) -> {
					throw new UnsupportedOperationException(method.getName());
				});
		var section = (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] { FaultSection.class }, (proxy, method,
						args) -> method.getName().equals("getFaultSurface") ? surface : method.invoke(delegate, args));
		var error = assertThrows(IllegalStateException.class,
				() -> calculator(List.of(section), null, 0).buildPatches(section));
		assertTrue(error.getMessage().contains("EvenlyGriddedSurface"));
	}

	@Test
	public void invalidTriangleGeometryReportsSectionIdentity() {
		var a = SurfaceTestFixtures.location(0, 0, 5);
		var b = SurfaceTestFixtures.location(0, 2, 5);
		var c = SurfaceTestFixtures.location(2, 2, 7);
		var d = SurfaceTestFixtures.location(2, 0, 7);
		var surface = SurfaceTestFixtures.nodes(new Location[][] { { a, b }, { c, d } });
		var section = withID(SurfaceTestFixtures.section(surface), 17);
		for (var strategy : new PatchGenerationStrategy[] { PatchGenerationStrategy.AUTO,
				PatchGenerationStrategy.TRIANGULAR }) {
			var calc = calculator(List.of(section), strategy, 0);
			var error = assertThrows(IllegalStateException.class, () -> calc.buildPatches(section));
			assertTrue(error.getMessage().contains("section 17 (synthetic)"));
			assertTrue(error.getMessage().contains("Folded cell"));
		}
	}
}
