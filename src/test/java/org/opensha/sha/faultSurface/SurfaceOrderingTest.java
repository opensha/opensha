package org.opensha.sha.faultSurface;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

/** Physical orientation must not silently change parent-relative indexing. */
public class SurfaceOrderingTest {
	private static final Location ORIGIN = new Location(34, -118);

	private static Location loc(double east, double north, double depth) {
		Location p = LocationUtils.location(ORIGIN, Math.atan2(east, north), Math.hypot(east, north));
		return new Location(p.lat, p.lon, depth);
	}

	private static FaultTrace trace(Location... locations) {
		FaultTrace trace = new FaultTrace("test");
		Collections.addAll(trace, locations);
		return trace;
	}

	private static GeoJSONFaultSection section(int id, FaultTrace upper, FaultTrace lower, FeatureProperties properties) {
		properties.set(GeoJSONFaultSection.FAULT_NAME, "Section " + id);
		properties.set(GeoJSONFaultSection.RAKE, 90d);
		Geometry geometry = lower == null ? new Geometry.LineString(upper)
				: new Geometry.MultiLineString(List.of(upper, lower));
		geometry.setSerializeZeroDepths(true);
		return GeoJSONFaultSection.fromFeature(new Feature(id, geometry, properties));
	}

	private static GeoJSONFaultSection rectangle(int id, int along, int down, boolean left) {
		double north = 3*along, east = (left ? -2 : 2)*Math.max(0, down);
		double depth = 1+2*Math.max(0, down);
		GeoJSONFaultSection section = section(id,
				trace(loc(east, north, depth), loc(east, north+3, depth)),
				trace(loc(east+(left ? -2 : 2), north, depth+2), loc(east+(left ? -2 : 2), north+3, depth+2)),
				new FeatureProperties());
		section.setParentSectionId(10);
		section.setSubSectionIndexAlong(along);
		section.setSubSectionIndexDownDip(down);
		return section;
	}

	private static GeoJSONFaultSection roundTrip(GeoJSONFaultSection section) throws Exception {
		return GeoJSONFaultSection.fromFeature(Feature.fromJSON(section.toFeature().toJSON()));
	}

	@Test public void leftDipPreservesUpperOrderingAndAlignsOnlyLower() {
		FaultTrace upper = trace(loc(0, 0, 1), loc(0, 6, 1));
		FaultTrace lower = trace(loc(-2, 6, 3), loc(-2, 0, 3));
		Location suppliedLowerStart = lower.first();
		ApproxEvenlyGriddedSurface surface = new ApproxEvenlyGriddedSurface(upper, lower, 0.5);
		assertEquals(upper.first(), surface.getUpperEdge().first());
		assertEquals(upper.last(), surface.getUpperEdge().last());
		assertEquals(lower.last(), surface.getLocation(surface.getNumRows()-1, 0));
		assertEquals(suppliedLowerStart, lower.first());
		assertTrue(GriddedSurfaceUtils.isDippingToLeft(surface));
	}

	@Test public void rxFollowsStoredParentDirectionRegardlessOfLocalDipSide() {
		for (boolean left : new boolean[] {false, true}) {
			FaultTrace upper = trace(loc(0, 0, 1), loc(0, 6, 1));
			FaultTrace lower = trace(loc(left ? -2 : 2, 0, 3), loc(left ? -2 : 2, 6, 3));
			ApproxEvenlyGriddedSurface forward = new ApproxEvenlyGriddedSurface(upper, lower, 0.5);
			upper.reverse(); lower.reverse();
			ApproxEvenlyGriddedSurface backward = new ApproxEvenlyGriddedSurface(upper, lower, 0.5);
			for (double north : new double[] {-2, 3, 8}) for (double east : new double[] {-1, 1}) {
				Location site = loc(east, north, 0);
				double rx = forward.getDistanceX(site);
				assertEquals(east > 0, rx > 0);
				assertEquals(-rx, backward.getDistanceX(site), 1e-5);
				assertEquals(forward.getDistanceRup(site), backward.getDistanceRup(site), 1e-5);
				assertEquals(forward.getDistanceJB(site), backward.getDistanceJB(site), 1e-5);
			}
		}
	}

