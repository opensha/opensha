package org.opensha.commons.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.siteData.impl.CVMHBasinDepth;
import org.opensha.commons.data.siteData.impl.CVM_Vs30;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.FixedLocationArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.util.ServletObjectInput.Policy;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;

public class ServletObjectInputTest {

	@Test
	public void vulnerabilityLookupAcceptsNamesAndNull() throws Exception {
		try (ServletObjectInput input = open(ServletRequestPolicies.VULNERABILITY, "example", null)) {
			assertEquals("example", input.readObject());
			assertNull(input.readObject());
		}
		try (ServletObjectInput input = open(ServletRequestPolicies.VULNERABILITY, 42)) {
			assertThrows(InvalidClassException.class, input::readObject);
		}
	}

	@Test
	public void byteLimitAppliesWithAndWithoutContentLength() throws Exception {
		String name = "example".repeat(30);
		byte[] bytes = serialize(name);
		Policy exactLength = new Policy("vulnerability", bytes.length, 4, 16, 0, Set.of());
		int limit = bytes.length - 1;
		Policy tooShort = new Policy("vulnerability", limit, 4, 16, 0, Set.of());

		IOException declaredSize = assertThrows(IOException.class, () -> ServletObjectInput.open(
				new ByteArrayInputStream(bytes), bytes.length, tooShort));
		String message = declaredSize.getMessage();
		assertTrue(message, message.contains("limit of " + limit + " bytes"));
		assertTrue(message, message.contains("opensha.servlet.maxRequestBytes.vulnerability"));
		try (ServletObjectInput input = ServletObjectInput.open(new ByteArrayInputStream(bytes), -1, tooShort)) {
			assertEquals(message, assertThrows(IOException.class, input::readObject).getMessage());
		}
		try (ServletObjectInput input = ServletObjectInput.open(new ByteArrayInputStream(bytes), -1, exactLength)) {
			assertEquals(name, input.readObject());
		}
	}

	@Test
	public void arrayLimitAppliesEvenToAnAllowedClass() throws Exception {
		Policy oneCoordinate = new Policy("siteData", 1024, 4, 16, 1, Set.of(double[].class));
		try (ServletObjectInput input = open(oneCoordinate, new double[] { 34, -118 })) {
			String message = assertThrows(InvalidClassException.class, input::readObject).getMessage();
			assertTrue(message, message.contains("siteData"));
			assertTrue(message, message.contains("double[] length 2 exceeds the limit of 1"));
		}
	}

	@Test
	public void siteDataAcceptsCoordinatesAndProviderOptions() throws Exception {
		Location location = new Location(34, -118);
		LocationList locations = new LocationList();
		locations.add(location);
		ArrayList<double[]> coordinates = new ArrayList<>(List.of(new double[] { 34, -118 }));
		Policy policy = ServletRequestPolicies.SITE_DATA;
		try (ServletObjectInput input = open(policy, coordinates)) {
			List<?> readCoordinates = (List<?>) input.readObject();
			assertEquals(34d, ((double[]) readCoordinates.get(0))[0], 0d);
		}
		try (ServletObjectInput input = open(policy, locations.unmodifiableList())) {
			assertEquals(locations, input.readObject());
		}

		ArrayList<?> options = new ArrayList<>(List.of(
				CVMHBasinDepth.Version.values()[0], Boolean.TRUE, CVM_Vs30.CVM.values()[0]));
		String operation = AbstractSiteDataServlet.OP_GET_CLOSEST;
		try (ServletObjectInput input = open(policy, options, operation, location)) {
			assertEquals(options, input.readObject());
			assertEquals(operation, input.readObject());
			assertEquals(location, input.readObject());
		}
	}

	@Test
	public void disaggregationAcceptsBinsAndMetadata() throws Exception {
		DisaggregationPlotData data = new DisaggregationPlotData(
				new double[] { 6 }, new double[] { 5, 7 }, new double[] { 20 }, new double[] { 10, 30 },
				100, 2, new double[][][] { { { 60, 40 } } });
		try (ServletObjectInput input = open(ServletRequestPolicies.DISAGGREGATION, data, "Plot metadata")) {
			DisaggregationPlotData result = (DisaggregationPlotData) input.readObject();
			assertEquals(40d, result.getPdf3D()[0][0][1], 0d);
			assertEquals("Plot metadata", input.readObject());
		}
	}

