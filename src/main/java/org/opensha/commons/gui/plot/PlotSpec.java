package org.opensha.commons.gui.plot;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.ui.RectangleEdge;
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
	private List<? extends XYAnnotation> annotations;
	
	private boolean legend = false;
	private boolean legendSkipBlank = true;
	private RectangleEdge legendLocation = RectangleEdge.BOTTOM;
	private LegendItemCollection customLegendCollection = null;
	
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
		this.annotations = annotations;
	}
	
	public List<? extends XYAnnotation> getPlotAnnotations() {
		return annotations;
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

	public LegendItemCollection getCustomLegendCollection() {
		return customLegendCollection;
	}

	/**
	 * Can be used to set a custom list of Legend items, or null for auto legend.
	 * @param customLegendCollection
	 */
	public void setCustomLegendCollection(
			LegendItemCollection customLegendCollection) {
		this.customLegendCollection = customLegendCollection;
	}
}
