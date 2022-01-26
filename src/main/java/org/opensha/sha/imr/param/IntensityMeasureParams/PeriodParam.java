package org.opensha.sha.imr.param.IntensityMeasureParams;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;

/**
 * This represents Period for the Spectral Acceleration parameter (SA_Param).  
 * The constructor requires a list of supported periods (in the form of a
 * DoubleDiscreteConstraint).  Once instantiated, this can be added to the
 * SA_Param as an independent parameter.
 * See constructors for info on editability and default values.
 * @author field
 *
 */
public class PeriodParam extends DoubleDiscreteParameter {

	public final static String NAME = "SA Period";
	public final static String UNITS = "sec";
	public final static String INFO = "Oscillator Period for SA";

	/**
	 * This is the most general constructor
	 * @param peroidList - desired constraints
	 * @param defaultPeriod - desired default value
	 * @param leaveEditable - whether or not to leave editable
	 */
	public PeriodParam(DoubleDiscreteConstraint peroidList, double defaultPeriod, boolean leaveEditable) {
		super(NAME, peroidList, UNITS);
		peroidList.setNonEditable();
		this.setInfo(INFO);
		setDefaultValue(defaultPeriod);
		if(!leaveEditable) setNonEditable();
	}
	
	/**
	 * This sets the default as 1.0 and leaves the parameter non editable
	 * @param peroidList
	 */
	public PeriodParam(DoubleDiscreteConstraint peroidList) { this(peroidList,1.0,false);}
	
	/**
	 * Helper method to quickly get the supported periods.
	 * 
	 * @return
	 */
	public List<Double> getSupportedPeriods() {
		DoubleDiscreteConstraint constr = (DoubleDiscreteConstraint) getConstraint();
		List<Double> periods = constr.getAllowedDoubles();
		return periods;
	}
	
	/**
	 * This assumes the list is always in order (is this correct?)
	 * @return
	 */
	public double getMinPeriod() {
		List<Double> periods = getSupportedPeriods();
		return periods.get(0);
	}
	
	/**
	 * This assumes the list is always in order (is this correct?)
	 * @return
	 */
	public double getMaxPeriod() {
		List<Double> periods = getSupportedPeriods();
		return periods.get(periods.size()-1);
	}
	
	public double[] getPeriods() {
		List<Double> periods = getSupportedPeriods();
		double[] pers = new double[periods.size()];
		for(int i=0;i<periods.size();i++)
			pers[i] = periods.get(i).doubleValue();
		return pers;
	}


}
