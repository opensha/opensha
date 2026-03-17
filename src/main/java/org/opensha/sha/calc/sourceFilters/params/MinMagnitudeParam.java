package org.opensha.sha.calc.sourceFilters.params;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.impl.DoubleParameter;

public class MinMagnitudeParam extends DoubleParameter {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Minimum Magnitude";
	public final static String UNITS = "Moment Magnitude";
	public final static String INFO = "Earthquake Ruptures with magnitudes less than this amount will be ignored";
	public final static double MIN = -1;
	public final static double MAX = 10;
	public final static Double DEFAULT = 0.0;
	
	public MinMagnitudeParam() throws ConstraintException {
		super(NAME, MIN, MAX, UNITS, DEFAULT);
		setInfo(INFO);
		setDefaultValue(DEFAULT);
	}

}
