package org.opensha.commons.gui.plot.jfreechart;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.List;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.data.Range;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * This is a number axis that will align labels with the centers of evenly gridded function bins
 * @author kevin
 *
 */
public class CustomOffsetNumberAxis extends NumberAxis {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

//	private HistogramFunction hist;
	
	private double centerPoint;
	private double delta;
	
	private static final double LOG_2 = Math.log(2d);
	
	private Range prevRange;
	private double[] tickVals;
	private double prevTickDelta;
	
	public CustomOffsetNumberAxis(EvenlyDiscretizedFunc func, String label) {
		this(func.getMinX(), func.getDelta(), label);
	}
	
	public CustomOffsetNumberAxis(double centerPoint, double delta, String label) {
		super(label);
		Preconditions.checkState(Doubles.isFinite(centerPoint));
		Preconditions.checkState(Doubles.isFinite(delta));
		Preconditions.checkState(delta > 0d);
		this.centerPoint = centerPoint;
		this.delta = delta;
//		System.out.println("Center point: "+centerPoint);
//		System.out.println("Delta: "+delta);
		setStandardTickUnits(new TickUnitSourceImpl());
	}
	
	private class TickUnitSourceImpl implements TickUnitSource {

		@Override
		public TickUnit getCeilingTickUnit(TickUnit unit) {
			return getCeilingTickUnit(unit.getSize());
		}

		@Override
		public TickUnit getCeilingTickUnit(double size) {
//			System.out.println("Ceiling for: "+size);
			double log = Math.log(size/delta) / LOG_2;
			double higher = Math.ceil(log);
//			System.out.println("log: "+Math.log(size)+" / "+LOG_2+" = "+log);
//			System.out.println("higher: "+higher);
			double ret = delta * Math.pow(2d, higher);
			Preconditions.checkState(ret >= size);
//			System.out.println("Ceiling ret: "+ret);
			return new NumberTickUnit(ret);
		}

		@Override
		public TickUnit getLargerTickUnit(TickUnit unit) {
//			System.out.println("Larger for: "+unit.getSize());
			double x = unit.getSize();
			double log = Math.log(x/delta) / LOG_2;
			double higher = Math.ceil(log);
			double ret = delta * Math.pow(2d, higher);
			Preconditions.checkState(ret >= x);
			if (ret == x)
				ret = delta * Math.pow(2d, higher+1);
//			System.out.println("Larger ret: "+ret);
			return new NumberTickUnit(ret);
		}
		
	}
	
	@Override
	protected double calculateLowestVisibleTickValue() {
//		System.out.println("****** calculateLowestVisibleTickValue");
		double[] vals = getTickValsInRange();
		Preconditions.checkState(vals.length > 0, "no vals indices in range!");
		return vals[0];
	}

	@Override
	protected double calculateHighestVisibleTickValue() {
//		System.out.println("****** calculateHighestVisibleTickValue");
		double[] vals = getTickValsInRange();
		Preconditions.checkState(vals.length > 0, "no vals indices in range!");
		return vals[vals.length-1];
	}

	@Override
	protected int calculateVisibleTickCount() {
//		System.out.println("****** calculateVisibleTickCount");
		return getTickValsInRange().length;
	}
	
	/**
	 * Calculates tick values within the current range
	 * @return
	 */
	private synchronized double[] getTickValsInRange() {
		return getTickValsInRange(getTickUnit().getSize());
	}
	
	/**
	 * Calculates tick values within the current range
	 * @param tickDelta
	 * @return
	 */
	private synchronized double[] getTickValsInRange(double tickDelta) {
		Range range = getRange();
		if (prevRange != null && range.getLowerBound() == prevRange.getLowerBound()
				&& range.getUpperBound() == prevRange.getUpperBound() && prevTickDelta == tickDelta)
			return tickVals;
		double lower = range.getLowerBound();
		double upper = range.getUpperBound();
		
		double lowerNumAwayCenter = Math.floor((lower - centerPoint)/tickDelta);
		double startLower = centerPoint + lowerNumAwayCenter*tickDelta;
		
//		System.out.println("Range: "+range);
//		System.out.println("Tick Delta: "+tickDelta);
//		System.out.println("lowerNumAwayCenter: "+lowerNumAwayCenter);
//		System.out.println("lowerNumAwayCenter, no floor: "+((lower - centerPoint)/tickDelta));
//		System.out.println("startLower: "+startLower);
		Preconditions.checkState(startLower <= lower);
		
		List<Double> vals = Lists.newArrayList();
		for (double val=startLower; val<=upper; val+=tickDelta) {
			if (val < lower)
				continue;
			vals.add(val);
		}
//		System.out.println("Vals: "+Joiner.on(",").join(vals));
		double[] ret = Doubles.toArray(vals);
		
		this.tickVals = ret;
		this.prevRange = new Range(range.getLowerBound(), range.getUpperBound());
		this.prevTickDelta = tickDelta;
		
		return ret;
	}

	/**
	 * Override the standard implementation to fix estimation of label width. Default implementation
	 * uses range.getLowerBound()/getUpperBound() as the reference upper/lower values, this versions
	 * uses the actual tick values via getTickValsInRange(size).
	 */
	@Override
	protected double estimateMaximumTickLabelWidth(Graphics2D g2, 
			TickUnit unit) {

		RectangleInsets tickLabelInsets = getTickLabelInsets();
		double result = tickLabelInsets.getLeft() + tickLabelInsets.getRight();

		if (isVerticalTickLabels()) {
			// all tick labels have the same width (equal to the height of the 
			// font)...
			FontRenderContext frc = g2.getFontRenderContext();
			LineMetrics lm = getTickLabelFont().getLineMetrics("0", frc);
			result += lm.getHeight();
		}
		else {
			// look at lower and upper bounds...
			FontMetrics fm = g2.getFontMetrics(getTickLabelFont());
//			Range range = getRange();
//			double lower = range.getLowerBound();
//			double upper = range.getUpperBound();
			double[] tickVals = getTickValsInRange();
			double lower = tickVals[0];
			double upper = tickVals[tickVals.length-1];
			String lowerStr = "";
			String upperStr = "";
			NumberFormat formatter = getNumberFormatOverride();
			if (formatter != null) {
				lowerStr = formatter.format(lower);
				upperStr = formatter.format(upper);
			}
			else {
				lowerStr = unit.valueToString(lower);
				upperStr = unit.valueToString(upper);                
			}
			double w1 = fm.stringWidth(lowerStr);
			double w2 = fm.stringWidth(upperStr);
			result += Math.max(w1, w2);
		}

		return result;

	}
	
	public static void main(String[] args) {
		double minX = -7.82;
		double delta = 1.5;
		int num = 15;
		
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minX, num, delta);
		for (int i=0; i<func.size(); i++)
			func.set(i, Math.random());
		GraphWindow gw = new GraphWindow(func, "Asdf", new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		gw.setGriddedFuncAxesTicks(true);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}

}
