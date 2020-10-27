package org.opensha.commons.gui.plot;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.ui.RectangleEdge;

import com.google.common.collect.Lists;

/**
 * Custom item renderer for drawing thick bars where the thickness is in actual y axis units.
 * Useful for things like tornado diagrams.
 * 
 * Limitations: gaps will be drawn in multi segment lines.
 * @author kevin
 *
 */
public class XYSolidBarRenderer extends AbstractXYItemRenderer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private float thicknessY;

	public XYSolidBarRenderer(float thicknessY) {
		this.thicknessY = thicknessY;
	}

	@Override
	public void drawItem(Graphics2D g2, XYItemRendererState state,
			Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
			ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset,
			int series, int item, CrosshairState crosshairState, int pass) {
//		System.out.println("Called with series="+series+", item="+item);

		if (item < 1)
			// nothing to draw
			return;
		
		double origX0 = dataset.getXValue(series, item-1);
		double origY0 = dataset.getYValue(series, item-1);
		double origX1 = dataset.getXValue(series, item);
		double origY1 = dataset.getYValue(series, item);

		// thickness Y is hypotenuse
		double angle = Math.atan((origY1 - origY0)/(origX1 - origX0));
		//			System.out.println("Angle: "+Math.toDegrees(angle)+" deg");
		double xAdd = thicknessY * Math.sin(angle);
		double yAdd = thicknessY * Math.cos(angle);
		//			System.out.println("xAdd = "+xAdd);
		//			System.out.println("yAdd = "+yAdd);

		double topLeftX = origX0 - xAdd;
		double topLeftY = origY0 + yAdd;

		double botLeftX = origX0 + xAdd;
		double botLeftY = origY0 - yAdd;

		double topRightX = origX1 - xAdd;
		double topRightY = origY1 + yAdd;

		double botRightX = origX1 + xAdd;
		double botRightY = origY1 - yAdd;

		List<Point2D> outline = Lists.newArrayList();
		outline.add(new Point2D.Double(topLeftX, topLeftY));
		outline.add(new Point2D.Double(topRightX, topRightY));
		outline.add(new Point2D.Double(botRightX, botRightY));
		outline.add(new Point2D.Double(botLeftX, botLeftY));

		Polygon p = buildPolygon(dataArea, plot, domainAxis, rangeAxis,
				outline);

		Paint paint = getItemPaint(series, item);
		if (paint != null) {
			//				System.out.println("Paint: "+paint);
			g2.setPaint(paint);
			g2.fillPolygon(p);
		}

		// add an entity for the item...
		if (info != null) {
			EntityCollection entities = info.getOwner().getEntityCollection();
			if (entities != null) {
				String tip = null;
				XYToolTipGenerator generator = getToolTipGenerator(series, 
						item);
				if (generator != null) {
					tip = generator.generateToolTip(dataset, series, item);
				}
				String url = null;
				if (getURLGenerator() != null) {
					url = getURLGenerator().generateURL(dataset, series, item);
				}
				XYItemEntity entity = new XYItemEntity(p, dataset, series, 
						item, tip, url);
				entities.add(entity);
			}
		}
	}

	public static Polygon buildPolygon(Rectangle2D dataArea, XYPlot plot,
			ValueAxis domainAxis, ValueAxis rangeAxis, Collection<Point2D> outline) {
		PlotOrientation orientation = plot.getOrientation();
		RectangleEdge domainEdge = Plot.resolveDomainAxisLocation(
				plot.getDomainAxisLocation(), orientation);
		RectangleEdge rangeEdge = Plot.resolveRangeAxisLocation(
				plot.getRangeAxisLocation(), orientation);
		Polygon p = new Polygon();
		
		for (Point2D pt : outline) {
			int pX = (int)(domainAxis.valueToJava2D(pt.getX(), dataArea, domainEdge)+0.5);
			int pY = (int)(rangeAxis.valueToJava2D(pt.getY(), dataArea, rangeEdge)+0.5);
			
			if (orientation == PlotOrientation.HORIZONTAL)
				p.addPoint(pY, pX);
			else
				p.addPoint(pX, pY);
		}
		
		return p;
	}

}
