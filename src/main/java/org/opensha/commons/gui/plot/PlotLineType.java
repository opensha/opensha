package org.opensha.commons.gui.plot;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.NoSuchElementException;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.xy.XYDataset;

import com.google.common.base.Preconditions;

/**
 * These are the supported line syles used in OpenSHA JFreeChart plots.
 * 
 * @author kevin
 *
 */
public enum PlotLineType {
	
	SOLID("Solid"),
	DOTTED("Dotted"),
	DASHED("Dashed"),
	SHORT_DASHED("Short Dashed"),
	DOTTED_AND_DASHED("Dotted & Dashed"),
	HISTOGRAM("Histogram"),
	STACKED_BAR("Stacked Bar"),
	SOLID_BAR("Solid Bar"),
	/**
	 * Shaded uncertainty bounds, can only be used with UncertainArbDiscDataset
	 */
	SHADED_UNCERTAIN("Shaded Uncertain Dataset"),
	/**
	 * Shaded uncertainty bounds with default 50% transparency, can only be used with UncertainArbDiscDataset
	 */
	SHADED_UNCERTAIN_TRANS("Shaded Transparent Uncertain Dataset"),
	/**
	 * Polygon with solid fill, edges are not drawn. Path is closed automatically.
	 */
	POLYGON_SOLID("Polygon with solid fill");
	
	private String desc;
	
	private PlotLineType(String desc) {
		this.desc = desc;
	}
	
	@Override
	public String toString() {
		return desc;
	}
	
