package org.opensha.sha.faultSurface;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

/** Analytic local Cartesian fixtures; nominal grid spacing is intentionally unrelated to cell area. */
public class ApproxEvenlyGriddedSurfaceTest {
	private static final Location ORIGIN = new Location(0d, 0d);
	private static final double ANGLE_TOL = 2e-4;

	@FunctionalInterface
	private interface Shape { double[] point(double u, double v); }

	private static Location location(double east, double north, double depth) {
		Location horizontal = LocationUtils.location(ORIGIN, Math.atan2(east, north), Math.hypot(east, north));
		return new Location(horizontal.getLatitude(), horizontal.getLongitude(), depth);
	}

	private static ApproxEvenlyGriddedSurface sample(int rows, int cols, Shape shape) {
		ApproxEvenlyGriddedSurface surface = new ApproxEvenlyGriddedSurface(rows, cols, 123d);
		for (int r=0; r<rows; r++) for (int c=0; c<cols; c++) {
			double[] p = shape.point((double)c/(cols-1), (double)r/(rows-1));
			surface.setLocation(r, c, location(p[0], p[1], p[2]));
		}
		return surface;
	}

	private static ApproxEvenlyGriddedSurface plane(double dip, double direction, double skew) {
		double delta = Math.toRadians(dip), az = Math.toRadians(direction);
		return sample(4, 6, (u,v) -> {
			double along = 2*u+skew*v, down = 2*v*Math.cos(delta);
			return new double[] {along*(-Math.cos(az))+down*Math.sin(az),
					along*Math.sin(az)+down*Math.cos(az), 3+2*v*Math.sin(delta)};
		});
	}

	private static void directionEquals(double expected, double actual, double tolerance) {
		double diff = Math.abs(expected-actual)%360d;
		assertEquals(0d, Math.min(diff, 360d-diff), tolerance);
	}

	@Test public void planarDipsAndDirectionsIncludingSkew() {
		for (double dip : new double[] {0.01, 30, 60, 89.99, 90})
			for (double az : new double[] {0, 1, 90, 179, 270, 359}) {
				var surface = plane(dip, az, 0.7);
				assertEquals(dip, surface.getAveDip(), ANGLE_TOL);
				directionEquals(az, surface.getAveDipDirection(), 0.002);
				directionEquals((az+270d)%360d, surface.getAveStrike(), 0.002);
			}
	}

	@Test public void verticalCurvedFaultStaysVertical() {
		var surface = sample(4, 17, (u,v) -> new double[] {Math.sin(Math.PI*u), 3*u, 2+v*(1+u)});
		assertEquals(90d, surface.getAveDip(), 1e-9);
		assertTrue(surface.getAveDip() <= 90d);
		assertTrue(Double.isFinite(surface.getAveDipDirection()));
	}

