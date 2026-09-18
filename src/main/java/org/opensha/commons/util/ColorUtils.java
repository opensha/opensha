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
	 * Returns a copy of {@code color} with the supplied fractional opacity.
	 *
	 * @param color source color
	 * @param opacity opacity in the range {@code [0, 1]}
	 * @return a color with the same RGB components and the supplied opacity
	 */
	public static Color transparent(Color color, double opacity) {
		if (!(opacity >= 0d && opacity <= 1d))
			throw new IllegalArgumentException("Opacity must be in the range [0, 1]: "+opacity);
		return transparent(color, (int)(255d*opacity + 0.5d));
	}

	/**
	 * Linearly blends two colors, including their alpha components.
	 *
	 * @param first first color
	 * @param second second color
	 * @param secondFraction fraction of {@code second}, in the range {@code [0, 1]}
	 * @return the blended color
	 */
	public static Color blend(Color first, Color second, double secondFraction) {
		if (!(secondFraction >= 0d && secondFraction <= 1d))
			throw new IllegalArgumentException("Blend fraction must be in the range [0, 1]: "+secondFraction);
		double firstFraction = 1d - secondFraction;
		return new Color(
				(int)(first.getRed()*firstFraction + second.getRed()*secondFraction + 0.5d),
				(int)(first.getGreen()*firstFraction + second.getGreen()*secondFraction + 0.5d),
				(int)(first.getBlue()*firstFraction + second.getBlue()*secondFraction + 0.5d),
				(int)(first.getAlpha()*firstFraction + second.getAlpha()*secondFraction + 0.5d));
	}

	/**
	 * Returns {@code true} if the average RGB component is greater than 127.
	 */
	public static boolean isLight(Color color) {
		return color.getRed() + color.getGreen() + color.getBlue() > 3*127;
	}

	/**
	 * Returns {@code true} if the average RGB component is at most 127.
	 */
	public static boolean isDark(Color color) {
		return !isLight(color);
	}

	/**
	 * Composites {@code foreground} over {@code background} using source-over alpha compositing.
	 *
	 * @param foreground foreground color
	 * @param background background color
	 * @return the composited color
	 */
	public static Color compositeOver(Color foreground, Color background) {
		double foregroundAlpha = foreground.getAlpha()/255d;
		double backgroundAlpha = background.getAlpha()/255d;
		double resultAlpha = foregroundAlpha + backgroundAlpha*(1d - foregroundAlpha);
		if (resultAlpha == 0d)
			return new Color(0, 0, 0, 0);
		double backgroundScale = backgroundAlpha*(1d - foregroundAlpha);
		return new Color(
				(int)((foreground.getRed()*foregroundAlpha + background.getRed()*backgroundScale)/resultAlpha + 0.5d),
				(int)((foreground.getGreen()*foregroundAlpha + background.getGreen()*backgroundScale)/resultAlpha + 0.5d),
				(int)((foreground.getBlue()*foregroundAlpha + background.getBlue()*backgroundScale)/resultAlpha + 0.5d),
				(int)(255d*resultAlpha + 0.5d));
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
