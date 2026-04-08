package org.opensha.commons.gui.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jfree.chart.ui.RectangleInsets;

import com.google.common.collect.Lists;

/**
 * Class for storing plot preferences (font sizes and colors).
 * Classes can subscribe as a listener for updates.
 * 
 * @author kevin
 *
 */
public class PlotPreferences {
	
	private int axisLabelFontSize;
	private int tickLabelFontSize;
	private int plotLabelFontSize;
	private int legendFontSize;
	private Color backgroundColor;
	
	private Color insetLegendBackground = new Color(255, 255, 255, 180);
	private Color insetLegendBorder = Color.BLACK;
	
	// gap between subplots
	private double subplotGap;
	
	// padding around the whole pot
	private RectangleInsets plotPadding;
	
	// axis and tick label padding
	private RectangleInsets axisLabelPadding = new RectangleInsets(3, 3, 3, 3);
	// this was previously hardcoded in GraphPanel & different from JFree default
	private RectangleInsets axisTickLabelPaddingX = new RectangleInsets(3, 10, 3, 10);
	// this is JFree default
	private RectangleInsets axisTickLabelPaddingY = new RectangleInsets(2, 4, 2, 4);
	
	// legend padding
	private double legendBorderThickness = 1d;
	private RectangleInsets legendPadding = new RectangleInsets(1, 1, 1, 1);
	private RectangleInsets legendItemLabelPadding = new RectangleInsets(2, 2, 2, 2);
	private RectangleInsets legendItemGraphicPadding = new RectangleInsets(2, 2, 2, 2);
	private double legendLineLength = 14d; // jfree default
	
	private int titleMaxLines = 3;
	
	private double cptStripWidth = 15d;
	private double cptPadding = 5d;
	private double cptTickLength = 4d;
	private double cptTickMinorLength = 2d;
	
	// OpenSHA has long used weird/inconsistent symbol sizes such that a symbol size of 1 is actually 3 points
	// if you set this to false, symbol syzing will be the true bounding box width
	private boolean trueSymbolSizing = false;
	// 2026 note: this has long been OpenSHA default; it overshoots for large thickness, but often better than
	// undershooting when separate lines meet at sharp angles.
	private int solidLineCap = BasicStroke.CAP_SQUARE;
	private int solidLineJoin = BasicStroke.JOIN_MITER;
	private int dashedLineCap = BasicStroke.CAP_BUTT;
	private int dashedLineJoin = BasicStroke.JOIN_BEVEL;
	
	private double sizeScalar = 1d;
	
	private List<ChangeListener> listeners = Lists.newArrayList();
	
	/*
	 * Package private default instance for use in neighboring utils when plot prefs not passed in
	 */
	static final PlotPreferences DEFAULT = getDefaultAppPrefs();
	
	/**
	 * Default OpenSHA app plot preferences.
	 * @return
	 */
	public static PlotPreferences getDefaultAppPrefs() {
		PlotPreferences pref = new PlotPreferences();
		pref.tickLabelFontSize = 12;
		pref.axisLabelFontSize = 14;
		pref.plotLabelFontSize = 16;
		pref.legendFontSize = 14;
		pref.backgroundColor = new Color( 200, 200, 230 );
		pref.subplotGap = 30;
		
		// axis padding, read as top, left, bottom, right
		
		// this creates right insets that are comparable to that taken up by the left of the plot w/ labels
		// also adds a little padding on top
		pref.plotPadding = new RectangleInsets(10, 0, 0, pref.tickLabelFontSize + 15);
		return pref;
	}
	
	/**
	 * Default OpenSHA screen figure (reports,  plots specified in pixels) plot preferences with big fonts
	 * @return
	 */
	public static PlotPreferences getDefaultScreenFigurePrefs() {
		PlotPreferences pref = new PlotPreferences();
		pref.tickLabelFontSize = 18;
		pref.axisLabelFontSize = 24;
		pref.plotLabelFontSize = 24;
		pref.legendFontSize = 20;
		pref.backgroundColor = Color.WHITE;
		pref.subplotGap = 30;
		
		// axis padding, read as top, left, bottom, right
		
		// this creates right insets that are comparable to that taken up by the left of the plot w/ labels
		// also adds a little padding on top
		pref.plotPadding = new RectangleInsets(10, 0, 0, pref.tickLabelFontSize + 15);
		return pref;
	}
	
