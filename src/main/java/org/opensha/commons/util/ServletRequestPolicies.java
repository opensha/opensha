package org.opensha.commons.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.siteData.impl.CVMHBasinDepth;
import org.opensha.commons.data.siteData.impl.CVM_Vs30;
import org.opensha.commons.data.xyz.AbstractGeoDataSet;
import org.opensha.commons.data.xyz.AbstractXYZ_DataSet;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.FixedLocationArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.CoastAttributes;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.PSXYElement;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.util.ServletObjectInput.Policy;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.commons.util.cpt.LinearBlender;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Allowlisted classes and resource limits for the servlet request formats.
 * Byte caps can be overridden with {@code opensha.servlet.maxRequestBytes.<endpoint>}.
 */
public final class ServletRequestPolicies {

	static final String BYTE_LIMIT_PROPERTY_PREFIX = "opensha.servlet.maxRequestBytes.";

	private static final long MIB = 1024L * 1024L;
	private static final int LARGE_ARRAY_LIMIT = 4_194_304;
	private static final Class<?> UNMODIFIABLE_LOCATIONS = new LocationList().unmodifiableList().getClass();

	// Ordinary strings and null do not invoke the deserialization filter.
	public static final Policy VULNERABILITY = policy("vulnerability", 1, 2, 16, 0, Set.of());
	public static final Policy DISAGGREGATION = policy("disaggregation", 10, 8, 1_000_000, 1_000_000,
			Set.of(DisaggregationPlotData.class, double[].class, double[][].class, double[][][].class));
	public static final Policy SITE_DATA = policy("siteData", 100, 12, 8_500_000, LARGE_ARRAY_LIMIT,
			Set.of(Location.class, LocationList.class, UNMODIFIABLE_LOCATIONS, FaultTrace.class, ArrayList.class,
					Object[].class, double[].class, Boolean.class, Enum.class, CVMHBasinDepth.Version.class,
					CVM_Vs30.CVM.class));

	private static final Set<Class<?>> MAP_CLASSES = mapClasses();
	public static final Policy GMT = mapPolicy("gmt");
	public static final Policy HAZUS = mapPolicy("hazus", GMT_Map[].class);

	private ServletRequestPolicies() {}

	/** Adds the exact request classes supplied by the downstream CyberShake servlet. */
	public static Policy cyberShake(Class<?>... cyberShakeClasses) {
		return mapPolicy("cyberShake", cyberShakeClasses);
	}

	private static Policy mapPolicy(String propertySuffix, Class<?>... additionalClasses) {
		Set<Class<?>> allowed = new HashSet<>(MAP_CLASSES);
		for (Class<?> type : additionalClasses)
			allowed.add(type);
		return policy(propertySuffix, 100, 32, 12_000_000, LARGE_ARRAY_LIMIT, allowed);
	}

	private static Policy policy(String propertySuffix, long defaultMiB, long depth, long references,
			long arrayLength, Set<Class<?>> allowed) {
		long maxBytes = Long.getLong(BYTE_LIMIT_PROPERTY_PREFIX + propertySuffix, defaultMiB * MIB);
		return new Policy(propertySuffix, maxBytes, depth, references, arrayLength, allowed);
	}

	private static Set<Class<?>> mapClasses() {
		// A new dataset may introduce serializable superclasses and field types as well as its concrete class.
		// Review the serialized form and add a request round-trip test when extending this list.
		Set<Class<?>> allowed = new HashSet<>(Set.of(
				GMT_Map.class, GMT_Map.HighwayFile.class, GMT_InterpolationSettings.class, Region.class,
				GriddedRegion.class, Location.class, LocationList.class, UNMODIFIABLE_LOCATIONS, FaultTrace.class,
				AbstractXYZ_DataSet.class, AbstractGeoDataSet.class, ArbDiscrGeoDataSet.class,
				FixedLocationArbDiscrGeoDataSet.class, GriddedGeoDataSet.class, GriddedGeoDataSet.FloatData.class,
				CPT.class, CPTVal.class, LinearBlender.class, CoastAttributes.class, TopographicSlopeFile.class,
				PSText.class, PSText.Justify.class, PSXYElement.class, PSXYPolygon.class, PSXYSymbol.class,
				PSXYSymbol.Symbol.class, PSXYSymbolSet.class, PlotLineType.class,
				Point2D.Double.class, Point2D.Float.class, Color.class,
				Number.class, Integer.class, Double.class, Enum.class, ArrayList.class, HashMap.class,
				Object[].class, Map.Entry[].class, double[].class, float[].class, int[].class));

		// Preset regions and their private, serializable base classes.
		allowed.addAll(Set.of(CaliforniaRegions.RELM_TESTING.class.getSuperclass(),
				CaliforniaRegions.RELM_TESTING_GRIDDED.class.getSuperclass(),
				CaliforniaRegions.RELM_GRIDDED.class, CaliforniaRegions.RELM_TESTING.class,
				CaliforniaRegions.RELM_TESTING_GRIDDED.class, CaliforniaRegions.RELM_COLLECTION.class,
				CaliforniaRegions.RELM_COLLECTION_GRIDDED.class, CaliforniaRegions.RELM_NOCAL.class,
				CaliforniaRegions.RELM_NOCAL_GRIDDED.class, CaliforniaRegions.RELM_SOCAL.class,
				CaliforniaRegions.RELM_SOCAL_GRIDDED.class, CaliforniaRegions.SF_BOX.class,
				CaliforniaRegions.SF_BOX_GRIDDED.class, CaliforniaRegions.LA_BOX.class,
				CaliforniaRegions.LA_BOX_GRIDDED.class, CaliforniaRegions.NORTHRIDGE_BOX.class,
				CaliforniaRegions.SAN_DIEGO_BOX.class, CaliforniaRegions.CYBERSHAKE_MAP_REGION.class,
				CaliforniaRegions.CYBERSHAKE_MAP_GRIDDED.class, CaliforniaRegions.CYBERSHAKE_CCA_MAP_REGION.class,
				CaliforniaRegions.CYBERSHAKE_CCA_MAP_GRIDDED.class,
				CaliforniaRegions.CYBERSHAKE_BAY_AREA_MAP_REGION.class,
				CaliforniaRegions.CYBERSHAKE_BAY_AREA_SMALL_MAP_REGION.class));

		// Fixed-location datasets serialize Guava collections through these concrete replacement classes.
		allowed.add(ImmutableList.of().getClass());
		allowed.add(ImmutableList.of(0).getClass());
		allowed.add(ImmutableList.of(0, 1).getClass());
		allowed.add(ImmutableMap.of().getClass());
		allowed.add(ImmutableMap.of(0, 0).getClass());
		allowed.add(ImmutableMap.of(0, 0, 1, 1).getClass());
		for (String type : new String[] { "ImmutableList$SerializedForm", "ImmutableMap$SerializedForm",
				"ImmutableBiMap$SerializedForm" }) {
			try {
				allowed.add(Class.forName("com.google.common.collect." + type, false,
						ImmutableList.class.getClassLoader()));
			} catch (ClassNotFoundException e) {
				// The serialized replacement types vary across Guava releases.
			}
		}
		return Set.copyOf(allowed);
	}
}
