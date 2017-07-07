package org.opensha.sha.util.component;

import java.awt.geom.Point2D;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.imr.param.OtherParams.Component;

public abstract class ComponentTranslation implements Named {
	
	/**
	 * 
	 * @return Component that this translation translates from
	 */
	public abstract Component getFromComponent();
	
	/**
	 * 
	 * @return Component that this translation translates to
	 */
	public abstract Component getToComponent();
	
	/**
	 * Scaling factor in linear domain for the given period. Scaling factors will be interpolated
	 * linearly between neighboring periods.
	 * @param period
	 * @throws IllegalArgumentException if period is not supported
	 * @return
	 */
	public abstract double getScalingFactor(double period) throws IllegalArgumentException;
	
	/**
	 * @return minimum supported period for conversion
	 */
	public abstract double getMinPeriod();
	
	/**
	 * @return maximum supported period for conversion
	 */
	public abstract double getMaxPeriod();
	
	public boolean isPeriodSupported(double period) {
		return (float)period >= (float)getMinPeriod() && (float)period <= (float)getMaxPeriod();
	}
	
	protected void assertValidPeriod(double period) {
		if (!isPeriodSupported(period))
			throw new IllegalArgumentException("Period is not supported: "+period
					+". Range: ["+getMinPeriod()+","+getMaxPeriod()+"]");
	}
	
	/**
	 * Returns a new curve where X value's have been scaled by the period specific conversion factor
	 * @param curve
	 * @param period
	 * @return
	 */
	public DiscretizedFunc convertCurve(DiscretizedFunc curve, double period) {
		double ratio = getScalingFactor(period);
		ArbitrarilyDiscretizedFunc scaled = new ArbitrarilyDiscretizedFunc();
		scaled.setName(curve.getName());
		scaled.setInfo(curve.getInfo());
		
		for (Point2D pt : curve)
			scaled.set(pt.getX()*ratio, pt.getY());
		
		return scaled;
	}
	
	/**
	 * 
	 * @param origVal
	 * @param period
	 * @return value scaled by the period specific conversion factor
	 */
	public double getScaledValue(double origVal, double period) {
		double ratio = getScalingFactor(period);
		return origVal*ratio;
	}

}
