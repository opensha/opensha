package org.opensha.sha.imr.param.IntensityMeasureParams;

import java.util.ArrayList;

import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This represents continuous Period for the Spectral Acceleration parameter (SA_InterpolatedParam).  
 * @author field
 *
 */
public class PeriodInterpolatedParam extends DoubleParameter {

	public final static String NAME = "SA Interpolated Period";
	public final static String UNITS = "sec";
	public final static String INFO = "Continous oscillator period for interpolated SA";

	/**
	 * This is the most general constructor
	 * @param minPeroid - minimum value
	 * @param maxPeroid - maximum value
	 * @param defaultPeriod - desired default value
	 * @param leaveEditable - whether or not to leave editable
	 */
	public PeriodInterpolatedParam(double minPeriod, double maxPeriod, double defaultPeriod, boolean leaveEditable) {
		super(NAME, minPeriod, maxPeriod, UNITS);
		this.setInfo(INFO);
		setDefaultValue(defaultPeriod);
		if(!leaveEditable) setNonEditable();
	}
	
}
