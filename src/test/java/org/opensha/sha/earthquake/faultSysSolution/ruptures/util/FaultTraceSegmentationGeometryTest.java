package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.SubsectionTransition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometryDiagnostics;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Connector;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Measurements;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.TraceRelation;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class FaultTraceSegmentationGeometryTest {

	private static final Location ORIGIN = new Location(34, -118);
	private static final double REVERSAL_DISTANCE_TOL_KM = 0.01; // endpoint bearings change on reversal
	private static final double DIST_TOL = 0.002; // 2 m, allowing spherical/local-frame differences
	private final FaultTraceSegmentationGeometry geometry = new FaultTraceSegmentationGeometry(10d, 5d);

	@Test
	public void testUsesEndpointStrikeAndSnapshotsSubsectionGeometry() {
		FaultSection first = section(0, 0, 0, 0, 2, 4, 0, 10);
		FaultSection second = section(1, 1, 5, 0, 3, 6, 5, 10);
		Measurements measurement = geometry.measure(first, second);
		assertEquals(first.getFaultTrace().getStrikeDirection(), measurement.first.strikeDegrees, 0d);
		assertEquals(second.getFaultTrace().getStrikeDirection(), measurement.second.strikeDegrees, 0d);
		assertEquals(FaultTraceSegmentationGeometry.calcBendAngle(first, second), measurement.bendAngleDegrees, 0d);
		int originalSize = measurement.first.getTrace().size();
		measurement.first.getTrace().clear();
		first.getFaultTrace().clear();
		assertEquals(originalSize, measurement.first.getTrace().size());
	}

	@Test
	public void testOverlapClippingPreservesDisconnectedTracePieces() {
		FaultSection first = section(0, 0, 0, 0, 0, 10);
		FaultSection second = section(1, 1, 5, -2, 1, 4, 6, -2, 5, 12);
		Measurements measurement = new FaultTraceSegmentationGeometry(10).measure(first, second);
		assertEquals(TraceRelation.OVERLAPPING, measurement.relation);
		assertEquals(1d, measurement.stepoverConnector.lengthKm, DIST_TOL);
		assertEquals(measurement.closestTraceConnector.lengthKm, measurement.stepoverConnector.lengthKm, 1e-8);
		assertOnTrace(measurement.stepoverConnector.firstLocation, first.getFaultTrace());
		assertOnTrace(measurement.stepoverConnector.secondLocation, second.getFaultTrace());
	}

	@Test
	public void testRepeatedVerticesDoNotChangeConnectorLengths() {
		FaultSection first = section(0, 0, 0, 0, 0, 10);
		FaultSection second = section(1, 1, 3, 2, 3, 8);
		Measurements original = geometry.measure(first, second);
		second.getFaultTrace().add(1, second.getFaultTrace().first());
		Measurements repeated = geometry.measure(first, second);
		assertEquals(original.stepoverConnector.lengthKm, repeated.stepoverConnector.lengthKm, 1e-8);
		assertEquals(original.exactPerpendicularConnector.lengthKm, repeated.exactPerpendicularConnector.lengthKm, 1e-8);
	}

	@Test
	public void testAlignedGap() {
		Measurements m = geometry.measure(section(0, 0, 0, 0, 0, 5), section(1, 1, 0, 7, 0, 12));
		assertEquals(TraceRelation.SEPARATED, m.relation);
		assertEquals(2d, m.projectedGapKm, DIST_TOL);
		assertEquals(2d, m.closestEndConnector.lengthKm, DIST_TOL);
		assertEquals(2d, m.closestTraceConnector.lengthKm, DIST_TOL);
		assertEquals(2d, m.facingEndConnector.lengthKm, DIST_TOL);
		assertEquals(2d, m.facingEndGapKm, DIST_TOL);
		assertEquals(0d, m.facingEndOffsetKm, DIST_TOL);
		assertTrue(m.facingEndsBoundProjectedGap);
		assertNull(m.stepoverConnector);
		assertFalse(m.bendTransitionDefined);
	}

	@Test
	public void testParallelOverlappingStepover() {
		Measurements m = geometry.measure(section(0, 0, 0, 0, 0, 10), section(1, 1, 3, 2, 3, 8));
		assertEquals(TraceRelation.OVERLAPPING, m.relation);
		assertEquals(6d, m.overlapLengthKm, DIST_TOL);
		assertEquals(0d, m.projectedGapKm, 0d);
		assertEquals(3d, m.stepoverConnector.lengthKm, DIST_TOL);
		assertEquals(3d, m.exactPerpendicularConnector.lengthKm, DIST_TOL);
		assertEquals(0d, m.exactPerpendicularConnector.alongStrikeKm, 1e-8);
		assertConnector(m.stepoverConnector, 5d);
		assertNull(m.facingEndConnector);
		assertTrue(Double.isNaN(m.facingEndGapKm));
	}

	@Test
	public void testStaggeredGapRetainsBothComponents() {
		Measurements m = geometry.measure(section(0, 0, 0, 0, 0, 5), section(1, 1, 3, 7, 3, 12));
		assertEquals(TraceRelation.SEPARATED, m.relation);
		assertEquals(2d, m.projectedGapKm, DIST_TOL);
		assertEquals(Math.sqrt(13d), m.closestEndConnector.lengthKm, DIST_TOL);
		assertEquals(2d, m.closestEndConnector.alongStrikeKm, DIST_TOL);
		assertEquals(3d, m.closestEndConnector.acrossStrikeKm, DIST_TOL);
		assertEquals(2d, m.facingEndGapKm, DIST_TOL);
		assertEquals(3d, m.facingEndOffsetKm, DIST_TOL);
		assertEquals(Math.sqrt(13d), m.facingEndConnector.lengthKm, DIST_TOL);
		assertTrue(m.facingEndsBoundProjectedGap);
		assertNull(m.stepoverConnector);
	}

	@Test
	public void testFacingEndsAreNotNecessarilyClosestEnds() {
		FaultSection a = section(0, 0, 0, 0, 3, 5), b = section(1, 1, 30, 12, 27, 17);
		Measurements m = geometry.measure(a, b);
		assertEquals(TraceRelation.SEPARATED, m.relation);
		assertEquals(0d, LocationUtils.horzDistance(a.getFaultTrace().last(), m.facingEndConnector.firstLocation), 1e-8);
		assertEquals(0d, LocationUtils.horzDistance(b.getFaultTrace().first(), m.facingEndConnector.secondLocation), 1e-8);
		assertEquals(7d, m.facingEndGapKm, DIST_TOL);
		assertEquals(27d, m.facingEndOffsetKm, DIST_TOL);
		assertTrue(m.closestEndConnector.lengthKm < m.facingEndConnector.lengthKm);
		assertTrue(m.facingEndsBoundProjectedGap);
	}

	@Test
	public void testFoldedTraceKeepsProjectedGapSeparateFromFacingEndGap() {
		Measurements m = geometry.measure(section(0, 0, 0, 0, 2, 6, 0, 4), section(1, 1, 4, 8, 4, 14));
		assertEquals(TraceRelation.SEPARATED, m.relation);
		assertEquals(2d, m.projectedGapKm, DIST_TOL);
		assertEquals(4d, m.facingEndGapKm, DIST_TOL);
		assertEquals(4d, m.facingEndOffsetKm, DIST_TOL);
		assertFalse(m.facingEndsBoundProjectedGap);
		assertTrue(m.closestTraceConnector.lengthKm < m.facingEndConnector.lengthKm);
	}

	@Test
	public void testFacingEndsAreInvariantToPairAndTraceReversal() {
		FaultSection a = section(0, 0, 0, 0, 3, 5), b = section(1, 1, 30, 12, 27, 17);
		Measurements original = geometry.measure(a, b), swapped = geometry.measure(b, a);
		assertEquals(original.facingEndGapKm, swapped.facingEndGapKm, 1e-8);
		assertEquals(original.facingEndOffsetKm, swapped.facingEndOffsetKm, 1e-8);
		assertEquals(0d, LocationUtils.horzDistance(original.facingEndConnector.firstLocation,
				swapped.facingEndConnector.secondLocation), 1e-8);
		Collections.reverse(a.getFaultTrace());
		Measurements reversed = geometry.measure(a, b);
		assertEquals(original.facingEndGapKm, reversed.facingEndGapKm, REVERSAL_DISTANCE_TOL_KM);
		assertEquals(original.facingEndOffsetKm, reversed.facingEndOffsetKm, REVERSAL_DISTANCE_TOL_KM);
		Collections.reverse(b.getFaultTrace());
		reversed = geometry.measure(a, b);
		assertEquals(original.facingEndConnector.lengthKm, reversed.facingEndConnector.lengthKm, 1e-8);
		assertEquals(0d, LocationUtils.horzDistance(original.facingEndConnector.secondLocation,
				reversed.facingEndConnector.secondLocation), 1e-8);
	}

	@Test
	public void testSharedBoundaryHasZeroGapAndDistance() {
		Measurements m = geometry.measure(section(0, 0, 0, 0, 0, 5), section(1, 0, 0, 5, 0, 10));
		assertEquals(TraceRelation.ALONG_STRIKE_TOUCHING, m.relation);
		assertEquals(0d, m.projectedGapKm, 1e-8);
		assertEquals(0d, m.closestTraceConnector.lengthKm, 1e-8);
		assertTrue(Double.isNaN(m.closestTraceConnector.perpendicularDeviationDegrees));
		assertEquals(0d, m.facingEndGapKm, 1e-8);
		assertEquals(0d, m.facingEndOffsetKm, 1e-8);
		assertTrue(m.facingEndsBoundProjectedGap);
		assertNull(m.stepoverConnector);
	}

	@Test
	public void testConstrainedConnectorDiffersFromObliqueClosestConnector() {
		FaultSection a = section(0, 0, 0, 0, 0, 10);
		FaultSection b = section(1, 1, 1, -2, 4, 2, 4, 8);
		Measurements m = geometry.measure(a, b);
		assertEquals(TraceRelation.OVERLAPPING, m.relation);
		assertTrue(m.closestTraceConnector.perpendicularDeviationDegrees > 5d);
		assertTrue(m.stepoverConnector.lengthKm > m.closestTraceConnector.lengthKm);
		assertTrue(m.exactPerpendicularConnector.lengthKm >= m.stepoverConnector.lengthKm-1e-8);
		assertConnector(m.stepoverConnector, 5d);
		assertConnector(m.exactPerpendicularConnector, 0d);
		assertOnTrace(m.stepoverConnector.firstLocation, a.getFaultTrace());
		assertOnTrace(m.stepoverConnector.secondLocation, b.getFaultTrace());
	}

	@Test
	public void testToleranceChangesConstrainedMinimum() {
		FaultSection a = section(0, 0, 0, 0, 0, 10), b = section(1, 1, 1, -2, 4, 2, 4, 8);
		Measurements strict = new FaultTraceSegmentationGeometry(10, 0).measure(a, b);
		Measurements loose = new FaultTraceSegmentationGeometry(10, 15).measure(a, b);
		assertEquals(strict.exactPerpendicularConnector.lengthKm, strict.stepoverConnector.lengthKm, 0d);
		assertTrue(loose.stepoverConnector.lengthKm <= strict.stepoverConnector.lengthKm+1e-8);
		assertConnector(loose.stepoverConnector, 15d);
	}

	@Test
	public void testDefaultDisablesOnlyAngularRestriction() {
		FaultSection a = section(0, 0, 0, 0, 0, 10), b = section(1, 1, 1, -2, 4, 2, 4, 8);
		FaultTraceSegmentationGeometry unrestricted = new FaultTraceSegmentationGeometry(10);
		assertEquals(Double.POSITIVE_INFINITY, unrestricted.getMaxPerpendicularDeviationDegrees(), 0d);
		Measurements m = unrestricted.measure(a, b);
		Measurements explicit = new FaultTraceSegmentationGeometry(10, Double.POSITIVE_INFINITY).measure(a, b);
		Measurements restricted = geometry.measure(a, b);
		assertEquals(m.stepoverConnector.lengthKm, explicit.stepoverConnector.lengthKm, 0d);
		assertTrue(m.stepoverConnector.lengthKm >= m.closestTraceConnector.lengthKm-1e-8);
		assertTrue(m.stepoverConnector.lengthKm <= restricted.stepoverConnector.lengthKm+1e-8);
		assertEquals(restricted.exactPerpendicularConnector.lengthKm, m.exactPerpendicularConnector.lengthKm, 0d);
		assertTrue(m.stepoverConnector.lengthKm > m.closestTraceConnector.lengthKm);
		assertOnTrace(m.stepoverConnector.firstLocation, a.getFaultTrace());
		assertOnTrace(m.stepoverConnector.secondLocation, b.getFaultTrace());
		assertNull(unrestricted.measure(section(2, 0, 0, 0, 0, 5), section(3, 1, 3, 7, 3, 12)).stepoverConnector);
	}

	@Test
	public void testUnlimitedAngleAllowsObliqueConnectorInsideOverlap() {
		FaultSection a = section(0, 0, 0, 0, 2, 4, 0, 10), b = section(1, 1, 5, 0, 3, 6, 5, 10);
		Measurements unrestricted = new FaultTraceSegmentationGeometry(10).measure(a, b);
		Measurements restricted = geometry.measure(a, b);
		assertTrue(unrestricted.stepoverConnector.perpendicularDeviationDegrees > 5d);
		assertEquals(unrestricted.closestTraceConnector.lengthKm, unrestricted.stepoverConnector.lengthKm, 1e-8);
		assertTrue(unrestricted.stepoverConnector.lengthKm < restricted.stepoverConnector.lengthKm);
	}

	@Test
	public void testCrossingTracesAndAmbiguousAxis() {
		Measurements m = geometry.measure(sectionAt(new Location(0, 0), 0, 0, 0, -5, 0, 5),
				sectionAt(new Location(0, 0), 1, 1, -5, 0, 5, 0));
		assertEquals(TraceRelation.AMBIGUOUS_STRIKE_AXIS, m.relation);
		assertEquals(0d, m.closestTraceConnector.lengthKm, 1e-8);
		assertTrue(Double.isNaN(m.projectedGapKm));
		assertTrue(Double.isNaN(m.closestTraceConnector.alongStrikeKm));
		assertNull(m.facingEndConnector);
		assertTrue(Double.isNaN(m.facingEndOffsetKm));
		assertNull(m.stepoverConnector);
	}

	@Test
	public void testIntersectingNonperpendicularTracesHaveZeroConnector() {
		Measurements m = geometry.measure(section(0, 0, 0, -5, 0, 5), section(1, 1, -1, -5, 1, 5));
		assertEquals(TraceRelation.OVERLAPPING, m.relation);
		assertEquals(0d, m.closestTraceConnector.lengthKm, 1e-8);
		assertEquals(0d, m.stepoverConnector.lengthKm, 1e-8);
	}

	@Test
	public void testPairSwapAndBothTraceReversal() {
		FaultSection a = section(0, 0, 0, 0, 0, 10), b = section(1, 1, 3, 2, 4, 8);
		Measurements original = geometry.measure(a, b), swapped = geometry.measure(b, a);
		assertEquals(original.relation, swapped.relation);
		assertEquals(original.bendAngleDegrees, swapped.bendAngleDegrees, 1e-10);
		assertEquals(original.stepoverConnector.lengthKm, swapped.stepoverConnector.lengthKm, 1e-8);
		Collections.reverse(a.getFaultTrace()); Collections.reverse(b.getFaultTrace());
		Measurements reversed = geometry.measure(a, b);
		assertEquals(original.relation, reversed.relation);
		assertEquals(original.stepoverConnector.lengthKm, reversed.stepoverConnector.lengthKm, DIST_TOL);
		assertEquals(original.bendAngleDegrees, reversed.bendAngleDegrees, 0.01);
	}

	@Test
	public void testSingleTraceReversalExposesDirectedStrikeDifference() {
		FaultSection a = section(0, 0, 0, 0, 0, 10), b = section(1, 1, 3, 2, 3, 8);
		double originalAxialDifference = geometry.measure(a, b).axialStrikeDifferenceDegrees;
		Collections.reverse(b.getFaultTrace());
		Measurements m = geometry.measure(a, b);
		assertTrue(m.bendAngleDegrees > 179d);
		assertEquals(originalAxialDifference, m.axialStrikeDifferenceDegrees, 0.01);
		assertEquals(3d, m.stepoverConnector.lengthKm, DIST_TOL);
	}

	@Test
	public void testDepthIgnoredAndAntimeridian() {
		FaultSection a = section(0, 0, 0, 0, 0, 10), b = section(1, 1, 3, 2, 3, 8);
		double distance = geometry.measure(a, b).stepoverConnector.lengthKm;
		for (int i=0; i<b.getFaultTrace().size(); i++) {
			Location p = b.getFaultTrace().get(i);
			b.getFaultTrace().set(i, new Location(p.lat, p.lon, 20d));
		}
		assertEquals(distance, geometry.measure(a, b).stepoverConnector.lengthKm, 1e-10);
		FaultSection c = sectionAt(new Location(10d, 179.99), 2, 0, -2, -5, -2, 5);
		FaultSection d = sectionAt(new Location(10d, 179.99), 3, 1, 2, -5, 2, 5);
		assertEquals(4d, geometry.measure(c, d).stepoverConnector.lengthKm, DIST_TOL);
	}

	@Test
	public void testBendThresholdAndTransitionFiltering() {
		FaultSection a = section(0, 0, 0, 0, 0, 5), b = section(1, 0, 0, 5, 0.4, 10), c = section(2, 0, 0.4, 10, 3.4, 14);
		assertFalse(geometry.isBend(a, b));
		assertTrue(geometry.isBend(b, c));
		double angle = FaultTraceSegmentationGeometry.calcBendAngle(b, c);
		assertTrue(new FaultTraceSegmentationGeometry(angle, 5).isBend(b, c));
		assertFalse(new FaultTraceSegmentationGeometry(Math.nextUp(angle), 5).isBend(b, c));
		ClusterRupture rupture = new ClusterRupture(new FaultSubsectionCluster(Arrays.asList(a, b, c)));
		List<SubsectionTransition> transitions = geometry.getBendTransitions(rupture);
		assertEquals(1, transitions.size());
		assertEquals(new SubsectionTransition(b, c), transitions.get(0));
		assertEquals(new SubsectionTransition(c, b), geometry.getBendTransitions(rupture.reversed()).get(0));
		assertEquals(new SubsectionTransition(b, c), transitions.get(0).reverse().reverse());
	}

	@Test
	public void testBelowThresholdBoundariesRemainInDiagnosticsButNotTransitions() {
		FaultSection a = section(0, 0, 0, 0, 0, 5), b = section(1, 0, 0, 5, 0, 10);
		assertFalse(new FaultTraceSegmentationGeometry(0, 5).isBend(a, b));
		assertFalse(geometry.measure(a, b).bendTransitionDefined);
		assertTrue(geometry.getBendTransitions(new ClusterRupture(new FaultSubsectionCluster(Arrays.asList(a, b)))).isEmpty());
	}

	@Test
	public void testInternalBendsOnSplaysAndNoInterParentBendTransitions() {
		FaultSection a = section(0, 0, 0, 0, 0, 5), b = section(1, 0, 0, 5, 0, 10),
				c = section(2, 0, 0, 10, 0, 15), d = section(3, 1, 3, 5, 3, 10), e = section(4, 1, 3, 10, 6, 14);
		FaultSubsectionCluster root = new FaultSubsectionCluster(Arrays.asList(a, b, c));
		FaultSubsectionCluster branch = new FaultSubsectionCluster(Arrays.asList(d, e));
		ClusterRupture rupture = new ClusterRupture(root).take(new Jump(b, root, d, branch, 3d));
		assertFalse(rupture.splays.isEmpty());
		assertEquals(Arrays.asList(new SubsectionTransition(d, e)), geometry.getBendTransitions(rupture));
	}

	@Test
	public void testDiagnosticRoundTrip() throws Exception {
		File directory = Files.createTempDirectory("segmentation-geometry-test").toFile();
		File csvFile = new File(directory, "geometry.csv"), jsonFile = new File(directory, "geometry.geojson");
		try {
			List<Measurements> measurements = Arrays.asList(
					new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, 0, 0, 10), section(1, 1, 3, 2, 3, 8)),
					geometry.measure(sectionAt(new Location(0, 0), 2, 0, 0, -5, 0, 5),
							sectionAt(new Location(0, 0), 3, 1, -5, 0, 5, 0)),
					geometry.measure(section(4, 0, 0, 0, 0, 5), section(5, 1, 3, 7, 3, 12)));
			FaultTraceSegmentationGeometryDiagnostics.write(csvFile, jsonFile, measurements);
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			assertEquals(16, csv.getNumRows());
			assertEquals(32, csv.getNumCols());
			assertEquals("Infinity", csv.get(1, 18));
			assertEquals("facing_ends", csv.get(15, 19));
			assertEquals(2d, csv.getDouble(15, 29), DIST_TOL);
			assertEquals(3d, csv.getDouble(15, 30), DIST_TOL);
			assertEquals("true", csv.get(15, 31));
			FeatureCollection collection = FeatureCollection.read(jsonFile);
			assertEquals(15, collection.features.size());
			for (Feature feature : collection.features)
				assertFalse(feature.properties.containsValue(Double.NaN));
		} finally {
			Files.deleteIfExists(csvFile.toPath()); Files.deleteIfExists(jsonFile.toPath()); Files.deleteIfExists(directory.toPath());
		}
	}

	@Test
	public void testGeoJSONTracesAreUniqueAndCarryOnlySubsectionProperties() throws Exception {
		File directory = Files.createTempDirectory("segmentation-traces-test").toFile();
		File csvFile = new File(directory, "geometry.csv"), jsonFile = new File(directory, "geometry.geojson");
		try {
			FaultSection a = section(0, 10, 0, 0, 0, 10), b = section(1, 20, 3, 2, 3, 8),
					c = section(2, 30, 6, 12, 6, 17);
			// Reuse traces in multiple pairs and on both sides of a pair.
			FaultTraceSegmentationGeometryDiagnostics.write(csvFile, jsonFile,
					Arrays.asList(geometry.measure(a, b), geometry.measure(c, a), geometry.measure(b, a)));
			Set<Integer> traceIDs = new HashSet<>();
			int connectors = 0;
			for (Feature feature : FeatureCollection.read(jsonFile).features) {
				if ("subsection_trace".equals(feature.properties.get("kind"))) {
					int id = ((Number)feature.properties.get("section_id")).intValue();
					assertTrue("Duplicate trace "+id, traceIDs.add(id));
					assertEquals(id, ((Number)feature.id).intValue());
					assertEquals("Subsection "+id, feature.properties.get("name"));
					assertEquals((id+1)*10, ((Number)feature.properties.get("parent_id")).intValue());
					assertTrue(feature.properties.containsKey("length_km"));
					assertTrue(feature.properties.containsKey("strike_deg"));
					for (String key : feature.properties.keySet())
						assertFalse("Pair property on trace: "+key, key.startsWith("first_") || key.startsWith("second_"));
					assertFalse(feature.properties.containsKey("relation"));
					assertFalse(feature.properties.containsKey("bend_deg"));
				} else {
					connectors++;
					assertTrue(feature.properties.containsKey("first_id"));
					assertTrue(feature.properties.containsKey("second_id"));
					assertTrue(feature.properties.containsKey("first_name"));
					assertTrue(feature.properties.containsKey("second_name"));
					assertTrue(feature.properties.containsKey("relation"));
				}
			}
			assertEquals(new HashSet<>(Arrays.asList(0, 1, 2)), traceIDs);
			assertTrue(connectors > 0);
		} finally {
			Files.deleteIfExists(csvFile.toPath()); Files.deleteIfExists(jsonFile.toPath()); Files.deleteIfExists(directory.toPath());
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectDegenerateTrace() {
		geometry.measure(section(0, 0, 0, 0, 0, 0), section(1, 1, 3, 2, 3, 8));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectClosedTraceWithUndefinedEndpointStrike() {
		geometry.measure(section(0, 0, 0, 0, 1, 2, 0, 0), section(1, 1, 3, 2, 3, 8));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectEmptyTrace() {
		geometry.measure(section(0, 0), section(1, 1, 3, 2, 3, 8));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectSinglePointTrace() {
		geometry.measure(section(0, 0, 0, 0), section(1, 1, 3, 2, 3, 8));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRejectInvalidTolerance() { new FaultTraceSegmentationGeometry(10, 90); }

	@Test(expected=IllegalArgumentException.class)
	public void testRejectNegativeInfinityTolerance() { new FaultTraceSegmentationGeometry(10, Double.NEGATIVE_INFINITY); }

	@Test(expected=IllegalArgumentException.class)
	public void testRejectInvalidBendThreshold() { new FaultTraceSegmentationGeometry(Double.NaN, 5); }

	private static FaultSection section(int id, int parent, double... xy) { return sectionAt(ORIGIN, id, parent, xy); }

	private static FaultSection sectionAt(Location origin, int id, int parent, double... xy) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id); section.setParentSectionId(parent);
		section.setSectionName("Subsection "+id); section.setParentSectionName("Parent "+parent);
		FaultTrace trace = new FaultTrace(section.getSectionName());
		for (int i=0; i<xy.length; i+=2)
			trace.add(LocationUtils.location(origin, Math.atan2(xy[i], xy[i+1]), Math.hypot(xy[i], xy[i+1])));
		section.setFaultTrace(trace);
		return section;
	}

	private static void assertConnector(Connector connector, double tolerance) {
		assertNotNull(connector);
		assertTrue(connector.perpendicularDeviationDegrees <= tolerance+1e-6);
		assertEquals(connector.lengthKm, Math.hypot(connector.alongStrikeKm, connector.acrossStrikeKm), 1e-10);
		assertEquals(connector.lengthKm, connector.horizontalDistanceKm, DIST_TOL);
		assertEquals(0d, connector.firstLocation.depth, 0d);
		assertEquals(0d, connector.secondLocation.depth, 0d);
	}

	private static void assertOnTrace(Location point, FaultTrace trace) {
		double distance = Double.POSITIVE_INFINITY;
		for (int i=1; i<trace.size(); i++)
			distance = Math.min(distance, LocationUtils.distanceToLineSegmentFast(trace.get(i-1), trace.get(i), point));
		assertEquals(0d, distance, DIST_TOL);
	}
}
