package org.opensha.commons.gui.plot;

import java.awt.Color;

/**
 * Utilities for creating figures of various size in publications with consistent font sizes and line widths
 */
public class FigureSizeUtils {
	
	// these are the usable area in inches of SSA journals
	public static final double DEFAULT_USABLE_PAGE_WIDTH = 7.2d;
	public static final double DEFAULT_USABLE_PAGE_HEIGHT = 9.4d;
	
	public static final int FIGURE_DPI_DEFAULT = 300;
	private int dpi;
	private double usablePageWidth;
	private double usablePageHeight;
	
	public static PlotPreferences getDefaultFigurePrefs() {
		return getFigurePrefs(FIGURE_DPI_DEFAULT);
	}
	
	public static PlotPreferences getFigurePrefs(int dpi) {
		PlotPreferences pref = PlotPreferences.getDefault();
		// these are the font sizes we typically use for figures
		// assume they're relative to 150
		int refDPI = 150;
		pref.setTickLabelFontSize(getScaledFontSize(18, refDPI, dpi));
		pref.setAxisLabelFontSize(getScaledFontSize(24, refDPI, dpi));
		pref.setPlotLabelFontSize(getScaledFontSize(24, refDPI, dpi));
		pref.setLegendFontSize(getScaledFontSize(20, refDPI, dpi));
		pref.setBackgroundColor(Color.WHITE);
		if (dpi != refDPI)
			pref.setSizeScalar((double)dpi/(double)refDPI);
		return pref;
	}
	
	public static int getScaledFontSize(int refSize, int refDPI, int targetDPI) {
		return (int)Math.round((double)refSize * (double)targetDPI / (double)refDPI);
	}
	
	public static int calcFractionalWidthPixels(double fractWidth) {
		return calcPixels(fractWidth, DEFAULT_USABLE_PAGE_WIDTH, FIGURE_DPI_DEFAULT);
	}
	
	public static int calcFractionalHeightPixels(double fractHeight) {
		return calcPixels(fractHeight, DEFAULT_USABLE_PAGE_HEIGHT, FIGURE_DPI_DEFAULT);
	}
	
	public static int calcPixels(double fraction, double totalLengthInches, int dpi) {
		return calcPixels(fraction*totalLengthInches, dpi);
	}
	
	public static int calcPixels(double lengthInches, int dpi) {
		return (int)Math.round(lengthInches*dpi);
	}
	
	public FigureSizeUtils() {
		this(FIGURE_DPI_DEFAULT);
	}
	
	public FigureSizeUtils(int dpi) {
		this(dpi, DEFAULT_USABLE_PAGE_WIDTH, DEFAULT_USABLE_PAGE_HEIGHT);
	}
	
	public FigureSizeUtils(int dpi, double usablePageWidth, double usablePageHeight) {
		this.dpi = dpi;
		this.usablePageWidth = usablePageWidth;
		this.usablePageHeight = usablePageHeight;
	}
	
	public PlotPreferences getFigurePrefs() {
		return getFigurePrefs(dpi);
	}
	
	public int getWidthPixels(double fractWidth) {
		return calcPixels(fractWidth, usablePageWidth, dpi);
	}
	
	public int getHeightPixels(double fractHeight) {
		return calcPixels(fractHeight, usablePageHeight, dpi);
	}

}