	/**
	 * Nice defaults for print figures with sizes specified in points, e.g., for true-to-size fonts when used with
	 * {@link PlotUtils#writePrintPlots(java.io.File, String, GraphPanel, double, double, int, boolean, boolean, boolean)}
	 * @return
	 */
	public static PlotPreferences getDefaultPrintFigurePrefs() {
		PlotPreferences pref = new PlotPreferences();
		pref.tickLabelFontSize = 8;
		pref.axisLabelFontSize = 10;
		pref.plotLabelFontSize = 12;
		pref.legendFontSize = 8;
		pref.backgroundColor = Color.WHITE;
		pref.subplotGap = 10;
		
		// axis padding, read as top, left, bottom, right
		
		// this creates right insets that are comparable to that taken up by the left of the plot w/ labels
		// also adds a little padding on top
		pref.plotPadding = new RectangleInsets(pref.tickLabelFontSize/2d, 0, 0, pref.tickLabelFontSize);
		// these are tighter than the jfree defaults
		pref.axisLabelPadding = new RectangleInsets(0, 1, 0, 1);
		pref.axisTickLabelPaddingX = new RectangleInsets(0, 5, 0, 5);
		pref.axisTickLabelPaddingY = new RectangleInsets(3, 1, 3, 1);
		pref.legendBorderThickness = 0.5;
		pref.legendPadding = new RectangleInsets(1, 1, 1, 1);
		pref.legendItemLabelPadding = new RectangleInsets(0, 2, 0, 2);
		pref.legendItemGraphicPadding = new RectangleInsets(1, 2, 1, 2);
		pref.cptPadding = 3d;
		pref.cptStripWidth = 8d;
		pref.cptTickMinorLength = 1d;
		pref.cptTickLength = 2d;
		pref.legendLineLength = 10d;
		pref.trueSymbolSizing = true;
		return pref;
	}
	
	private PlotPreferences() {
		
	}
	
	public int getAxisLabelFontSize() {
		return axisLabelFontSize;
	}

	public void setAxisLabelFontSize(int axisLabelFontSize) {
		this.axisLabelFontSize = axisLabelFontSize;
		fireChangeEvent();
	}

	public int getTickLabelFontSize() {
		return tickLabelFontSize;
	}

	public void setTickLabelFontSize(int tickLabelFontSize) {
		this.tickLabelFontSize = tickLabelFontSize;
		fireChangeEvent();
	}

	public int getPlotLabelFontSize() {
		return plotLabelFontSize;
	}

	public void setPlotLabelFontSize(int plotLabelFontSize) {
		this.plotLabelFontSize = plotLabelFontSize;
		fireChangeEvent();
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
		fireChangeEvent();
	}

	public int getLegendFontSize() {
		return legendFontSize;
	}

	public void setLegendFontSize(int legendFontSize) {
		this.legendFontSize = legendFontSize;
	}

	public void addChangeListener(ChangeListener l) {
		listeners.add(l);
	}
	
	public boolean removeChangeListener(ChangeListener l) {
		return listeners.remove(l);
	}
	
	private void fireChangeEvent() {
		if (listeners.isEmpty())
			return;
		ChangeEvent e = new ChangeEvent(this);
		for (ChangeListener l : listeners)
			l.stateChanged(e);
	}

	public Color getInsetLegendBackground() {
		return insetLegendBackground;
	}

	public void setInsetLegendBackground(Color insetLegendBackground) {
		this.insetLegendBackground = insetLegendBackground;
	}

	public Color getInsetLegendBorder() {
		return insetLegendBorder;
	}

	public void setInsetLegendBorder(Color insetLegendBorder) {
		this.insetLegendBorder = insetLegendBorder;
	}
	
	public void scaleFontSizes(double scalar) {
		axisLabelFontSize = (int)(axisLabelFontSize*scalar + 0.5);
		tickLabelFontSize = (int)(tickLabelFontSize*scalar + 0.5);
		plotLabelFontSize = (int)(plotLabelFontSize*scalar + 0.5);
		legendFontSize = (int)(legendFontSize*scalar + 0.5);
		fireChangeEvent();
	}
	
	public void setSizeScalar(double sizeScalar) {
		this.sizeScalar = sizeScalar;
		fireChangeEvent();
	}
	
	public double getSizeScalar() {
		return sizeScalar;
	}
	
	public double getSubplotGap() {
		return subplotGap;
	}

	public void setSubplotGap(double subplotGap) {
		this.subplotGap = subplotGap;
	}

	public RectangleInsets getPlotPadding() {
		return plotPadding;
	}

	public void setPlotPadding(RectangleInsets plotPadding) {
		this.plotPadding = plotPadding;
	}

	public RectangleInsets getAxisLabelPadding() {
		return axisLabelPadding;
	}

	public void setAxisLabelPadding(RectangleInsets axisLabelPadding) {
		this.axisLabelPadding = axisLabelPadding;
	}

	public RectangleInsets getAxisTickLabelPaddingX() {
		return axisTickLabelPaddingX;
	}

	public RectangleInsets getAxisTickLabelPaddingY() {
		return axisTickLabelPaddingY;
	}

	public void setAxisTickLabelPadding(RectangleInsets axisTickLabelPaddingX, RectangleInsets axisTickLabelPaddingY) {
		this.axisTickLabelPaddingX = axisTickLabelPaddingX;
		this.axisTickLabelPaddingY = axisTickLabelPaddingY;
	}