	public Stroke buildStroke(float lineWidth) {
		Preconditions.checkArgument(lineWidth>0, "Line width must be >0");
		if (this == SOLID)
			return new BasicStroke(lineWidth);
		else if (this == DOTTED)
			return new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_BEVEL,0,new float[] {Float.min(6, Float.max(lineWidth*0.7f, 1))},0);
		else if (this == DASHED)
			return new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_BEVEL,0,new float[] {9},0);
		else if (this == SHORT_DASHED)
			return new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_BEVEL,0,new float[] {4},0);
		else if (this == DOTTED_AND_DASHED)
			return new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_BEVEL,0,new float[] {5,3,2,3},0);
		else
			throw new IllegalStateException("Stroke not applicable for lineType: "+this);
	}
	
	public static PlotLineType forString(String desc) {
		for (PlotLineType plt : values()) {
			if (plt.desc.equalsIgnoreCase(desc))
				return plt;
		}
		throw new NoSuchElementException("No line type exists for '"+desc+"'");
	}
	
	/**
	 * Builds a render for the given <code>PlotLineType</code> and/or <code>PlotSymbol</code>.
	 * 
	 * @param plt plot line type, or null for none
	 * @param sym plot symbol type, or null for none
	 * @param width width of the line or symbol. if line and symbol, symbol width will equal <code>4*width</code>
	 * @throws IllegalStateException when both plt and sym are null
	 * @return
	 */
	public static XYItemRenderer buildRenderer(PlotLineType plt, PlotSymbol sym, float width)
	throws IllegalStateException {
		if (plt == null)
			return buildRenderer(plt, sym, width, width);
		else
			return buildRenderer(plt, sym, width, width*4);
	}
	
	/**
	 * @return true if the line type is compatible with symbols, false otherwise
	 */
	public boolean isSymbolCompatible() {
		return !(this == HISTOGRAM || this == STACKED_BAR || this == SOLID_BAR
				|| this == SHADED_UNCERTAIN_TRANS || this == SHADED_UNCERTAIN || this == POLYGON_SOLID);
	}
	
	public static void checkValidConfiguration(PlotLineType plt, PlotSymbol sym) {
		Preconditions.checkState(sym != null || plt != null,
				"Must supply either a plot line type, or a symbol.");
		Preconditions.checkState(plt == null || (sym == null || plt.isSymbolCompatible()),
				"A symbol cannot be suplied with a line type that doesn't support symbols.");
	}
	
	/**
	 * Builds a render for the given <code>PlotLineType</code> and/or <code>PlotSymbol</code>.
	 * 
	 * @param plt plot line type, or null for none
	 * @param sym plot symbol type, or null for none
	 * @param lineWidth width of the line, if not null
	 * @param symWidth width of the symbols, if not null
	 * @throws IllegalStateException when both plt and sym are null
	 * @return
	 */
	public static XYItemRenderer buildRenderer(PlotLineType plt, PlotSymbol sym, float lineWidth, float symWidth)
	throws IllegalStateException {
		checkValidConfiguration(plt, sym);
		XYItemRenderer renderer = null;
		// will usually use this
		XYLineAndShapeRenderer lineShpRend = new XYLineAndShapeRenderer(plt != null, sym != null);
		lineShpRend.setDrawSeriesLineAsPath(true);
		if (plt != null) {
			Preconditions.checkArgument(plt ==  SHADED_UNCERTAIN || lineWidth > 0, "line widht must be > 0");
			if (plt == HISTOGRAM) {
				XYBarRenderer xyRend = new XYBarRenderer();
				xyRend.setShadowVisible(false);
				xyRend.setMargin(0.1);
				xyRend.setBarPainter(new StandardXYBarPainter());
				renderer = xyRend;
			} else if (plt == STACKED_BAR) {
				StackedXYBarRenderer sbRend = new StackedXYBarRenderer();
				sbRend.setShadowVisible(false);
				renderer = sbRend;
			} else if (plt == SOLID_BAR) {
				renderer = new XYSolidBarRenderer(lineWidth);
			} else if (plt == SHADED_UNCERTAIN) {
				renderer = new XYShadedUncertainLineRenderer();
			} else if (plt == SHADED_UNCERTAIN_TRANS) {
				renderer = new XYShadedUncertainLineRenderer(0.5);
			} else if (plt == POLYGON_SOLID){
//				renderer = new XYAreaRenderer(XYAreaRenderer.AREA);
				renderer = new CustomXYAreaRenderer();
			} else {
				renderer = lineShpRend;
				Stroke stroke = plt.buildStroke(lineWidth);
//				renderer.setStroke(stroke);
//				renderer.setDefaultStroke(stroke);
				renderer.setSeriesStroke(0, stroke);
//				renderer.setBaseStroke(stroke);
			}
		}
		if (sym != null) {
			if (renderer == null)
				renderer = lineShpRend;
			else {
				Preconditions.checkState(renderer instanceof XYLineAndShapeRenderer,
						"Renderer already exists but isn't correct type for plt="+plt+" and sym="+sym);
			}
			Preconditions.checkArgument(symWidth > 0, "symbol widht must be >0");
			Shape shape = sym.buildShape(symWidth);
			Preconditions.checkNotNull(shape, "Couldn't build shape for symbol: "+sym);
//			renderer.setShape(shape);
			renderer.setSeriesShape(0, shape);
//			stdRend.setBaseShape(shape);
//			lineShpRend.setShapesFilled(sym.isFilled());
			lineShpRend.setSeriesShapesFilled(0, sym.isFilled());
//			stdRend.setBaseShapesFilled(sym.isFilled());
			
		}
		return renderer;
	}
	
	/**
	 * The default JFreeChart XYAreaRenderer is meant to fill areas down to the baseline axis, as in you're defining
	 * the top of the polygon and the bottom is always the axis. That makes sense for some charts, but not as we're
	 * using it. This just draws the passed in polygon instead.
	 */
	public static class CustomXYAreaRenderer extends XYLineAndShapeRenderer {

		@Override
		public void drawItem(Graphics2D g2, XYItemRendererState state,
				Rectangle2D dataArea, PlotRenderingInfo info,
				XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis,
				XYDataset dataset, int series, int item,
				CrosshairState crosshairState, int pass) {
			// Only render once per series (on the first item)
			if (item != 0) {
				return;
			}
			// this will be called twice, only plot on first pass
			if (pass != 0) {
				return;
			}

			// Prepare the path for the polygon
			Path2D polygon = new Path2D.Double();
			boolean firstPoint = true;

			int pointCount = dataset.getItemCount(series);
			double firstX = Double.NaN, firstY = Double.NaN;

			for (int i = 0; i < pointCount; i++) {
				double xValue = dataset.getXValue(series, i);
				double yValue = dataset.getYValue(series, i);

				// Convert data to screen coordinates
				double xScreen = domainAxis.valueToJava2D(xValue, dataArea, plot.getDomainAxisEdge());
				double yScreen = rangeAxis.valueToJava2D(yValue, dataArea, plot.getRangeAxisEdge());

				if (firstPoint) {
					polygon.moveTo(xScreen, yScreen);
					firstX = xScreen;
					firstY = yScreen;
					firstPoint = false;
				} else {
					polygon.lineTo(xScreen, yScreen);
				}
			}

			// Ensure the polygon is closed
			if (pointCount > 2) {
				polygon.closePath();
			}

			// Get the series paint for the fill color
			Paint seriesPaint = getItemPaint(series, 0);
			g2.setPaint(seriesPaint);

			// Fill the polygon
			g2.fill(polygon);
		}
	}

}
