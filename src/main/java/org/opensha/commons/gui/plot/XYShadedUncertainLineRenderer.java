package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.JFrame;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.jfreechart.DiscretizedFunctionXYDataSet;
import org.opensha.commons.gui.plot.jfreechart.JFreeLogarithmicAxis;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

public class XYShadedUncertainLineRenderer extends AbstractXYItemRenderer {
	
	private double fillTrans;
	
	public XYShadedUncertainLineRenderer() {
		this(Double.NaN);
	}
	
	public XYShadedUncertainLineRenderer(double fillTrans) {
		this.fillTrans = fillTrans;
	}

	@Override
	public synchronized void drawItem(Graphics2D g2, XYItemRendererState state,
			Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
			ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset,
			int series, int item, CrosshairState crosshairState, int pass) {
//		System.out.println("Draw called. series="+series+", item="+item+", pass="+pass);
		
		if (!(dataset instanceof DiscretizedFunctionXYDataSet
				&& ((DiscretizedFunctionXYDataSet)dataset).getXYDataset(series) instanceof UncertainArbDiscDataset)) {
			System.err.println("Isn't an UncertainArbDiscrDataset, doing nothing!");
			return;
		}
		
//		System.out.println("Rendering uncertain!");
		
		UncertainArbDiscDataset uData = (UncertainArbDiscDataset)((DiscretizedFunctionXYDataSet)dataset).getXYDataset(series);
		
		boolean logX = domainAxis instanceof JFreeLogarithmicAxis;
		boolean logY = rangeAxis instanceof JFreeLogarithmicAxis;
		
		// find starting item number
		int lastIndexBefore = -1;
		int firstIndexAfter = -1;
		double lowerX = domainAxis.getLowerBound();
		double upperX = domainAxis.getUpperBound();
		for (int i=0; i<dataset.getItemCount(series); i++) {
			double x = dataset.getXValue(series, i);
//			double minY = uData.getLowerY(i);
//			double maxY = uData.getUpperY(i);
			
			if (x < lowerX || lastIndexBefore < 0) {
				lastIndexBefore = i;
			}
			if (x > upperX || i == dataset.getItemCount(series)-1) {
				firstIndexAfter = i;
				break;
			}
		}
		
//		System.out.println("LastIndexBefore="+lastIndexBefore+", lowerX="+lowerX);
		
		if (item != lastIndexBefore)
			// only plot once
			return;
		
//		System.out.println("Drawing a polygon!");
		
		List<Point2D> outline = Lists.newArrayList();
		outline.add(getLogCompatible(logX, logY, uData.get(lastIndexBefore)));
		for (int i=lastIndexBefore; i<=firstIndexAfter; i++)
			outline.add(getLogCompatible(logX, logY, new Point2D.Double(uData.getX(i), uData.getUpperY(i))));
		outline.add(getLogCompatible(logX, logY, uData.get(firstIndexAfter)));
		for (int i=firstIndexAfter+1; --i>=lastIndexBefore;)
			outline.add(getLogCompatible(logX, logY, new Point2D.Double(uData.getX(i), uData.getLowerY(i))));
		
		Polygon p = XYSolidBarRenderer.buildPolygon(dataArea, plot, domainAxis, rangeAxis,
				outline);
		
		Paint paint = getItemPaint(series, item);
		Preconditions.checkState(paint instanceof Color);
		if (Double.isNaN(fillTrans)) {
			g2.setPaint(paint);
		} else {
			Color lineColor = (Color)paint;
			Color fillColor = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int)(255d*fillTrans));
			g2.setPaint(fillColor);
		}
		g2.fillPolygon(p);
	}
	
	private static Point2D getLogCompatible(boolean logX, boolean logY, Point2D pt) {
		if (!logX && !logY)
			return pt;
		double x = pt.getX(), y = pt.getY();
//		if (logX) {
//			x = Math.log10(x);
//		}
		if (logY) {
			y = Math.max(y, GraphPanel.LOG_Y_MIN_VAL);
//			y = Math.log10(Math.max(y, GraphPanel.LOG_Y_MIN_VAL));
		}
		return new Point2D.Double(x, y);
	}
	
	public static void main(String[] args) {
		ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		
		for (int i=0; i<10; i++) {
			double x = i;
			double y = 0.5 + Math.random();
			meanFunc.set(x, y);
			upperFunc.set(x, y + 0.2*Math.random());
			lowerFunc.set(x, y - 0.2*Math.random());
		}
		
		UncertainArbDiscDataset uncertainFunc = new UncertainArbDiscDataset(meanFunc, lowerFunc, upperFunc);
		
		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.add(uncertainFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 2f, Color.BLUE));
		funcs.add(uncertainFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		GraphWindow gw = new GraphWindow(funcs, "Demo", chars);
		gw.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
