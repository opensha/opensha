package org.opensha.commons.data.function;

import java.awt.geom.Point2D;

import org.opensha.commons.util.ClassUtils;

public class UnmodifiableEvenlyDiscrFunc extends EvenlyDiscretizedFunc {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private boolean initialized;
	
	public UnmodifiableEvenlyDiscrFunc(EvenlyDiscretizedFunc func) {
		super(func.getMinX(), func.getMaxX(), func.size());
		for (int i=0; i<func.size(); i++)
			points[i] = func.getY(i);
		setName(func.getName());
		setInfo(func.getInfo());
		setTolerance(func.getTolerance());
		setXAxisName(func.getXAxisName());
		setYAxisName(func.getYAxisName());
		initialized = true;
	}

	@Override
	public EvenlyDiscretizedFunc deepClone() {
		EvenlyDiscretizedFunc clone = new EvenlyDiscretizedFunc(getMinX(), getMaxX(), size());
		for (int i=0; i<size(); i++)
			clone.set(i, getY(i));
		clone.setName(getName());
		clone.setInfo(getInfo());
		clone.setTolerance(getTolerance());
		clone.setXAxisName(getXAxisName());
		clone.setYAxisName(getYAxisName());
		return clone;
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
	public void set(int index, double Y) throws IndexOutOfBoundsException {
		setFail();
	}
	
	@Override
	public void set(double min, int num, double delta) {
		if (!initialized)
			// allow during initial construction
			super.set(min, num, delta);
		else
			setFail();
	}

	@Override
	public void set(double min, double max, int num) {
		if (!initialized)
			// allow during initial construction
			super.set(min, max, num);
		else
			setFail();
	}

	@Override
	public void scale(double val) {
		setFail();
	}

	private void setFail() {
		throw new UnsupportedOperationException("cannot modify an "+ClassUtils.getClassNameWithoutPackage(getClass()));
	}

}