	@Test public void geoJSONAcceptsLeftDippingParentsAndChildren() throws Exception {
		GeoJSONFaultSection parent = rectangle(20, 0, -1, true);
		GeoJSONFaultSection loaded = roundTrip(parent);
		assertEquals(parent.getFaultTrace(), loaded.getFaultTrace());
		assertEquals(parent.getLowerFaultTrace(), loaded.getLowerFaultTrace());
		List<GeoJSONFaultSection> children = parent.getSubSectionsList(0.8, 100, 2);
		for (int i=0; i<children.size(); i++) {
			GeoJSONFaultSection child = children.get(i), reloaded = roundTrip(child);
			assertEquals(i, reloaded.getSubSectionIndexAlong());
			assertEquals(child.getFaultTrace(), reloaded.getFaultTrace());
			assertEquals(child.getLowerFaultTrace(), reloaded.getLowerFaultTrace());
			assertEquals(child.getAveDip(), reloaded.getAveDip(), 0);
			assertEquals(child.getDipDirection(), reloaded.getDipDirection(), 0);
			if (i > 0) {
				assertEquals(children.get(i-1).getFaultTrace().last(), child.getFaultTrace().first());
				assertEquals(children.get(i-1).getLowerFaultTrace().last(), child.getLowerFaultTrace().first());
			}
		}
	}

	@Test public void explicitChildrenRecomputeDirectionButSimpleChildrenInherit() throws Exception {
		FaultTrace curved = trace(loc(0, 0, 1), loc(0, 3, 1), loc(1, 6, 1));
		FeatureProperties props = new FeatureProperties();
		props.set(GeoJSONFaultSection.DIP, 45d);
		props.set(GeoJSONFaultSection.UPPER_DEPTH, 1d);
		props.set(GeoJSONFaultSection.LOW_DEPTH, 3d);
		props.set(GeoJSONFaultSection.DIP_DIR, 123.25f);
		GeoJSONFaultSection simple = section(0, curved, null, props);
		Location previousLowerEnd = null;
		for (GeoJSONFaultSection child : simple.getSubSectionsList(1, 100, 2)) {
			assertEquals(simple.getDipDirection(), child.getDipDirection(), 0);
			assertEquals(simple.getDipDirection(), roundTrip(child).getDipDirection(), 0);
			RuptureSurface surface = child.getFaultSurface(0.1, false, false);
			if (previousLowerEnd != null)
				assertEquals(0, LocationUtils.linearDistanceFast(previousLowerEnd, surface.getFirstLocOnLowerEdge()), 1e-4);
			previousLowerEnd = surface.getLastLocOnLowerEdge();
		}
		GeoJSONFaultSection explicit = section(1, curved,
				trace(loc(-2, 0, 3), loc(-2, 3, 3), loc(-1, 6, 3)), props);
		for (GeoJSONFaultSection child : explicit.getSubSectionsList(1, 100, 2)) {
			assertNotEquals(explicit.getDipDirection(), child.getDipDirection(), 1);
			assertEquals(child.getFaultSurface(1).getAveDipDirection(), child.getDipDirection(), 2e-5);
		}
	}

	@Test public void horizontalAndTriangularPairsRoundTrip() throws Exception {
		FaultTrace upper = trace(loc(0, 0, 1), loc(0, 4, 1));
		for (FaultTrace lower : List.of(trace(loc(2, 0, 1), loc(2, 4, 1)),
				trace(loc(2, 2, 3), loc(2, 2, 3)))) {
			GeoJSONFaultSection section = section(0, upper, lower, new FeatureProperties());
			assertTrue(Double.isFinite(roundTrip(section).getAveDip()));
			assertTrue(Double.isFinite(section.getOrigDownDipWidth()));
			assertTrue(section.getArea(false) > 0);
		}
	}

