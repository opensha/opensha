package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

public class GriddedSurfaceGeometryTest {
	private static void validate(EvenlyGriddedSurface surface) {
		new GriddedSurfaceGeometry(surface, 11, 'S');
	}

	@Test
	public void rejectsFoldedCellsWithEitherWinding() {
		Location a = SurfaceTestFixtures.location(0, 0, 5);
		Location b = SurfaceTestFixtures.location(0, 2, 5);
		Location c = SurfaceTestFixtures.location(2, 2, 7);
		Location d = SurfaceTestFixtures.location(2, 0, 7);
		for (Location[][] nodes : new Location[][][] { { { a, b }, { c, d } }, { { b, a }, { d, c } } })
			assertTrue(assertThrows(IllegalArgumentException.class,
					() -> validate(SurfaceTestFixtures.nodes(nodes))).getMessage().contains("Folded cell"));
	}

	@Test
	public void acceptsCollapsedBoundaryTipsAndRejectsCollapsedInteriors() {
		validate(SurfaceTestFixtures.surface(12, new double[] { 0, 2, 4 }, new double[] { 45, 45 }, 5));
		validate(SurfaceTestFixtures.surface(12, new double[] { 4, 2, 0 }, new double[] { 45, 45 }, 5));
		assertThrows(IllegalArgumentException.class, () -> validate(
				SurfaceTestFixtures.surface(12, new double[] { 4, 0, 4 }, new double[] { 45, 45 }, 5)));
		Location a = SurfaceTestFixtures.location(0, 0, 5);
		Location b = SurfaceTestFixtures.location(0, 2, 5);
		Location c = SurfaceTestFixtures.location(2, 0, 7);
		Location d = SurfaceTestFixtures.location(2, 2, 7);
		assertThrows(IllegalArgumentException.class,
				() -> validate(SurfaceTestFixtures.nodes(new Location[][] { { a, b }, { a, d }, { c, d } })));
		assertThrows(IllegalArgumentException.class,
				() -> validate(SurfaceTestFixtures.nodes(new Location[][] { { a, a }, { a, a } })));
	}

	@Test
	public void rejectsMissingNodesAndInsufficientDimensions() {
		Location a = SurfaceTestFixtures.location(0, 0, 5);
		assertThrows(IllegalArgumentException.class,
				() -> validate(SurfaceTestFixtures.nodes(new Location[][] { { a, a } })));
		assertThrows(IllegalArgumentException.class,
				() -> validate(SurfaceTestFixtures.nodes(new Location[][] { { a }, { a } })));
		assertThrows(NullPointerException.class,
				() -> validate(SurfaceTestFixtures.nodes(new Location[][] { { a, a }, { null, a } })));
	}
}
