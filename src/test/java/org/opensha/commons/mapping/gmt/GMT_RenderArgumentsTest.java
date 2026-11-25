package org.opensha.commons.mapping.gmt;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.PSText.Justify;
import org.opensha.commons.util.ServletWorkLimits;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;

public class GMT_RenderArgumentsTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void knownSettingsGenerateIndividualArguments() throws Exception {
		GMT_Map map = newMap();
		map.setGMT_Param("MAP_FRAME_TYPE", "FANCY");
		map.setGMT_Param("MAP_FRAME_WIDTH", "0.04i");
		map.setGMT_Param("MAP_TICK_LENGTH_PRIMARY", "-0.04i/0.02i");
		GMT_RenderArguments.validateRemoteMap(map);

		List<String> script = new GMT_MapGenerator().getGMT_ScriptLines(map, temp.getRoot().getAbsolutePath());
		assertTrue(script.contains("${GMT_PATH}gmtset 'MAP_FRAME_TYPE=FANCY'"));
		assertTrue(script.contains("${GMT_PATH}gmtset 'MAP_FRAME_WIDTH=0.04i'"));
		assertTrue(script.contains("${GMT_PATH}gmtset 'MAP_TICK_LENGTH_PRIMARY=-0.04i/0.02i'"));
	}

	@Test
	public void rejectsUnsupportedKeysAndValues() {
		GMT_Map map = newMap();
		map.setGMT_Param("MAP_FRAME_PEN", "1p");
		assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(map));

		GMT_Map widthMap = newMap();
		for (String width : List.of("0.0i", "two inches", "0.04i plain", "NaN")) {
			widthMap.setGMT_Param("MAP_FRAME_WIDTH", width);
			assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(widthMap));
		}
	}

	@Test
	public void annotationPreservesOrdinaryPunctuationInOneRecord() throws Exception {
		GMT_Map map = newMap();
		map.addText(annotation("O'Brien; PGA (μm/s)"));
		GMT_RenderArguments.validateRemoteMap(map);

		ArrayList<String> script = new ArrayList<>();
		GMT_MapGenerator.addTextCommands(script, map, "-R-118/-117/34/35", " -JM6i", "map.ps");
		assertTrue(script.contains("printf '%s\\n' '-117.5 34.5 O'\"'\"'Brien; PGA (μm/s)'"
				+ " | ${GMT_PATH}pstext '-F+f12p,Helvetica-Bold,0/0/0+jLB'"
				+ " -R-118/-117/34/35 -JM6i -K -O >> 'map.ps'"));
	}

	@Test
	public void rejectsMultilineAndExternalTypesettingText() {
		GMT_Map map = newMap();
		for (String text : List.of("two\nlines", "tab\tcharacter", "<math>x + y</math>", "a".repeat(1025))) {
			map.setCustomLabel(text);
			assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(map));
		}

		GMT_Map annotated = newMap();
		annotated.addText(annotation("two\nlines"));
		assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(annotated));
	}

	@Test
	public void rejectsInvalidNumericArguments() throws Exception {
		GMT_Map map = newMap();
		GMT_InterpolationSettings interpolation = GMT_InterpolationSettings.getDefaultSettings();
		map.setInterpSettings(interpolation);
		GMT_RenderArguments.validateRemoteMap(map);

		interpolation.setInterpSpacing(Double.NaN);
		assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(map));

		GMT_Map colorbar = newMap();
		colorbar.setCPTCustomInterval(0d);
		assertThrows(GMT_MapException.class, () -> GMT_RenderArguments.validateRemoteMap(colorbar));
	}

	@Test
	public void boundsGridAndRasterWork() throws GMT_MapException {
		GMT_Map map = newMap();
		map.setDpi(600);
		GMT_RenderArguments.validateRemoteMap(map);

		map.setDpi(Integer.MAX_VALUE);
		GMT_MapException rasterError = assertThrows(GMT_MapException.class,
				() -> GMT_RenderArguments.validateRemoteMap(map));
		assertTrue(rasterError.getMessage().contains("pixels"));

		map.setDpi(600);
		map.setGriddedDataInc(0.00001);
		GMT_MapException gridError = assertThrows(GMT_MapException.class,
				() -> GMT_RenderArguments.validateRemoteMap(map));
		assertTrue(gridError.getMessage().contains("grid exceeds"));
	}

	@Test
	public void boundsCombinedGridWork() throws GMT_MapException {
		GMT_RenderArguments.validateGridWork(ServletWorkLimits.TOTAL_GRID_CELLS);
		assertThrows(GMT_MapException.class,
				() -> GMT_RenderArguments.validateGridWork((long) ServletWorkLimits.TOTAL_GRID_CELLS + 1));
	}

	private static GMT_Map newMap() {
		Region region = new Region(new Location(34, -118), new Location(35, -117));
		GMT_Map map = new GMT_Map(region, null, 0.1, GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		map.setCustomScaleMin(0d);
		map.setCustomScaleMax(1d);
		return map;
	}

	private static PSText annotation(String text) {
		return new PSText(new Point2D.Double(-117.5, 34.5), Color.BLACK, 12, text, Justify.LEFT_BOTTOM);
	}
}