	@Test public void verticalTraceConstructorPreservesOrderingAndInputs() {
		FaultTrace upper = new FaultTrace("upper"), lower = new FaultTrace("lower");
		upper.add(location(0, 0, 2)); upper.add(location(0, 2, 2));
		lower.add(location(0, 0, 4)); lower.add(location(0, 2, 4));
		Location firstUpper = upper.first(), firstLower = lower.first();
		var surface = new ApproxEvenlyGriddedSurface(upper, lower, 0.5);
		assertEquals(upper.first(), surface.getUpperEdge().first());
		assertEquals(firstUpper, upper.first());
		assertEquals(firstLower, lower.first());
		assertEquals(90d, surface.getAveDip(), 1e-9);
		directionEquals(90, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void nearlyVerticalNoiseDoesNotFlipDirections() {
		var surface = sample(5, 8, (u,v) -> new double[] {1e-9*v*Math.sin(8*u), 2*u, 2+v});
		assertEquals(90d, surface.getAveDip(), 1e-6);
		directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void horizontalUsesStrikeConventionAtAnyScale() {
		for (double size : new double[] {1e-5, 1, 10}) {
			var surface = sample(3, 4, (u,v) -> new double[] {size*v, size*u, 2});
			assertEquals(0d, surface.getAveDip(), 1e-10);
			directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
		}
	}

	@Test public void opposingDirectionsCancelWithoutCancellingDip() {
		// Two equal-area 45-degree bands dip east and west. Their average
		// normal is vertical, but their mean local dip must remain 45 degrees.
		var surface = new ApproxEvenlyGriddedSurface(3, 2, 1);
		for (int c=0; c<2; c++) {
			surface.setLocation(0, c, location(0, c, 2));
			surface.setLocation(1, c, location(1, c, 3));
			surface.setLocation(2, c, location(0, c, 4));
		}
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
		directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void directionCancellationUsesEdgeConvention() {
		// Vertical cells with opposite winding have equal and opposite normals.
		// This deliberately backtracking trace exercises an undefined resultant.
		var surface = new ApproxEvenlyGriddedSurface(2, 3, 1);
		for (int r=0; r<2; r++) {
			surface.setLocation(r, 0, location(0, 0, 2+r));
			surface.setLocation(r, 1, location(0, 1, 2+r));
			surface.setLocation(r, 2, location(0, 0, 2+r));
		}
		assertEquals(90d, surface.getAveDip(), 1e-9);
		double expected = (surface.getRowAsTrace(0).getAveStrike()+90d)%360d;
		directionEquals(expected, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void missingGridLocationsFailClearly() {
		var surface = new ApproxEvenlyGriddedSurface(2, 2, 1);
		surface.setLocation(0, 0, location(0, 0, 2));
		assertThrows(IllegalStateException.class, surface::getAveDip);
	}

	@Test public void areaWeightedListricDipUsesAllRows() {
		var surface = new ApproxEvenlyGriddedSurface(3, 2, 99);
		for (int c=0; c<2; c++) {
			surface.setLocation(0, c, location(0, c, 2));
			surface.setLocation(1, c, location(2*Math.cos(Math.PI/6), c, 3));
			surface.setLocation(2, c, location(2*Math.cos(Math.PI/6)+0.5, c, 3+Math.sin(Math.PI/3)));
		}
		assertEquals(40d, surface.getAveDip(), ANGLE_TOL); // (2*30 + 1*60)/3
		directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void unequalAlongStrikeCellsUsePhysicalArea() {
		var surface = new ApproxEvenlyGriddedSurface(2, 3, 1);
		double[] north = {0, 1, 4}, depth = {2, 3, 3};
		for (int c=0; c<3; c++) {
			surface.setLocation(0, c, location(0, north[c], 2));
			surface.setLocation(1, c, location(1, north[c], depth[c]));
		}
		// First cell has center normal (0.5,-0.5,1), area sqrt(1.5).
		// Second is a 45-degree cell with three times the along-strike length.
		double a = Math.sqrt(1.5), b = 3*Math.sqrt(2);
		double expected = (a*Math.toDegrees(Math.atan(Math.sqrt(0.5)))+b*45)/(a+b);
		assertEquals(expected, surface.getAveDip(), ANGLE_TOL);
	}

	@Test public void centerOfWarpedCellHasAnalyticOrientation() {
		var surface = sample(2, 2, (u,v) -> new double[] {v, u, 2+2*u*v});
		assertEquals(Math.toDegrees(Math.atan(Math.sqrt(2))), surface.getAveDip(), ANGLE_TOL);
		directionEquals(45d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void warpedGridRefinementConvergesToAreaIntegral() {
		double weighted = 0, area = 0;
		for (int i=0; i<400; i++) for (int j=0; j<400; j++) {
			double h = 2*Math.hypot((i+0.5)/400, (j+0.5)/400), a = Math.sqrt(1+h*h);
			weighted += a*Math.toDegrees(Math.atan(h)); area += a;
		}
		double expected = weighted/area, previousError = Double.POSITIVE_INFINITY;
		for (int n : new int[] {4, 8, 16, 32}) {
			var surface = sample(n+1, n+1, (u,v) -> new double[] {v, u, 2+2*u*v});
			double error = Math.abs(surface.getAveDip()-expected);
			assertTrue(error < previousError); previousError = error;
			directionEquals(45d, surface.getAveDipDirection(), ANGLE_TOL);
		}
		assertTrue(previousError < 0.01);
	}

	@Test public void triangularTipAndCollapsedCellsContributeTheirArea() {
		var triangle = sample(3, 4, (u,v) -> new double[] {v, 2*u*v, 2+v});
		assertEquals(45d, triangle.getAveDip(), ANGLE_TOL);
		directionEquals(90d, triangle.getAveDipDirection(), ANGLE_TOL);
		var surface = new ApproxEvenlyGriddedSurface(3, 2, 1);
		for (int c=0; c<2; c++) {
			surface.setLocation(0, c, location(0, c, 2));
			surface.setLocation(1, c, location(0, c, 2));
			surface.setLocation(2, c, location(1, c, 3));
		}
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
	}

	@Test public void zeroAreaAndOneRowFailClearly() {
		var collapsed = sample(2, 3, (u,v) -> new double[] {0, u, 2});
		assertThrows(IllegalStateException.class, collapsed::getAveDip);
		assertThrows(IllegalStateException.class, collapsed::getAveDipDirection);
		var line = new ApproxEvenlyGriddedSurface(1, 2, 1);
		line.setLocation(0, 0, location(0, 0, 2)); line.setLocation(0, 1, location(0, 1, 2));
		assertThrows(IllegalStateException.class, line::getAveDip);
	}

	@Test public void tinyTraceSurfaceRetainsBothBoundaries() {
		FaultTrace upper = new FaultTrace("upper"), lower = new FaultTrace("lower");
		upper.add(location(0, 0, 2)); upper.add(location(0, 0.1, 2));
		lower.add(location(0.1, 0, 2.1)); lower.add(location(0.1, 0.1, 2.1));
		var surface = new ApproxEvenlyGriddedSurface(upper, lower, 10);
		assertEquals(2, surface.getNumRows()); assertEquals(2, surface.getNumCols());
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
		directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void nodeEditsInvalidateBothSummaries() {
		var surface = sample(2, 2, (u,v) -> new double[] {v, u, 2+v});
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
		assertEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
		for (int c=0; c<2; c++) surface.set(1, c, location(1, c, 2));
		assertEquals(0d, surface.getAveDip(), ANGLE_TOL);
		for (int c=0; c<2; c++) surface.setLocation(1, c, location(-1, c, 3));
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
		directionEquals(270d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void endpointDipsDoNotHideInteriorDepth() {
		var surface = sample(2, 5, (u,v) -> new double[] {v, 2*u, 2+v*Math.sin(Math.PI*u)});
		assertTrue(surface.getAveDip() > 20);
		assertTrue(surface.getAveDip() < 60);
	}

	@Test public void horizontalCollapsedUpperEdgeUsesLowerStrike() {
		var surface = sample(2, 3, (u,v) -> new double[] {v, u*v, 2});
		assertEquals(0d, surface.getAveDip(), ANGLE_TOL);
		directionEquals(90d, surface.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void columnReversalPreservesNonverticalPlaneDirection() {
		var source = plane(35, 280, 0.3);
		var reversed = new ApproxEvenlyGriddedSurface(source.getNumRows(), source.getNumCols(), 1);
		for (int r=0; r<source.getNumRows(); r++) for (int c=0; c<source.getNumCols(); c++)
			reversed.setLocation(r, c, source.getLocation(r, source.getNumCols()-1-c));
		assertEquals(source.getAveDip(), reversed.getAveDip(), ANGLE_TOL);
		directionEquals(source.getAveDipDirection(), reversed.getAveDipDirection(), ANGLE_TOL);
	}

	@Test public void directionGetterAlsoInitializesDip() {
		var surface = plane(30, 135, 0);
		directionEquals(135, surface.getAveDipDirection(), ANGLE_TOL);
		assertEquals(30, surface.getAveDip(), ANGLE_TOL);
	}

	@Test public void invalidGridSpacingFailsEarly() {
		for (double spacing : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
			assertThrows(IllegalArgumentException.class, () -> new ApproxEvenlyGriddedSurface(2, 2, spacing));
	}

	@Test public void directionsAcrossNorthUseVectorAverage() {
		var surface = new ApproxEvenlyGriddedSurface(2, 3, 1);
		for (int r=0; r<2; r++) for (int c=0; c<3; c++)
			surface.setLocation(r, c, location(-c, r, 2+r+(c == 1 ? 0.1 : 0)));
		directionEquals(0d, surface.getAveDipDirection(), ANGLE_TOL);
		assertEquals(Math.toDegrees(Math.atan(Math.sqrt(1.01))), surface.getAveDip(), ANGLE_TOL);
	}

	@Test public void aseismicReductionStillRetainsTinySurface() {
		FaultTrace upper = new FaultTrace("upper"), lower = new FaultTrace("lower");
		upper.add(location(0, 0, 2)); upper.add(location(0, 0.1, 2));
		lower.add(location(0.1, 0, 2.1)); lower.add(location(0.1, 0.1, 2.1));
		var surface = new ApproxEvenlyGriddedSurface(upper, lower, 10, true, 0.5);
		assertEquals(2, surface.getNumRows()); assertEquals(2, surface.getNumCols());
		assertEquals(2.05, surface.getLocation(0, 0).getDepth(), 1e-10);
		assertEquals(2.1, surface.getLocation(1, 0).getDepth(), 1e-10);
		assertEquals(45d, surface.getAveDip(), ANGLE_TOL);
	}

	@Test public void strikeUsesInteriorGeometryAndSharesFallback() {
		// Upper edge points north, but along-strike depth variation tilts the
		// plane so its geometric strike is substantially different from north.
		var tilted = sample(3, 4, (u,v) -> new double[] {v, u, 2+u+v});
		directionEquals(315d, tilted.getAveStrike(), ANGLE_TOL);
		directionEquals(tilted.getAveStrike()+90d, tilted.getAveDipDirection(), ANGLE_TOL);
		var horizontal = sample(2, 2, (u,v) -> new double[] {v, u, 2});
		directionEquals(horizontal.getRowAsTrace(0).getAveStrike(), horizontal.getAveStrike(), ANGLE_TOL);
	}

	@Test public void cachedStrikeChangesWhenNodesChange() {
		var surface = sample(2, 2, (u,v) -> new double[] {v, u, 2+v});
		directionEquals(0d, surface.getAveStrike(), ANGLE_TOL);
		for (int r=0; r<2; r++) surface.setLocation(r, 1, location(r, 1, 3+r));
		directionEquals(315d, surface.getAveStrike(), ANGLE_TOL);
	}

	@Test public void geographicWrapAndHighLatitudeRemainFinite() {
		for (Location origin : new Location[] {new Location(0, 179.999), new Location(80, -120)}) {
			var surface = new ApproxEvenlyGriddedSurface(2, 2, 1);
			for (int r=0; r<2; r++) for (int c=0; c<2; c++) {
				Location p = LocationUtils.location(origin, Math.atan2(r, c), Math.hypot(r, c));
				surface.setLocation(r, c, new Location(p.getLatitude(), p.getLongitude(), 2+r));
			}
			assertEquals(45d, surface.getAveDip(), 0.1);
			directionEquals(90d, surface.getAveDipDirection(), 0.1);
		}
	}
}