	@Test public void suppliedNominalSummariesAreRetainedAndDirectionsNormalized() throws Exception {
		FeatureProperties props = new FeatureProperties();
		props.set(GeoJSONFaultSection.UPPER_DEPTH, 0d);
		props.set(GeoJSONFaultSection.LOW_DEPTH, 15d);
		props.set(GeoJSONFaultSection.DIP, 70d);
		props.set(GeoJSONFaultSection.DIP_DIR, -90d);
		GeoJSONFaultSection section = section(0, trace(loc(0, 0, 1), loc(0, 4, 1)),
				trace(loc(-2, 0, 3), loc(-2, 4, 3)), props);
		GeoJSONFaultSection reloaded = roundTrip(section);
		assertEquals(0d, reloaded.getOrigAveUpperDepth(), 0);
		assertEquals(15d, reloaded.getAveLowerDepth(), 0);
		assertEquals(70d, reloaded.getAveDip(), 0);
		assertEquals(270d, reloaded.getDipDirection(), 0);
		props.set(GeoJSONFaultSection.DIP_DIR, Double.POSITIVE_INFINITY);
		assertThrows(IllegalStateException.class, () -> section(0, section.getFaultTrace(), section.getLowerFaultTrace(), props));
	}

	@Test public void serializationRevalidatesEditedGeometry() {
		GeoJSONFaultSection section = rectangle(0, 0, -1, false);
		section.getFaultSurface(1d); // populate the previous geometry's cache
		section.getLowerFaultTrace().set(0, loc(2, 0, 0));
		assertThrows(IllegalStateException.class, section::toFeature);
	}

	@Test public void rxCacheIsInvalidatedAfterNodeEdits() {
		ApproxEvenlyGriddedSurface surface = new ApproxEvenlyGriddedSurface(2, 2, 1);
		for (int c=0; c<2; c++) {
			surface.setLocation(0, c, loc(0, 4*c, 1));
			surface.setLocation(1, c, loc(2, 4*c, 3));
		}
		Location site = loc(1, 2, 0);
		assertTrue(surface.getDistanceX(site) > 0);
		for (int c=0; c<2; c++) surface.setLocation(1, c, loc(-2, 4*c, 3));
		assertTrue(surface.getDistanceX(site) > 0); // local dip does not change the parent's sign
		for (int c=0; c<2; c++) surface.setLocation(0, c, loc(2, 4*c, 1));
		assertTrue(surface.getDistanceX(site) < 0);
	}

	@Test public void pairedDepthCheckRejectsInversionHiddenByMean() {
		FaultTrace upper = trace(loc(0, 0, 2), loc(0, 4, 2));
		FaultTrace lower = trace(loc(2, 0, 1), loc(2, 4, 6));
		assertThrows(IllegalStateException.class, () -> new ApproxEvenlyGriddedSurface(upper, lower, 1));
		assertThrows(IllegalStateException.class, () -> section(0, upper, lower, new FeatureProperties()));
		assertThrows(IllegalStateException.class, () -> section(0, upper, upper, new FeatureProperties()));
	}

	@Test public void missingDepthsAreWrittenUnderCorrectProperties() throws Exception {
		GeoJSONFaultSection section = rectangle(0, 0, -1, false);
		assertEquals(1d, ((Number)section.getProperty(GeoJSONFaultSection.UPPER_DEPTH)).doubleValue(), 1e-12);
		assertEquals(3d, ((Number)section.getProperty(GeoJSONFaultSection.LOW_DEPTH)).doubleValue(), 1e-12);
		assertEquals(section.getAveLowerDepth(), roundTrip(section).getAveLowerDepth(), 0);
	}

	private static CompoundSurface compound(List<GeoJSONFaultSection> sections, boolean downDip) {
		List<RuptureSurface> surfaces = new ArrayList<>();
		for (GeoJSONFaultSection section : sections) surfaces.add(section.getFaultSurface(0.5));
		CompoundSurface compound = CompoundSurface.get(surfaces, sections);
		assertEquals(downDip, compound.hasSurfacesDownDip());
		assertEquals(sections, compound.getSectionsList());
		return compound;
	}

	private static void assertJoinedUpperEdge(CompoundSurface surface) {
		Location previous = null;
		for (int index : surface.getUpperEdgeSurfaceIndexes()) {
			RuptureSurface child = surface.getSurfaceList().get(index);
			Location first = surface.isSurfaceReversed(index) ? child.getLastLocOnUpperEdge() : child.getFirstLocOnUpperEdge();
			Location last = surface.isSurfaceReversed(index) ? child.getFirstLocOnUpperEdge() : child.getLastLocOnUpperEdge();
			if (previous != null) assertEquals(0, LocationUtils.linearDistanceFast(previous, first), 1e-9);
			previous = last;
		}
	}

