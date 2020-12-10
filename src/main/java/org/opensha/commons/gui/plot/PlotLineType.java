package org.opensha.commons.gui.plot;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.NoSuchElementException;

import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

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
	SHADED_UNCERTAIN_TRANS("Shaded Transparent Uncertain Dataset");
	
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
				|| this == SHADED_UNCERTAIN_TRANS || this == SHADED_UNCERTAIN);
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

}
