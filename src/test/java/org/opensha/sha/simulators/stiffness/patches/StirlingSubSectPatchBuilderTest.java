package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

public class StirlingSubSectPatchBuilderTest {
	static FaultSectionPrefData section(int id, double length) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setSectionName("Stirling fixture " + id);
		FaultTrace trace = new FaultTrace("upper");
		trace.add(SurfaceTestFixtures.location(0, 0, 5));
		trace.add(SurfaceTestFixtures.location(0, length, 5));
		section.setFaultTrace(trace);
		section.setAveDip(45);
		section.setAveUpperDepth(5);
		section.setAveLowerDepth(5 + 5 * Math.sin(Math.PI / 4));
		section.setAveRake(90);
		return section;
	}

	static SubSectStiffnessCalculator calculator(FaultSectionPrefData section, PatchGenerationStrategy strategy) {
		return new SubSectStiffnessCalculator(List.of(section), 2, 30000, 30000, .5, strategy, 1);
	}

	@Test
	public void preservesLegacyPlacementForBothStrategies() {
		var section = section(0, 7);
		var surface = (StirlingGriddedSurface) section.getFaultSurface(.2, false, false);
		double length = surface.getAveLength(), width = surface.getAveWidth();
		for (PatchGenerationStrategy strategy : new PatchGenerationStrategy[] {
				PatchGenerationStrategy.STIRLING_FILL_OVERLAP, PatchGenerationStrategy.STIRLING_CENTERED }) {
			var patches = calculator(section, strategy).buildPatches(section);
			double[] along = strategy == PatchGenerationStrategy.STIRLING_CENTERED
					? new double[] { length / 6, length / 2, 5 * length / 6 }
					: new double[] { 1, 1 + (length - 2) / 3, 1 + 2 * (length - 2) / 3, length - 1 };
			double[] down = strategy == PatchGenerationStrategy.STIRLING_CENTERED
					? new double[] { width / 4, 3 * width / 4 }
					: new double[] { 1, width / 2, width - 1 };
			assertEquals(along.length * down.length, patches.size());
			int index = 0;
			for (double x : along) {
				for (double y : down) {
					Location expected = surface.getInterpolatedLocation(x, y);
					var patch = patches.get(index++);
					assertEquals(0, LocationUtils.linearDistanceFast(expected, patch.center), 1e-7);
					assertTrue(patch.patch instanceof RectangularPatch);
					assertEquals(4e6, patch.patch.area, 0);
				}
			}
		}
	}

	@Test
	public void firstColumnStrikeIsDefinedForShortSections() {
		var section = section(0, .1);
		for (PatchGenerationStrategy strategy : new PatchGenerationStrategy[] {
				PatchGenerationStrategy.STIRLING_FILL_OVERLAP, PatchGenerationStrategy.STIRLING_CENTERED }) {
			var patches = calculator(section, strategy).buildPatches(section);
			assertFalse(patches.isEmpty());
			for (var patch : patches)
				assertTrue(Double.isFinite(patch.patch.strike));
		}
	}

	@Test
	public void separateStrategiesHaveSeparatePatchesAndCaches() {
		var section = section(0, 7);
		var center = calculator(section, PatchGenerationStrategy.STIRLING_CENTERED);
		var fill = calculator(section, PatchGenerationStrategy.STIRLING_FILL_OVERLAP);
		assertEquals(6, center.getPatches(section).size());
		assertEquals(12, fill.getPatches(section).size());
		var centerCache = center.getAggregationCache(StiffnessType.CFF);
		var fillCache = fill.getAggregationCache(StiffnessType.CFF);
		assertNotEquals(centerCache.getCacheFileName(), fillCache.getCacheFileName());
		assertThrows(IllegalStateException.class, () -> fillCache.copyCacheFrom(centerCache));
	}
}