	public double getLegendBorderThickness() {
		return legendBorderThickness;
	}

	public void setLegendBorderThickness(double legendBorderThickness) {
		this.legendBorderThickness = legendBorderThickness;
	}

	public RectangleInsets getLegendPadding() {
		return legendPadding;
	}

	public void setLegendPadding(RectangleInsets legendPadding) {
		this.legendPadding = legendPadding;
	}

	public RectangleInsets getLegendItemLabelPadding() {
		return legendItemLabelPadding;
	}

	public void setLegendItemLabelPadding(RectangleInsets legendItemLabelPadding) {
		this.legendItemLabelPadding = legendItemLabelPadding;
	}

	public RectangleInsets getLegendItemGraphicPadding() {
		return legendItemGraphicPadding;
	}

	public void setLegendItemGraphicPadding(RectangleInsets legendItemGraphicPadding) {
		this.legendItemGraphicPadding = legendItemGraphicPadding;
	}

	public double getLegendLineLength() {
		return legendLineLength;
	}

	public void setLegendLineLength(double legendLineLength) {
		this.legendLineLength = legendLineLength;
	}

	public double getCptPadding() {
		return cptPadding;
	}

	public void setCptPadding(double cptPadding) {
		this.cptPadding = cptPadding;
	}

	public double getCptStripWidth() {
		return cptStripWidth;
	}

	public void setCptStripWidth(double cptStripWidth) {
		this.cptStripWidth = cptStripWidth;
	}

	public double getCptTickLength() {
		return cptTickLength;
	}

	public void setCptTickLength(double cptTickLength) {
		this.cptTickLength = cptTickLength;
	}

	public double getCptTickMinorLength() {
		return cptTickMinorLength;
	}

	public void setCptTickMinorLength(double cptTickMinorLength) {
		this.cptTickMinorLength = cptTickMinorLength;
	}

	public int getTitleMaxLines() {
		return titleMaxLines;
	}

	public void setTitleMaxLines(int titleMaxLines) {
		this.titleMaxLines = titleMaxLines;
	}

	public boolean isTrueSymbolSizing() {
		return trueSymbolSizing;
	}

	public void setTrueSymbolSizing(boolean trueSymbolSizing) {
		this.trueSymbolSizing = trueSymbolSizing;
	}

	public int getSolidLineCap() {
		return solidLineCap;
	}

	public void setSolidLineCap(int solidLineCap) {
		this.solidLineCap = solidLineCap;
	}

	public int getSolidLineJoin() {
		return solidLineJoin;
	}

	public void setSolidLineJoin(int solidLineJoin) {
		this.solidLineJoin = solidLineJoin;
	}

	public int getDashedLineCap() {
		return dashedLineCap;
	}

	public void setDashedLineCap(int dashedLineCap) {
		this.dashedLineCap = dashedLineCap;
	}

	public int getDashedLineJoin() {
		return dashedLineJoin;
	}

	public void setDashedLineJoin(int dashedLineJoin) {
		this.dashedLineJoin = dashedLineJoin;
	}

	public PlotPreferences clone() {
		PlotPreferences ret = new PlotPreferences();
		ret.axisLabelFontSize = axisLabelFontSize;
		ret.tickLabelFontSize = tickLabelFontSize;
		ret.plotLabelFontSize = plotLabelFontSize;
		ret.legendFontSize = legendFontSize;
		ret.backgroundColor = backgroundColor;
		ret.insetLegendBackground = insetLegendBackground;
		ret.insetLegendBorder = insetLegendBorder;
		ret.subplotGap = subplotGap;
		ret.sizeScalar = sizeScalar;
		ret.plotPadding = plotPadding;
		ret.axisLabelPadding = axisLabelPadding;
		ret.axisTickLabelPaddingX = axisTickLabelPaddingX;
		ret.axisTickLabelPaddingY = axisTickLabelPaddingY;
		ret.legendBorderThickness = legendBorderThickness;
		ret.legendPadding = legendPadding;
		ret.legendItemGraphicPadding = legendItemGraphicPadding;
		ret.legendItemLabelPadding = legendItemLabelPadding;
		ret.cptPadding = cptPadding;
		ret.cptStripWidth = cptStripWidth;
		ret.cptTickLength = cptTickLength;
		ret.cptTickMinorLength = cptTickMinorLength;
		ret.titleMaxLines = titleMaxLines;
		ret.legendLineLength = legendLineLength;
		ret.trueSymbolSizing = trueSymbolSizing;
		ret.solidLineCap = solidLineCap;
		ret.solidLineJoin = solidLineJoin;
		ret.dashedLineCap = dashedLineCap;
		ret.dashedLineJoin = dashedLineJoin;
		// don't copy listeners
		return ret;
	}

}
