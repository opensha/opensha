package org.opensha.commons.util;

import java.awt.Color;

import net.mahdilamb.colormap.Colors;

/** Utility methods for working with {@link Color}s. */
public final class ColorUtils {

	private ColorUtils() {}

	/**
	 * Returns a copy of {@code color} with the supplied alpha value.
	 *
	 * @param color source color
	 * @param alpha alpha value in the range {@code [0, 255]}
	 * @return a color with the same RGB components and the supplied alpha
	 */
	public static Color transparent(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	/**
	 * Returns the light Tableau color paired with the supplied base Tableau color.
	 * The supplied color must be one of the {@code Colors.tab_*} constant instances.
	 *
	 * @param color a base Tableau color constant
	 * @return the corresponding light Tableau color
	 * @throws IllegalArgumentException if {@code color} is not a base Tableau color constant
	 */
	public static Color getTabLight(Color color) {
		if (color == Colors.tab_blue)
			return Colors.tab_lightblue;
		if (color == Colors.tab_orange)
			return Colors.tab_lightorange;
		if (color == Colors.tab_green)
			return Colors.tab_lightgreen;
		if (color == Colors.tab_red)
			return Colors.tab_lightred;
		if (color == Colors.tab_purple)
			return Colors.tab_lightpurple;
		if (color == Colors.tab_brown)
			return Colors.tab_lightbrown;
		if (color == Colors.tab_pink)
			return Colors.tab_lightpink;
		if (color == Colors.tab_grey)
			return Colors.tab_lightgrey;
		if (color == Colors.tab_olive)
			return Colors.tab_lightolive;
		if (color == Colors.tab_aqua)
			return Colors.tab_lightaqua;
		throw new IllegalArgumentException("Not a base Colors.tab_* constant: "+color);
	}

	/**
	 * Lightens a color by repeatedly moving each RGB component halfway toward 255.
	 * The alpha component is preserved.
	 *
	 * @param color source color
	 * @param steps number of lightening steps, zero or greater
	 * @return the lightened color
	 */
	public static Color saturate(Color color, int steps) {
		if (steps < 0)
			throw new IllegalArgumentException("Steps must be non-negative: "+steps);
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		for (int i=0; i<steps; i++) {
			r = (int)(0.5d*(r + 255d)+0.5d);
			g = (int)(0.5d*(g + 255d)+0.5d);
			b = (int)(0.5d*(b + 255d)+0.5d);
		}
		return new Color(r, g, b, color.getAlpha());
	}

}
