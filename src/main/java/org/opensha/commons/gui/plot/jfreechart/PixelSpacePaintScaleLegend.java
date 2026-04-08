package org.opensha.commons.gui.plot.jfreechart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.PaintScaleWrapper;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;

import com.google.common.base.Preconditions;

/**
 * Extension of {@link PaintScaleLegend} that ignores the passed in subdivision count and instead spaces subdivisions
 * such that each are the given number of pixels wide (or high for vertical). This also works with log-spaced CPT files,
 * and draws discrete CPTs with a single box for each discrete color.
 */
public class PixelSpacePaintScaleLegend extends PaintScaleLegend {

	private PaintScaleWrapper scale;
	private ValueAxis axis;
	private double pixelsPerSpan;
	
	private static double OVERSHOOT_PIXELS = 1; // overshoot the bounds by this much to prevent tiny visible gaps

	public PixelSpacePaintScaleLegend(PaintScaleWrapper scale, ValueAxis axis, double pixelsPerSpan) {
		super(scale, axis);
		this.scale = scale;
		this.axis = axis;
		this.pixelsPerSpan = pixelsPerSpan;
	}

	@Override
	public Object draw(Graphics2D g2, Rectangle2D area, Object params) {
		// get values that we can't access directly
		Paint backgroundPaint = getBackgroundPaint();
		AxisLocation axisLocation = getAxisLocation();
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
		
		double lower   = axis.getLowerBound();
		double upper   = axis.getUpperBound();
		Preconditions.checkState(upper > lower,
				"Axis bound upper must be > lower");

		Rectangle2D r = new Rectangle2D.Double();
		
		// see if it's discrete
		boolean discrete = true;
		CPT cpt = scale.getCPT();
		for (CPTVal val : cpt) {
			if (!val.minColor.equals(val.maxColor)) {
				discrete = false;
				break;
			}
		}

		if (RectangleEdge.isTopOrBottom(getPosition())) {
			RectangleEdge axisEdge = Plot.resolveRangeAxisLocation(
					axisLocation, PlotOrientation.HORIZONTAL);
			
			double totalW = target.getWidth();
			
			double stripY, axisY;
			if (axisEdge == RectangleEdge.TOP) {
				stripY = target.getMaxY() - stripWidth;
				axisY = target.getMaxY() - stripWidth - axisOffset;
			} else {
				stripY = target.getMinY();
				axisY = target.getMinY() + stripWidth + axisOffset;
			}
			
			if (discrete) {
				for (int i=0; i<cpt.size(); i++) {
					CPTVal val = cpt.get(i);
					double startVal = val.start;
					double endVal = val.end;
					if (startVal == endVal)
						continue;
					if (cpt.isLog10()) {
						startVal = Math.pow(10, startVal);
						endVal = Math.pow(10, endVal);
					}
					double start = axis.valueToJava2D(startVal, target, axisEdge);
					double end = axis.valueToJava2D(endVal, target, axisEdge);
					if (i < cpt.size()-1)
						// we're not the last one, overshoot to the right by a bit to fill any gap;
						// this will be hidden under the next one
						end += OVERSHOOT_PIXELS;
					double width = end-start;
					r.setRect(start, stripY, width, stripWidth);
					g2.setPaint(val.minColor);
					g2.fill(r);
				}
			} else {
				int subdivisions = (int)Math.round(totalW/pixelsPerSpan);
				if (subdivisions < 10)
					subdivisions = 10;
				
				double fullSpan = totalW/subdivisions;
				double halfSpan = 0.5*fullSpan;
				
				for (int i = 0; i < subdivisions; i++) {
					double start = target.getMinX() + i*fullSpan;
					double middle = start + halfSpan;
					double width = fullSpan;
					if (i < subdivisions-1)
						// we're not the last one, overshoot to the right by a bit to fill any gap;
						// this will be hidden under the next one
						width += OVERSHOOT_PIXELS;
					double scaleValue = axis.java2DToValue(middle, target, axisEdge);

					r.setRect(start, stripY, width, stripWidth);
					g2.setPaint(scale.getPaint(scaleValue));
					g2.fill(r);
				}
			}
			if (isStripOutlineVisible()) {
				g2.setPaint(stripOutlinePaint);
				g2.setStroke(stripOutlineStroke);
				g2.draw(new Rectangle2D.Double(target.getMinX(),
						stripY, target.getWidth(), stripWidth));
			}
			axis.draw(g2, axisY, target, target, axisEdge, null);
		} else { // LEFT / RIGHT (vertical strip)

			RectangleEdge axisEdge = Plot.resolveRangeAxisLocation(
					axisLocation, PlotOrientation.VERTICAL);
			
			double stripX, axisX;
			if (axisEdge == RectangleEdge.LEFT) {
				stripX = target.getMaxX() - stripWidth;
				axisX = target.getMaxX() - stripWidth - axisOffset;
			} else {
				stripX = target.getMinX();
				axisX = target.getMinX() + stripWidth + axisOffset;
			}
			
			if (discrete) {
				for (int i=0; i<cpt.size(); i++) {
					CPTVal val = cpt.get(i);
					double startVal = val.start;
					double endVal = val.end;
					if (startVal == endVal)
						continue;
					if (cpt.isLog10()) {
						startVal = Math.pow(10, startVal);
						endVal = Math.pow(10, endVal);
					}
					double start = axis.valueToJava2D(startVal, target, axisEdge);
					double end = axis.valueToJava2D(endVal, target, axisEdge);
					if (i < cpt.size()-1)
						// we're not the last one, overshoot to the top by a bit to fill any gap;
						// this will be hidden under the next one
						end -= OVERSHOOT_PIXELS;
					double height = start-end;
					r.setRect(stripX, end, stripWidth, height);
					g2.setPaint(val.minColor);
					g2.fill(r);
				}
			} else {
				double totalH = target.getHeight();
				
				int subdivisions = (int)Math.round(totalH/pixelsPerSpan);
				if (subdivisions < 10)
					subdivisions = 10;
				
				double fullSpan = totalH/subdivisions;
				double halfSpan = 0.5*fullSpan;
				
				// for vertical, start is the top (max value)
				// i=0 is also top (max value)
				for (int i = 0; i < subdivisions; i++) {
					double start = target.getMinY() + i*fullSpan;
					double middle = start + halfSpan;
					double height = fullSpan;
					if (i < subdivisions-1) {
						// we're not the last one, overshoot to the bottom by a bit to fill any gap;
						// this will be hidden under the next one
						height += OVERSHOOT_PIXELS;
					}
					double scaleValue = axis.java2DToValue(middle, target, axisEdge);

					r.setRect(stripX, start, stripWidth, height);
					g2.setPaint(scale.getPaint(scaleValue));
					g2.fill(r);
				}
			}
			if (isStripOutlineVisible()) {
				g2.setPaint(stripOutlinePaint);
				g2.setStroke(stripOutlineStroke);
				g2.draw(new Rectangle2D.Double(stripX,
						target.getMinY(), stripWidth, target.getHeight()));
			}
			axis.draw(g2, axisX, target, target, axisEdge, null);
		}
		return null;   // PaintScaleLegend always returns null
	}

}
