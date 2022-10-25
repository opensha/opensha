package org.opensha.sha.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;

/**
 * This constitutes is for the natural-log Spectral Acceleration intensity measure
 * parameter.  It requires being given a PeriodParam and DampingParam, as these are
 * the parameters that SA depends upon.
 * See constructors for info on editability and default values.
 * @author field
 *
 */
public class SA_InterpolatedParam extends WarningDoubleParameter {

	public final static String NAME = "SA Interpolated";
	public final static String UNITS = "g";
	public final static String INFO = "Response Spectral Acceleration";
	protected final static Double MIN = new Double(Math.log(Double.MIN_VALUE));
	protected final static Double MAX = new Double(Double.MAX_VALUE);
	protected final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE));
	protected final static Double DEFAULT_WARN_MAX = new Double(Math.log(3.0));

	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(0.5) (the natural
	 * log of 0.5). The parameter is left as non editable
	 */
	public SA_InterpolatedParam(PeriodInterpolatedParam periodInterpParam, DampingParam dampingParam) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
		this.setInfo(INFO);
		DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
		warn2.setNonEditable();
		setWarningConstraint(warn2);
		addIndependentParameter(periodInterpParam);
		addIndependentParameter(dampingParam);
		setDefaultValue(0.5);
		setNonEditable();
	}
	
	/**
	 * Helper method to quickly get the period param
	 * 
	 * @return
	 */
	public PeriodInterpolatedParam getPeriodInterpolatedParam() {
		return (PeriodInterpolatedParam) this.getIndependentParameter(PeriodInterpolatedParam.NAME);
	}
	
	/**
	 * Helper method to quickly get the damping param
	 * 
	 * @return
	 */
	public DampingParam getDampingParam() {
		return (DampingParam) this.getIndependentParameter(DampingParam.NAME);
	}
	
	public static void setPeriodInSA_Param(Parameter<?> param, double period) {
		SA_Param saParam = (SA_Param) param;
		saParam.getPeriodParam().setValue(period);
	}

}
