package org.opensha.commons.gui.plot;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.LegendItemSource;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.block.ColumnArrangement;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.data.function.DiscretizedFunc;

import com.google.common.collect.Lists;

/**
 * Plot specification, contains all data for a 2D plot.
 * 
 * @author kevin
 *
 */
public class PlotSpec implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private List<? extends PlotElement> elems;
	private List<PlotCurveCharacterstics> chars;
	private String title, xAxisLabel, yAxisLabel;
	private List<XYAnnotation> annotations;
	private List<Title> subtitles;
	
	// for standard legend
	private boolean legend = false;
	private boolean legendSkipBlank = true;
	private RectangleEdge legendLocation = RectangleEdge.BOTTOM;
	private LegendItemCollection customLegendCollection = null;
	
	// for inset legend
	private boolean insetLegend = false;
	private RectangleAnchor insetLegendLocation = RectangleAnchor.TOP_RIGHT;
	private double insetLegendRelX = 0.95;
	private double insetLegendRelY = 0.95;
	private double insetLegendMaxWidth = 0.35;
	private boolean insetLegendSingleColumn = true;
	
	/**
	 * 
	 * @param elems	list of elements to plot
	 * @param chars	list of plot curve characteristics, or null for default colors
	 * @param title	plot title
	 * @param xAxisLabel	x axis label
	 * @param yAxisLabel	y axis label
	 */
	public PlotSpec(List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> chars, String title, String xAxisLabel, String yAxisLabel) {
		this.elems = elems;
		this.chars = chars;
		this.title = title;
		this.xAxisLabel = xAxisLabel;
		this.yAxisLabel = yAxisLabel;
	}

	/**
	 * 
	 * @return list of all plot elements (unmodifiable)
	 */
	public List<? extends PlotElement> getPlotElems() {
		return Collections.unmodifiableList(elems);
	}
	
	/**
	 * 
	 * @return list of all plot elements that are also Discretized Functions
	 */
	public List<DiscretizedFunc> getPlotFunctionsOnly() {
		List<DiscretizedFunc> funcs = Lists.newArrayList();
		for (PlotElement e : elems)
			if (e instanceof DiscretizedFunc)
				funcs.add((DiscretizedFunc)e);
		return funcs;
	}

	/**
	 * Set the list of plot elements
	 * 
	 * @param elems
	 */
	public void setPlotElems(List<? extends PlotElement> elems) {
		this.elems = elems;
	}
	
	/**
	 * Set the list of plot annotations (or null for no annotations). Note that any line annotations
	 * will use default rendering (black 1pt line).
	 * 
	 * @param annotations
	 */
	public void setPlotAnnotations(List<? extends XYAnnotation> annotations) {
		this.annotations = new ArrayList<>(annotations);
	}
	
	public void addPlotAnnotation(XYAnnotation annotation) {
		if (annotations == null)
			annotations = new ArrayList<>();
		annotations.add(annotation);
	}
	
	public List<XYAnnotation> getPlotAnnotations() {
		return annotations;
	}
	
	/**
	 * Set the list of plot subtitles (such as a color scale label)
	 * 
	 * @param annotations
	 */
	public void setSubtitles(List<? extends Title> subtitles) {
		this.subtitles = new ArrayList<>(subtitles);
	}
	
	public void addSubtitle(Title subtitle) {
		if (subtitles == null)
			subtitles = new ArrayList<>();
		subtitles.add(subtitle);
	}
	
	public List<Title> getSubtitles() {
		return subtitles;
	}

	public List<PlotCurveCharacterstics> getChars() {
		return chars;
	}
	
	public void setChars(List<PlotCurveCharacterstics> chars) {
		this.chars = chars;
	}

	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}

	public String getXAxisLabel() {
		return xAxisLabel;
	}
	
	public void setXAxisLabel(String xAxisLabel) {
		this.xAxisLabel = xAxisLabel;
	}

	public String getYAxisLabel() {
		return yAxisLabel;
	}
	
	public void setYAxisLabel(String yAxisLabel) {
		this.yAxisLabel = yAxisLabel;
	}

	public boolean isLegendVisible() {
		return legend;
	}

	/**
	 * Used to enable the legend (disabled by default)
	 * @param legend
	 */
	public void setLegendVisible(boolean legend) {
		this.legend = legend;
	}
	
	public boolean isLegendSkipBlank() {
		return legendSkipBlank;
	}
	
	public void setLegendSkipBlank(boolean legendSkipBlank) {
		this.legendSkipBlank = legendSkipBlank;
	}

	public RectangleEdge getLegendLocation() {
		return legendLocation;
	}

	/**
	 * Sets the location of the Legend if visible. Default is BOTTOM
	 * @param legendLocation
	 */
	public void setLegendLocation(RectangleEdge legendLocation) {
		this.legendLocation = legendLocation;
	}

	public LegendItemCollection getLegendItems(XYPlot plot) {
		if (customLegendCollection != null)
			return customLegendCollection;
		// build it
		LegendItemCollection items = plot.getLegendItems();
		for (int i=0; i<items.getItemCount(); i++) {
			LegendItem legend = items.get(i);
			Shape lineShape = legend.getLine();
			Stroke stroke = legend.getLineStroke();
			if (lineShape instanceof Line2D
					&& stroke instanceof BasicStroke
					&& ((BasicStroke)stroke).getEndCap() == BasicStroke.CAP_BUTT) {
				// widen legends for dashed and dotted lines
				Line2D line = (Line2D)lineShape;
				line.setLine(line.getX1()*2d, line.getY1(),
						line.getX2()*2d, line.getY2());
			}
		}
		if (isLegendSkipBlank()) {
			LegendItemCollection newItems = new LegendItemCollection();
			for (int i=0; i<items.getItemCount(); i++) {
				LegendItem item = items.get(i);
				String label = item.getLabel();
				if (label != null && !label.isEmpty())
					newItems.add(item);
			}
			items = newItems;
		}
		return items;
	}

	/**
	 * Can be used to set a custom list of Legend items, or null for auto legend.
	 * @param customLegendCollection
	 */
	public void setCustomLegendItems(
			LegendItemCollection customLegendCollection) {
		this.customLegendCollection = customLegendCollection;
	}
	
	/**
	 * Creates an inset legend with default settings (top right, 0.95 relative x/y, single column, max 35%
	 * width).
	 * 
	 * @param insetLegend
	 */
	public void setLegendInset(boolean insetLegend) {
		if (insetLegend)
			this.legend = true;
		this.insetLegend = insetLegend;
	}
	
	/**
	 * Creates a legend that is inset in the plot. Function names will be used to populate the legend
	 * (null names will be skipped unless you call setLegendSkipBlank(true)). Coordinates are relative
	 * and in the range of [0,1]. If you set the anchor to TOP_RIGHT, then the relX coordinate will be
	 * the x location of the right edge of the legend, and the relY coordinate the y location of the top
	 * edge.
	 * 
	 * @param anchor location of the inset legend, e.g., RectangleAnchor.TOP_RIGHT
	 * @param relX relative x coordinate of the legend in the range [0,1]
	 * @param relY relative y coordinate of the legend in the range [0,1]
	 * @param maxWidth maximum relative width of the legend in the range (0,1]
	 * @param singleColumn if true, force each item to be on a new line
	 */
	public void setLegendInset(RectangleAnchor anchor, double relX, double relY, double maxWidth,
			boolean singleColumn) {
		this.legend = true;
		this.insetLegend = true;
		this.insetLegendLocation = anchor;
		this.insetLegendRelX = relX;
		this.insetLegendRelY = relY;
		this.insetLegendMaxWidth = maxWidth;
		this.insetLegendSingleColumn = singleColumn;
	}
	
	public boolean isLegendInset() {
		return insetLegend;
	}
	
	public XYTitleAnnotation buildInsetLegend(LegendItemCollection items, PlotPreferences plotPrefs) {
//		new LegendTitle(source, hLayout, vLayout)
		LegendItemSource source = new LegendItemSource() {
			
			@Override
			public LegendItemCollection getLegendItems() {
				return items;
			}
		};
		LegendTitle lt;
		if (insetLegendSingleColumn)
			lt = new LegendTitle(source, new ColumnArrangement(), new ColumnArrangement());
		else
			lt = new LegendTitle(source);
		Font legendFont = lt.getItemFont();
		lt.setItemFont(new Font(legendFont.getName(), legendFont.getStyle(), plotPrefs.getLegendFontSize()));
		lt.setBackgroundPaint(plotPrefs.getInsetLegendBackground());
		if (plotPrefs.getInsetLegendBorder() != null)
			lt.setFrame(new BlockBorder(plotPrefs.getInsetLegendBorder()));
		RectangleEdge edge;
		if (insetLegendLocation == RectangleAnchor.BOTTOM
				|| insetLegendLocation == RectangleAnchor.BOTTOM_LEFT
				|| insetLegendLocation == RectangleAnchor.BOTTOM_RIGHT)
			edge = RectangleEdge.BOTTOM;
		else
			edge = RectangleEdge.TOP;
		lt.setPosition(edge);
		XYTitleAnnotation ann = new XYTitleAnnotation(insetLegendRelX, insetLegendRelY,
				lt, RectangleAnchor.TOP_RIGHT);
		ann.setMaxWidth(insetLegendMaxWidth);
		return ann;
	}
}