	@Test public void compoundSeparatesTraversalFromDipSideInBothImplementations() {
		for (boolean downDip : new boolean[] {false, true}) for (boolean allLeft : new boolean[] {false, true}) {
			List<GeoJSONFaultSection> sections = new ArrayList<>();
			for (int r=0; r<(downDip ? 2 : 1); r++) for (int c=0; c<3; c++)
				sections.add(rectangle(r*3+c, c, downDip ? r : -1, allLeft || c == 2));
			CompoundSurface forward = compound(sections, downDip);
			assertJoinedUpperEdge(forward);
			double oriented = 0, area = 0;
			for (RuptureSurface child : forward.getSurfaceList()) {
				double dip = child.getAveDip();
				oriented += child.getArea()*(GriddedSurfaceUtils.isDippingToLeft(child) ? 180-dip : dip);
				area += child.getArea();
			}
			oriented /= area;
			assertEquals(Math.min(oriented, 180-oriented), forward.getAveOrientedDip(), 1e-9);
			Collections.reverse(sections);
			CompoundSurface backward = compound(sections, downDip);
			assertJoinedUpperEdge(backward);
			assertEquals(forward.getAveOrientedDip(), backward.getAveOrientedDip(), 1e-9);
			assertEquals(forward.getAveDip(), backward.getAveDip(), 1e-9);
			for (double east : new double[] {-1, 1}) for (double north : new double[] {1, 4, 7}) {
				Location site = loc(east, north, 0);
				// Even near a left-dipping child, Rx follows the parent's northward trace.
				assertEquals(east > 0, forward.getDistanceX(site) > 0);
				assertEquals(forward.getDistanceRup(site), backward.getDistanceRup(site), 1e-9);
				assertEquals(forward.getDistanceJB(site), backward.getDistanceJB(site), 1e-9);
				assertEquals(forward.getDistanceX(site), backward.getDistanceX(site), 1e-9);
			}
		}
	}

	@Test public void actualEurekaPeakAndTerracedHillsChildrenStayJoined() throws Exception {
		// Boundary coordinates from the initial 2026 variable-depth model. Both
		// exposed ordering problems when children were independently forced to RHR.
		try (var reader = new InputStreamReader(getClass().getResourceAsStream("surface_ordering_regressions.geojson"),
				StandardCharsets.UTF_8)) {
			for (Feature feature : FeatureCollection.read(reader)) {
				GeoJSONFaultSection parent = GeoJSONFaultSection.fromFeature(feature);
				List<GeoJSONFaultSection> children = parent.getSubSectionsList(0.5*parent.getOrigDownDipWidth(), 0, 2);
				for (GeoJSONFaultSection child : children) roundTrip(child);
				assertJoinedUpperEdge(compound(children, false));
				Collections.reverse(children);
				assertJoinedUpperEdge(compound(children, false));
			}
		}
	}

	@Test public void horizontalCompoundsHaveFiniteWidths() {
		for (boolean downDip : new boolean[] {false, true}) {
			List<GeoJSONFaultSection> sections = new ArrayList<>();
			for (int r=0; r<(downDip ? 2 : 1); r++) for (int c=0; c<2; c++) {
				GeoJSONFaultSection section = section(r*2+c,
						trace(loc(2*r, 3*c, 1), loc(2*r, 3*c+3, 1)),
						trace(loc(2*r+2, 3*c, 1), loc(2*r+2, 3*c+3, 1)), new FeatureProperties());
				section.setParentSectionId(10);
				section.setSubSectionIndexAlong(c);
				section.setSubSectionIndexDownDip(downDip ? r : -1);
				sections.add(section);
			}
			CompoundSurface compound = compound(sections, downDip);
			assertEquals(0d, compound.getAveDip(), 0);
			assertTrue(compound.getAveWidth() > 0 && Double.isFinite(compound.getAveWidth()));
			assertTrue(compound.getAveHorizontalWidth() > 0 && Double.isFinite(compound.getAveHorizontalWidth()));
		}
	}
}
