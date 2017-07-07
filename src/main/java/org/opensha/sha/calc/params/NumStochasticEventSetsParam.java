package org.opensha.sha.calc.params;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.impl.IntegerParameter;

public class NumStochasticEventSetsParam extends IntegerParameter {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Num Event Sets";
	public final static String INFO = "Number of stochastic event sets for those types of calculations";
	public final static int MIN = 1;
	public final static int MAX = Integer.MAX_VALUE;
	public final static Integer DEFAULT = new Integer(1);

	public NumStochasticEventSetsParam() throws ConstraintException {
		super(NAME, MIN, MAX, DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}

}
