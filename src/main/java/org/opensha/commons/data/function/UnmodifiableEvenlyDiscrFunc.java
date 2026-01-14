package org.opensha.commons.data.function;

import java.awt.geom.Point2D;

import org.opensha.commons.util.ClassUtils;

/**
 * Unmodifiable view of an {@link EvenlyDiscretizedFunc}. All set methods will throw an {@link UnsupportedOperationException}.
 */
public class UnmodifiableEvenlyDiscrFunc extends EvenlyDiscretizedFunc {
	
	public UnmodifiableEvenlyDiscrFunc(EvenlyDiscretizedFunc func) {
		super(func.getMinX(), func.getMaxX(), func.size(), func.points);
		
		this.info = func.info;
		this.minX = func.minX;
		this.maxX = func.maxX;
		this.name = func.name;
		this.xAxisName = func.xAxisName;
		this.yAxisName = func.yAxisName;
		this.tolerance = func.tolerance;
		this.points = func.points;
	}

	@Override
	public void set(double min, int num, double delta) {
		setFail();
	}

	@Override
	public void set(double min, double max, int num) {
		setFail();
	}

	@Override
	protected void set(double min, double max, int num, double[] points) {
		setFail();
	}

	@Override
	public void clear() {
		setFail();
	}

	@Override
	public void set(Point2D point) {
		setFail();
	}

	@Override
	public void set(double x, double y) {
		setFail();
	}

	@Override
	public void set(int index, double y) {
		setFail();
	}

	@Override
	public EvenlyDiscretizedFunc deepClone() {
		return new UnmodifiableEvenlyDiscrFunc(this);
	}

	@Override
	public void scale(double val) {
		setFail();
	}
	
	private void setFail() {
		throw new UnsupportedOperationException("cannot modify an "+ClassUtils.getClassNameWithoutPackage(getClass()));
	}

}
