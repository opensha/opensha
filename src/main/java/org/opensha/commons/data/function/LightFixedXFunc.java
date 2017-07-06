package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.Arrays;

import org.apache.commons.math3.stat.StatUtils;

/**
 * This is a lightweight array based DescretizedFunc instance that doesn't allow
 * changing the set of X values. It uses less memory than other DiscretizedFunc instances.
 * @author kevin
 *
 */
public class LightFixedXFunc extends AbstractDiscretizedFunc {
	
	private double[] xVals, yVals;
	
	public LightFixedXFunc(DiscretizedFunc func) {
		xVals = new double[func.size()];
		yVals = new double[xVals.length];
		
		for (int i=0; i<xVals.length; i++) {
			Point2D pt = func.get(i);
			xVals[i] = pt.getX();
			yVals[i] = pt.getY();
		}
	}
	
	/**
	 * Values must be sorted!
	 * @param xVals
	 * @param yVals
	 */
	public LightFixedXFunc(double[] xVals, double[] yVals) {
		this.xVals = xVals;
		this.yVals = yVals;
	}

	@Override
	public double getY(double x) {
		int ind = Arrays.binarySearch(xVals, x);
		return yVals[ind];
	}

	@Override
	public double getFirstInterpolatedX(double y) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
	
	@Override
	public int getXIndexBefore(double x) {
		int ind = Arrays.binarySearch(xVals, x);
		if (ind < 0)
			return -ind-2;
		return ind-1;
	}

	@Override
	public int getXIndex(double x) {
		return Arrays.binarySearch(xVals, x);
	}

	@Override
	public int getIndex(Point2D point) {
		return Arrays.binarySearch(xVals, point.getX());
	}

	@Override
	public DiscretizedFunc deepClone() {
		double[] xVals = Arrays.copyOf(this.xVals, this.xVals.length);
		double[] yVals = Arrays.copyOf(this.yVals, this.yVals.length);
		return new LightFixedXFunc(xVals, yVals);
	}

	@Override
	public int size() {
		return xVals.length;
	}

	@Override
	public double getMinX() throws IndexOutOfBoundsException {
		return xVals[0];
	}

	@Override
	public double getMaxX() throws IndexOutOfBoundsException {
		return xVals[xVals.length-1];
	}

	@Override
	public double getMinY() throws IndexOutOfBoundsException {
		return StatUtils.min(yVals);
	}

	@Override
	public double getMaxY() throws IndexOutOfBoundsException {
		return StatUtils.max(yVals);
	}

	@Override
	public Point2D get(int index) {
		if (index < 0 || index >= size())
			return null;
		return new Point2D.Double(xVals[index], yVals[index]);
	}

	@Override
	public double getX(int index) throws IndexOutOfBoundsException {
		return xVals[index];
	}

	@Override
	public double getY(int index) throws IndexOutOfBoundsException {
		return yVals[index];
	}

	@Override
	public void set(Point2D point) {
		int ind = getIndex(point);
		if (ind < 0)
			throw new IllegalArgumentException("No matching value in functions domain and can't add new points");
		yVals[ind] = point.getY();
	}

	@Override
	public void set(double x, double y) {
		int ind = getXIndex(x);
		if (ind < 0)
			throw new IllegalArgumentException("No matching value in functions domain and can't add new points");
		yVals[ind] = y;
	}

	@Override
	public void set(int index, double Y) throws IndexOutOfBoundsException {
		yVals[index] = Y;
	}

	@Override
	public String getMetadataString() {
		return null;
	}

}
