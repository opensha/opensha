package org.opensha.commons.gui.plot.jfreechart;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.PaintScaleWrapper;

import com.google.common.base.Preconditions;

/**
 * This fixes a bug in PaintScaleLegend wherein log-spaced colorbars are split into subdivisions in linear space,
 * resulting in larger blocks for smaller values.
 */
public class LogPaintScaleLegend extends PaintScaleLegend {

	private JFreeLogarithmicAxis axis;
	private PaintScaleWrapper scale;

	public LogPaintScaleLegend(PaintScaleWrapper scale, JFreeLogarithmicAxis axis) {
		super(scale, axis);
		Preconditions.checkState(scale.getCPT().isLog10(), "Passed in paint scale CPT isn't log10?");
		this.scale = scale;
		this.axis = axis;
	}

	@Override
	public Object draw(Graphics2D g2, Rectangle2D area, Object params) {
		// get values that we can't access directly
		Paint backgroundPaint = getBackgroundPaint();
		AxisLocation axisLocation = getAxisLocation();
		int subdivisions = getSubdivisionCount();
		double stripWidth = getStripWidth();
		Paint stripOutlinePaint = getStripOutlinePaint();
		Stroke stripOutlineStroke = getStripOutlineStroke();
		double axisOffset = getAxisOffset();

		Rectangle2D target = (Rectangle2D) area.clone();
		target = trimMargin(target);
		if (backgroundPaint != null) {
			g2.setPaint(backgroundPaint);
			g2.fill(target);
		}
		getFrame().draw(g2, target);
		getFrame().getInsets().trim(target);
		target = trimPadding(target);
		
		double lower   = axis.getLowerBound();          // positive (enforced by JFreeLogarithmicAxis)
		double upper   = axis.getUpperBound();
		Preconditions.checkState(lower > 0 && upper > lower,
				"Log axis bounds must be positive and ordered");

		double logLower = Math.log10(lower);
		double logUpper = Math.log10(upper);
		double logInc   = (logUpper - logLower) / subdivisions;

		// helper lambdas for brevity
		java.util.function.IntFunction<Double> v0 = i ->
		Math.pow(10, logLower + i       * logInc);          // band start
		java.util.function.IntFunction<Double> v1 = i ->
		Math.pow(10, logLower + (i + 1) * logInc);          // band end
		java.util.function.IntFunction<Double> vMid = i ->
		Math.pow(10, logLower + (i + 0.5) * logInc);        // band mid (for colour)

		Rectangle2D r = new Rectangle2D.Double();

		// ───────────────────────────────────────────
		// 3. render: same four cases as the stock draw()
		// ───────────────────────────────────────────
		if (RectangleEdge.isTopOrBottom(getPosition())) {

			RectangleEdge axisEdge = Plot.resolveRangeAxisLocation(
					axisLocation, PlotOrientation.HORIZONTAL);

			if (axisEdge == RectangleEdge.TOP) {
				for (int i = 0; i < subdivisions; i++) {
					double vv0 = axis.valueToJava2D(v0.apply(i), target, RectangleEdge.TOP);
					double vv1 = axis.valueToJava2D(v1.apply(i), target, RectangleEdge.TOP);
					double ww  = Math.abs(vv1 - vv0) + 1.0;

					r.setRect(Math.min(vv0, vv1), target.getMaxY() - stripWidth, ww, stripWidth);
					g2.setPaint(scale.getPaint(vMid.apply(i)));
					g2.fill(r);
				}
				if (isStripOutlineVisible()) {
					g2.setPaint(stripOutlinePaint);
					g2.setStroke(stripOutlineStroke);
					g2.draw(new Rectangle2D.Double(target.getMinX(),
							target.getMaxY() - stripWidth, target.getWidth(), stripWidth));
				}
				axis.draw(g2, target.getMaxY() - stripWidth - axisOffset,
						target, target, RectangleEdge.TOP, null);

			} else { // BOTTOM
				for (int i = 0; i < subdivisions; i++) {
					double vv0 = axis.valueToJava2D(v0.apply(i), target, RectangleEdge.BOTTOM);
					double vv1 = axis.valueToJava2D(v1.apply(i), target, RectangleEdge.BOTTOM);
					double ww  = Math.abs(vv1 - vv0) + 1.0;

					r.setRect(Math.min(vv0, vv1), target.getMinY(), ww, stripWidth);
					g2.setPaint(scale.getPaint(vMid.apply(i)));
					g2.fill(r);
				}
				if (isStripOutlineVisible()) {
					g2.setPaint(stripOutlinePaint);
					g2.setStroke(stripOutlineStroke);
					g2.draw(new Rectangle2D.Double(target.getMinX(),
							target.getMinY(), target.getWidth(), stripWidth));
				}
				axis.draw(g2, target.getMinY() + stripWidth + axisOffset,
						target, target, RectangleEdge.BOTTOM, null);
			}

		} else { // LEFT / RIGHT (vertical strip)

			RectangleEdge axisEdge = Plot.resolveRangeAxisLocation(
					axisLocation, PlotOrientation.VERTICAL);

			if (axisEdge == RectangleEdge.LEFT) {
				for (int i = 0; i < subdivisions; i++) {
					double vv0 = axis.valueToJava2D(v0.apply(i), target, RectangleEdge.LEFT);
					double vv1 = axis.valueToJava2D(v1.apply(i), target, RectangleEdge.LEFT);
					double hh  = Math.abs(vv1 - vv0) + 1.0;

					r.setRect(target.getMaxX() - stripWidth, Math.min(vv0, vv1), stripWidth, hh);
					g2.setPaint(scale.getPaint(vMid.apply(i)));
					g2.fill(r);
				}
				if (isStripOutlineVisible()) {
					g2.setPaint(stripOutlinePaint);
					g2.setStroke(stripOutlineStroke);
					g2.draw(new Rectangle2D.Double(target.getMaxX() - stripWidth,
							target.getMinY(), stripWidth, target.getHeight()));
				}
				axis.draw(g2, target.getMaxX() - stripWidth - axisOffset,
						target, target, RectangleEdge.LEFT, null);

			} else { // RIGHT
				for (int i = 0; i < subdivisions; i++) {
					double vv0 = axis.valueToJava2D(v0.apply(i), target, RectangleEdge.LEFT);
					double vv1 = axis.valueToJava2D(v1.apply(i), target, RectangleEdge.LEFT);
					double hh  = Math.abs(vv1 - vv0) + 1.0;

					r.setRect(target.getMinX(), Math.min(vv0, vv1), stripWidth, hh);
					g2.setPaint(scale.getPaint(vMid.apply(i)));
					g2.fill(r);
				}
				if (isStripOutlineVisible()) {
					g2.setPaint(stripOutlinePaint);
					g2.setStroke(stripOutlineStroke);
					g2.draw(new Rectangle2D.Double(target.getMinX(),
							target.getMinY(), stripWidth, target.getHeight()));
				}
				axis.draw(g2, target.getMinX() + stripWidth + axisOffset,
						target, target, RectangleEdge.RIGHT, null);
			}
		}
		return null;   // PaintScaleLegend always returns null
	}

}
