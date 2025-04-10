package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.jfree.data.xy.AbstractXYZDataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.util.DataUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class XYZDatasetWrapper extends AbstractXYZDataset implements IntervalXYDataset {
	
	private XYZ_DataSet xyz;
	private String zLabel;
	private boolean xLog;
	private boolean yLog;
	private double spacingX;
	private double spacingY;
	private double halfSpacingX;
	private double halfSpacingY;
	
	public XYZDatasetWrapper(XYZPlotSpec spec, boolean xLog, boolean yLog) {
		this.xyz = spec.getXYZ_Data();
		this.zLabel = spec.isIncludeZlabelInLegend() ? spec.getZAxisLabel() : "";
		this.xLog = xLog;
		this.yLog = yLog;
		Double thicknessX = spec.getThicknessX();
		Double thicknessY = spec.getThicknessY();
		if (thicknessX != null && thicknessY != null) {
			// use passed in
			spacingX = thicknessX;
			spacingY = thicknessY;
		} else if (xyz instanceof EvenlyDiscrXYZ_DataSet) {
			spacingX = thicknessX == null ? ((EvenlyDiscrXYZ_DataSet)xyz).getGridSpacingX() : thicknessX;
			spacingY = thicknessY == null ? ((EvenlyDiscrXYZ_DataSet)xyz).getGridSpacingY() : thicknessY;
		} else {
			// detect from data - use median of differences
			Preconditions.checkState(xyz.size() > 1, "Can't detect spacing if we only have %s value", xyz.size());
			int numToCheck = Integer.min(1000, xyz.size());
			Preconditions.checkState(numToCheck > 0);
			List<Double> xDiffs = new ArrayList<>(numToCheck);
			List<Double> yDiffs = new ArrayList<>(numToCheck);
			
			Point2D prevPt = xyz.getPoint(0);
			for (int i=1; i<xyz.size(); i++) {
				Point2D pt = xyz.getPoint(i);
				
				double diffX = Math.abs(pt.getX() - prevPt.getX());
				double diffY = Math.abs(pt.getY() - prevPt.getY());
				
				if ((float)diffX > 0f)
					xDiffs.add(diffX);
				if ((float)diffY > 0f)
					yDiffs.add(diffY);
				
				if (xDiffs.size() >= numToCheck && yDiffs.size() > numToCheck)
					break;
				
				prevPt = pt;
			}
			Preconditions.checkState(!xDiffs.isEmpty() || !yDiffs.isEmpty(),
					"Can't detect spacing with %x xDiffs, %s yDiffs, and %s values", xDiffs.size(), yDiffs.size(), xyz.size());
			if (xDiffs.isEmpty()) {
				// use y as x
				spacingY = yDiffs.size() == 1 ? yDiffs.get(0) : DataUtils.median(Doubles.toArray(yDiffs));
				spacingX = spacingY;
			} else if (yDiffs.isEmpty()) {
				// use x as y
				spacingX = xDiffs.size() == 1 ? xDiffs.get(0) : DataUtils.median(Doubles.toArray(xDiffs));
				spacingY = spacingX;
			} else {
				spacingX = xDiffs.size() == 1 ? xDiffs.get(0) : DataUtils.median(Doubles.toArray(xDiffs));
				spacingY = yDiffs.size() == 1 ? yDiffs.get(0) : DataUtils.median(Doubles.toArray(yDiffs));
			}
			if (thicknessX != null)
				// override what we detected
				spacingX = thicknessX;
			if (thicknessY != null)
				// override what we detected
				spacingY = thicknessY;
//			System.out.println("Detected xSpacing="+spacingX+", ySpacing="+spacingY);
		}
		halfSpacingX = 0.5*spacingX;
		halfSpacingY = 0.5*spacingY;
	}

	@Override
	public Number getZ(int series, int item) {
		return xyz.get(item);
	}

	@Override
	public int getItemCount(int series) {
		return xyz.size();
	}

	@Override
	public Number getX(int series, int item) {
		double x = xyz.getPoint(item).getX();
		if (xLog)
			return Math.pow(10, x);
		return x;
	}

	@Override
	public Number getY(int series, int item) {
		double y = xyz.getPoint(item).getY();
		if (yLog)
			return Math.pow(10, y);
		return y;
	}

	@Override
	public int getSeriesCount() {
		return 1;
	}

	@Override
	public Comparable getSeriesKey(int arg0) {
		return zLabel;
	}

	@Override
	public double getStartXValue(int series, int item) {
		double x = xyz.getPoint(item).getX() - halfSpacingX;
		if (xLog)
			return Math.pow(10, x);
		return x;
	}

	@Override
	public double getEndXValue(int series, int item) {
		double x = xyz.getPoint(item).getX() + halfSpacingX;
		if (xLog)
			return Math.pow(10, x);
		return x;
	}

	@Override
	public double getStartYValue(int series, int item) {
		double y = xyz.getPoint(item).getY() - halfSpacingY;
		if (yLog)
			return Math.pow(10, y);
		return y;
	}

	@Override
	public double getEndYValue(int series, int item) {
		double y = xyz.getPoint(item).getY() + halfSpacingY;
		if (yLog)
			return Math.pow(10, y);
		return y;
	}

	@Override
	public Number getStartX(int series, int item) {
		return getStartXValue(series, item);
	}

	@Override
	public Number getEndX(int series, int item) {
		return getEndXValue(series, item);
	}

	@Override
	public Number getStartY(int series, int item) {
		return getStartYValue(series, item);
	}

	@Override
	public Number getEndY(int series, int item) {
		return getEndYValue(series, item);
	}
	
}