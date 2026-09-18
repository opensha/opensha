package org.opensha.commons.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.awt.Color;

import org.junit.Test;

import net.mahdilamb.colormap.Colors;

public class ColorUtilsTest {

	@Test
	public void testTransparent() {
		assertEquals(new Color(12, 34, 56, 78), ColorUtils.transparent(new Color(12, 34, 56, 200), 78));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testTransparentRejectsInvalidAlpha() {
		ColorUtils.transparent(Color.BLACK, 256);
	}

	@Test
	public void testGetTabLight() {
		assertSame(Colors.tab_lightblue, ColorUtils.getTabLight(Colors.tab_blue));
		assertSame(Colors.tab_lightorange, ColorUtils.getTabLight(Colors.tab_orange));
		assertSame(Colors.tab_lightgreen, ColorUtils.getTabLight(Colors.tab_green));
		assertSame(Colors.tab_lightred, ColorUtils.getTabLight(Colors.tab_red));
		assertSame(Colors.tab_lightpurple, ColorUtils.getTabLight(Colors.tab_purple));
		assertSame(Colors.tab_lightbrown, ColorUtils.getTabLight(Colors.tab_brown));
		assertSame(Colors.tab_lightpink, ColorUtils.getTabLight(Colors.tab_pink));
		assertSame(Colors.tab_lightgrey, ColorUtils.getTabLight(Colors.tab_grey));
		assertSame(Colors.tab_lightolive, ColorUtils.getTabLight(Colors.tab_olive));
		assertSame(Colors.tab_lightaqua, ColorUtils.getTabLight(Colors.tab_aqua));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testGetTabLightRejectsEqualCopy() {
		ColorUtils.getTabLight(new Color(Colors.tab_blue.getRGB()));
	}

	@Test
	public void testSaturate() {
		Color color = new Color(0, 100, 255, 63);
		assertEquals(color, ColorUtils.saturate(color, 0));
		assertEquals(new Color(128, 178, 255, 63), ColorUtils.saturate(color, 1));
		assertEquals(new Color(192, 217, 255, 63), ColorUtils.saturate(color, 2));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testSaturateRejectsNegativeSteps() {
		ColorUtils.saturate(Color.BLACK, -1);
	}
}
