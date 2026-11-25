package org.opensha.commons.mapping.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;

public class GMT_RenderFilesTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void writesMetadataAsTextWithLegacyLineBreaks() throws IOException {
		Path output = temp.getRoot().toPath();
		GMT_RenderFiles.writeHtmlMetadata(output,
				"Paramètres & settings<br>\nfirst<BR/>second<br />third\nHeading: <h1>example</h1><br title=\"tag\">");

		String page = Files.readString(output.resolve(GMT_RenderFiles.MAP_METADATA_FILE));
		assertTrue(page.contains("<pre>\nParamètres &amp; settings\nfirst\nsecond\nthird\n"
				+ "Heading: &lt;h1&gt;example&lt;/h1&gt;&lt;br title=&quot;tag&quot;&gt;</pre>"));
		assertFalse(page.contains("<h1>"));
		assertFalse(page.contains("<br title="));
	}

	@Test
	public void acceptsLegacyOutputsAndPreservesOptionalFormats() throws Exception {
		Path output = temp.getRoot().toPath().resolve("gmtData");
		GMT_Map standard = newMap();
		GMT_RenderFiles.validateMap(standard, null, output);
		assertEquals("map_data.txt", standard.getXyzFileName());

		GMT_Map cyberShake = newMap();
		cyberShake.setXyzFileName("base_map.xyz");
		cyberShake.setJPGFileName(null);
		GMT_RenderFiles.validateMap(cyberShake, null, output);
		assertEquals("base_map.xyz", cyberShake.getXyzFileName());
		assertNull(cyberShake.getJPGFileName());

		for (String prefix : List.of("sa03", "sa10", "pga", "pgv")) {
			GMT_Map hazus = newMap();
			hazus.setXyzFileName(prefix + "_map_data.txt");
			hazus.setPSFileName(prefix + "_map.ps");
			hazus.setPDFFileName(prefix + "_map.pdf");
			hazus.setJPGFileName(prefix + "_map.jpg");
			GMT_RenderFiles.validateMap(hazus, null, output);
		}
	}

	@Test
	public void rejectsReservedOrInvalidOutputsAndUnknownPalette() {
		Path output = temp.getRoot().toPath().resolve("gmtData");
		GMT_Map map = newMap();
		for (String name : List.of("nested/map.xyz", "two words.xyz", "other.pdf", GMT_RenderFiles.SCRIPT_FILE,
				GMT_RenderFiles.TEXT_METADATA_FILE, "scatter_diffs.xyz")) {
			map.setXyzFileName(name);
			assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, null, output));
		}
		map.setXyzFileName("map.xyz");
		map.setCptFile("unlisted.cpt");
		assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, null, output));
	}

	@Test
	public void inlinePaletteUsesServerFilename() throws Exception {
		GMT_Map map = new GMT_Map(null, null, 0.1, new CPT());
		map.setCustomCptFileName("my_palette.cpt");
		GMT_RenderFiles.validateMap(map, null, temp.getRoot().toPath().resolve("gmtData"));
		assertEquals("cptFile.cpt", map.getCustomCptFileName());
	}

	@Test
	public void validatesCustomInputsAndQuotesPathsInScript() throws Exception {
		Path input = temp.newFolder("approved inputs").toPath();
		Path output = temp.newFolder("gmtData").toPath();
		Path grid = Files.createFile(input.resolve("base grid.grd"));
		Path intensity = Files.createFile(input.resolve("shade grid.grad"));

		LocationList border = new LocationList();
		border.add(new Location(34, -118));
		border.add(new Location(34, -117));
		border.add(new Location(35, -118));
		GMT_Map map = new GMT_Map(new Region(border, null), null, 0.1, GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		map.setCustomScaleMin(0d);
		map.setCustomScaleMax(1d);
		map.setMaskIfNotRectangular(true);
		map.setCustomGRDPath(grid.getFileName().toString());
		map.setCustomIntenPath(intensity.getFileName().toString());

		GMT_RenderFiles.validateMap(map, input.toString(), output);
		assertEquals(grid.toRealPath().toString(), map.getCustomGRDPath());
		assertEquals(intensity.toRealPath().toString(), map.getCustomIntenPath());
		List<String> script = new GMT_MapGenerator().getGMT_ScriptLines(map, output.toString());
		String quotedGrid = "'" + grid.toRealPath() + "'";
		String quotedIntensity = "'" + intensity.toRealPath() + "'";
		assertTrue(script.stream().anyMatch(line -> line.contains("grdmath " + quotedGrid)
				&& line.endsWith(" = temp_junkMasked.grd")));
		assertTrue(script.stream().anyMatch(line -> line.contains("-I" + quotedIntensity)));
		assertFalse(script.stream().anyMatch(line -> line.startsWith("mv ") && line.contains(quotedGrid)));
	}

	@Test
	public void rejectsPathsOutsideInputRootAndDisallowsEmptyOrOverlappingRoots() throws Exception {
		Path input = temp.newFolder("input").toPath();
		Path output = temp.newFolder("gmtData").toPath();
		Path outside = Files.createFile(temp.getRoot().toPath().resolve("outside.grd"));
		Files.createFile(input.resolve("valid.grd"));
		GMT_Map map = newMap();

		for (String name : List.of(outside.toString(), "../outside.grd")) {
			map.setCustomGRDPath(name);
			assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, input.toString(), output));
		}
		map.setCustomGRDPath("valid.grd");
		assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, "", output));
		assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, output.toString(), output));
	}

	@Test
	public void rejectsSymlinkOutsideInputRoot() throws Exception {
		Path input = temp.newFolder("input").toPath();
		Path output = temp.newFolder("gmtData").toPath();
		Path outside = Files.createFile(temp.getRoot().toPath().resolve("outside.grd"));
		try {
			Files.createSymbolicLink(input.resolve("link.grd"), outside);
		} catch (IOException | UnsupportedOperationException e) {
			assumeNoException(e);
		}
		GMT_Map map = newMap();
		map.setCustomGRDPath("link.grd");
		assertThrows(GMT_MapException.class, () -> GMT_RenderFiles.validateMap(map, input.toString(), output));
	}

	private static GMT_Map newMap() {
		return new GMT_Map(null, null, 0.1, GMT_CPT_Files.MAX_SPECTRUM.getFileName());
	}
}
