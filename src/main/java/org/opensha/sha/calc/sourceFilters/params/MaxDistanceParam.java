package org.opensha.sha.calc.sourceFilters.params;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.impl.DoubleParameter;

public class MaxDistanceParam extends DoubleParameter {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Maximum Distance";
	public final static String UNITS = "km";
	public final static String INFO = "Earthquake Ruptures beyond this distance are ignored";
	public final static double MIN = 0;
	public final static double MAX = 40000;
	public final static Double DEFAULT = Double.valueOf(200);
	
	public MaxDistanceParam() throws ConstraintException {
		super(NAME, MIN, MAX, UNITS, DEFAULT);
		setInfo(INFO);
		setDefaultValue(DEFAULT);
	}

}