	@Test
	public void mapsAcceptSupportedDatasetsAndOverlays() throws Exception {
		Location first = new Location(34, -118);
		Location last = new Location(35, -117);
		Region region = new Region(first, last);
		region.addInterior(new Region(new Location(34.2, -117.8), new Location(34.3, -117.7)));
		ArbDiscrGeoDataSet scattered = new ArbDiscrGeoDataSet(true);
		scattered.set(first, 1);
		CPT cpt = new CPT(0, 2, Color.BLACK, Color.WHITE);
		GMT_Map singlePoint = new GMT_Map(region, new FixedLocationArbDiscrGeoDataSet(scattered), 0.1, cpt);

		scattered.set(last, 2);
		GMT_Map map = new GMT_Map(region, scattered, 0.1, cpt);
		map.setGMT_Param("MAP_FRAME_WIDTH", "0.1i");
		map.setCustomScaleMin(0d);
		PSXYPolygon polygon = new PSXYPolygon();
		polygon.addPoint(new Point2D.Double(-118, 34));
		polygon.addPoint(new Point2D.Float(-117, 35));
		map.addPolys(polygon);
		map.addText(new PSText(new Point2D.Double(-117.5, 34.5), Color.WHITE, 12, "Site", PSText.Justify.CENTER));
		PSXYSymbolSet symbols = new PSXYSymbolSet();
		symbols.setCpt(cpt);
		symbols.addSymbol(new PSXYSymbol(new Point2D.Double(-117.5, 34.5), PSXYSymbol.Symbol.CIRCLE, 0.1), 1);
		map.setSymbolSet(symbols);

		try (ServletObjectInput input = open(ServletRequestPolicies.GMT, null, map, "Map metadata", "metadata.txt")) {
			assertNull(input.readObject());
			assertEquals(2, ((GMT_Map) input.readObject()).getGriddedData().size());
			assertEquals("Map metadata", input.readObject());
			assertEquals("metadata.txt", input.readObject());
		}

		GriddedRegion grid = new CaliforniaRegions.CYBERSHAKE_MAP_GRIDDED(0.2);
		GMT_Map[] maps = {
				map,
				new GMT_Map(grid, new GriddedGeoDataSet.FloatData(grid, true), 0.2, cpt),
				singlePoint,
				new GMT_Map(region, new FixedLocationArbDiscrGeoDataSet(scattered), 0.1, cpt)
		};
		try (ServletObjectInput input = open(
				ServletRequestPolicies.HAZUS, null, maps, "Map metadata", "metadata.txt")) {
			assertNull(input.readObject());
			GMT_Map[] decoded = (GMT_Map[]) input.readObject();
			assertEquals(maps.length, decoded.length);
			assertEquals(grid.getNodeCount(), decoded[1].getGriddedData().size());
			assertEquals(1, decoded[2].getGriddedData().size());
			assertEquals(2, decoded[3].getGriddedData().size());
			assertEquals("Map metadata", input.readObject());
			assertEquals("metadata.txt", input.readObject());
		}
		try (ServletObjectInput input = open(ServletRequestPolicies.GMT, (Object) maps)) {
			assertThrows(InvalidClassException.class, input::readObject);
		}
	}

	@Test
	public void unsupportedDatasetIsNamedInTheMapRejection() throws Exception {
		Region region = new Region(new Location(34, -118), new Location(35, -117));
		GMT_Map map = new GMT_Map(region, new UnlistedGeoDataSet(), 0.1, new CPT());
		try (ServletObjectInput input = open(ServletRequestPolicies.GMT, null, map)) {
			assertNull(input.readObject());
			String message = assertThrows(InvalidClassException.class, input::readObject).getMessage();
			assertTrue(message, message.contains("gmt servlet request"));
			assertTrue(message, message.contains(UnlistedGeoDataSet.class.getName()));
			assertTrue(message, message.contains("ServletRequestPolicies"));
		}
	}

	private static final class UnlistedGeoDataSet extends ArbDiscrGeoDataSet {
		private static final long serialVersionUID = 1L;

		UnlistedGeoDataSet() {
			super(true);
		}
	}

	private static ServletObjectInput open(Policy policy, Object... values) throws IOException {
		byte[] bytes = serialize(values);
		return ServletObjectInput.open(new ByteArrayInputStream(bytes), bytes.length, policy);
	}

	private static byte[] serialize(Object... values) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			for (Object value : values)
				output.writeObject(value);
		}
		return bytes.toByteArray();
	}
}
