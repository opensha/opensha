package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Connector;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Measurements;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.SectionGeometry;

/** CSV and GeoJSON export for subsection segmentation geometry diagnostics. */
public final class FaultTraceSegmentationGeometryDiagnostics {

	private static final int NUM_CONNECTOR_COLUMNS = 9;

	private FaultTraceSegmentationGeometryDiagnostics() {}

	/**
	 * Writes one CSV row per connector alternative, including missing alternatives.
	 * GeoJSON contains unique subsection traces with their own properties and
	 * connector features with pair-specific properties. Undefined values are blank
	 * in CSV and omitted in GeoJSON. No probability rules are applied.
	 */
	public static void write(File csvFile, File geoJSONFile, List<Measurements> measurements) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("first_id", "second_id", "first_parent", "second_parent", "first_name", "second_name",
				"first_length_km", "second_length_km", "first_strike_deg", "second_strike_deg", "bend_deg",
				"axial_difference_deg", "mean_strike_axis_deg", "relation", "overlap_km", "projected_gap_km",
				"min_bend_deg", "bend_transition_defined", "perpendicular_tolerance_deg", "connector_type",
				"length_km", "horizontal_distance_km", "along_km", "across_km", "perpendicular_deviation_deg",
				"first_lat", "first_lon", "second_lat", "second_lon", "facing_end_gap_km", "facing_end_offset_km",
				"facing_ends_bound_projected_gap");
		List<Feature> features = new ArrayList<>();
		Map<Integer, Feature> traces = new TreeMap<>();
		for (Measurements measurement : measurements) {
			for (SectionGeometry section : Arrays.asList(measurement.first, measurement.second)) {
				if (!traces.containsKey(section.sectionID))
					traces.put(section.sectionID, traceFeature(section));
			}
			FeatureProperties pairProperties = pairProperties(measurement);
			for (ConnectorType type : ConnectorType.values()) {
				Connector connector = type.getConnector(measurement);
				List<String> row = pairColumns(measurement);
				row.add(type.kind);
				if (connector == null) {
					for (int column=0; column<NUM_CONNECTOR_COLUMNS; column++)
						row.add("");
				} else {
					row.addAll(Arrays.asList(number(connector.lengthKm), number(connector.horizontalDistanceKm),
							number(connector.alongStrikeKm), number(connector.acrossStrikeKm),
							number(connector.perpendicularDeviationDegrees), number(connector.firstLocation.lat),
							number(connector.firstLocation.lon), number(connector.secondLocation.lat),
							number(connector.secondLocation.lon)));
					features.add(connectorFeature(pairProperties, type, connector));
				}
				row.add(number(measurement.facingEndGapKm));
				row.add(number(measurement.facingEndOffsetKm));
				String boundsProjectedGap = "";
				if (measurement.facingEndConnector != null)
					boundsProjectedGap = Boolean.toString(measurement.facingEndsBoundProjectedGap);
				row.add(boundsProjectedGap);
				csv.addLine(row);
			}
		}
		csv.writeToFile(csvFile);
		features.addAll(0, traces.values());
		FeatureCollection.write(new FeatureCollection(features), geoJSONFile);
	}

	private static Feature traceFeature(SectionGeometry section) {
		FeatureProperties properties = new FeatureProperties();
		properties.put("kind", "subsection_trace");
		properties.put("section_id", section.sectionID);
		properties.put("name", section.name);
		properties.put("parent_id", section.parentID);
		properties.put("length_km", section.lengthKm);
		properties.put("strike_deg", section.strikeDegrees);
		properties.put("stroke", "#333333");
		properties.put("stroke-width", 2);
		return new Feature(section.sectionID, new Geometry.LineString(section.getTrace()), properties);
	}

	private static Feature connectorFeature(FeatureProperties pairProperties, ConnectorType type, Connector connector) {
		FeatureProperties properties = new FeatureProperties(pairProperties);
		properties.put("kind", type.kind);
		properties.put("stroke", type.color);
		properties.put("length_km", connector.lengthKm);
		properties.put("horizontal_distance_km", connector.horizontalDistanceKm);
		putFinite(properties, "along_km", connector.alongStrikeKm);
		putFinite(properties, "across_km", connector.acrossStrikeKm);
		putFinite(properties, "perpendicular_deviation_deg", connector.perpendicularDeviationDegrees);
		return new Feature(new Geometry.LineString(connector.firstLocation, connector.secondLocation), properties);
	}

	private static List<String> pairColumns(Measurements measurement) {
		return new ArrayList<>(Arrays.asList(Integer.toString(measurement.first.sectionID),
				Integer.toString(measurement.second.sectionID), Integer.toString(measurement.first.parentID),
				Integer.toString(measurement.second.parentID), measurement.first.name, measurement.second.name,
				number(measurement.first.lengthKm), number(measurement.second.lengthKm),
				number(measurement.first.strikeDegrees), number(measurement.second.strikeDegrees),
				number(measurement.bendAngleDegrees), number(measurement.axialStrikeDifferenceDegrees),
				number(measurement.averageStrikeAxisDegrees), measurement.relation.name(),
				number(measurement.overlapLengthKm), number(measurement.projectedGapKm),
				number(measurement.minBendAngleDegrees), Boolean.toString(measurement.bendTransitionDefined),
				Double.toString(measurement.maxPerpendicularDeviationDegrees)));
	}

	private static FeatureProperties pairProperties(Measurements measurement) {
		FeatureProperties properties = new FeatureProperties();
		properties.put("first_id", measurement.first.sectionID);
		properties.put("second_id", measurement.second.sectionID);
		properties.put("first_parent", measurement.first.parentID);
		properties.put("second_parent", measurement.second.parentID);
		properties.put("first_name", measurement.first.name);
		properties.put("second_name", measurement.second.name);
		properties.put("first_length_km", measurement.first.lengthKm);
		properties.put("second_length_km", measurement.second.lengthKm);
		properties.put("first_strike_deg", measurement.first.strikeDegrees);
		properties.put("second_strike_deg", measurement.second.strikeDegrees);
		properties.put("relation", measurement.relation.name());
		properties.put("bend_deg", measurement.bendAngleDegrees);
		properties.put("axial_difference_deg", measurement.axialStrikeDifferenceDegrees);
		properties.put("bend_transition_defined", measurement.bendTransitionDefined);
		properties.put("min_bend_deg", measurement.minBendAngleDegrees);
		properties.put("angular_restriction", Double.isFinite(measurement.maxPerpendicularDeviationDegrees));
		putFinite(properties, "perpendicular_tolerance_deg", measurement.maxPerpendicularDeviationDegrees);
		putFinite(properties, "mean_strike_axis_deg", measurement.averageStrikeAxisDegrees);
		putFinite(properties, "overlap_km", measurement.overlapLengthKm);
		putFinite(properties, "projected_gap_km", measurement.projectedGapKm);
		putFinite(properties, "facing_end_gap_km", measurement.facingEndGapKm);
		putFinite(properties, "facing_end_offset_km", measurement.facingEndOffsetKm);
		if (measurement.facingEndConnector != null)
			properties.put("facing_ends_bound_projected_gap", measurement.facingEndsBoundProjectedGap);
		return properties;
	}

	private enum ConnectorType {
		CLOSEST_TRACE("closest_trace", "#888888"),
		CLOSEST_ENDS("closest_ends", "#dd8800"),
		EXACT_PERPENDICULAR("exact_perpendicular", "#9933aa"),
		STEPOVER("allowed_stepover", "#dd2222"),
		FACING_ENDS("facing_ends", "#8c564b");

		final String kind;
		final String color;

		ConnectorType(String kind, String color) {
			this.kind = kind;
			this.color = color;
		}

		Connector getConnector(Measurements measurement) {
			switch (this) {
			case CLOSEST_TRACE:
				return measurement.closestTraceConnector;
			case CLOSEST_ENDS:
				return measurement.closestEndConnector;
			case EXACT_PERPENDICULAR:
				return measurement.exactPerpendicularConnector;
			case STEPOVER:
				return measurement.stepoverConnector;
			case FACING_ENDS:
				return measurement.facingEndConnector;
			default:
				throw new IllegalStateException("Unhandled connector type: "+this);
			}
		}
	}

	private static void putFinite(FeatureProperties properties, String key, double value) {
		if (Double.isFinite(value))
			properties.put(key, value);
	}

	private static String number(double value) {
		return Double.isFinite(value) ? Double.toString(value) : "";
	}
}
