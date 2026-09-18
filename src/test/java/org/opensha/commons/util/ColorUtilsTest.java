package org.opensha.commons.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
	public void testTransparentOpacity() {
		assertEquals(new Color(12, 34, 56, 0), ColorUtils.transparent(new Color(12, 34, 56), 0d));
		assertEquals(new Color(12, 34, 56, 128), ColorUtils.transparent(new Color(12, 34, 56), 0.5d));
		assertEquals(new Color(12, 34, 56, 255), ColorUtils.transparent(new Color(12, 34, 56), 1d));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testTransparentRejectsInvalidOpacity() {
		ColorUtils.transparent(Color.BLACK, Double.NaN);
	}

	@Test
	public void testBlend() {
		Color first = new Color(10, 20, 30, 40);
		Color second = new Color(110, 220, 130, 240);
		assertEquals(first, ColorUtils.blend(first, second, 0d));
		assertEquals(second, ColorUtils.blend(first, second, 1d));
		assertEquals(new Color(35, 70, 55, 90), ColorUtils.blend(first, second, 0.25d));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testBlendRejectsInvalidFraction() {
		ColorUtils.blend(Color.BLACK, Color.WHITE, -0.1d);
	}

	@Test
	public void testLightAndDark() {
		assertTrue(ColorUtils.isLight(Color.WHITE));
		assertFalse(ColorUtils.isDark(Color.WHITE));
		assertFalse(ColorUtils.isLight(new Color(127, 127, 127)));
		assertTrue(ColorUtils.isDark(new Color(127, 127, 127)));
		assertTrue(ColorUtils.isLight(new Color(128, 128, 128)));
	}

	@Test
	public void testCompositeOver() {
		assertEquals(new Color(128, 0, 127),
				ColorUtils.compositeOver(new Color(255, 0, 0, 128), Color.BLUE));
		Color background = new Color(12, 34, 56, 78);
		assertEquals(background, ColorUtils.compositeOver(new Color(0, 0, 0, 0), background));
		Color foreground = new Color(98, 76, 54);
		assertEquals(foreground, ColorUtils.compositeOver(foreground, background));
		assertEquals(new Color(0, 0, 0, 0),
				ColorUtils.compositeOver(new Color(1, 2, 3, 0), new Color(4, 5, 6, 0)));
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
