package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This HistoricOpenIntervalParam is used for setting the the time over which an event is
 * known to have not occurred.
 */
public class HistoricOpenIntervalParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Historic Open Interval";
	public final static String INFO = "Historic time interval over which event is known not to have occurred";
	private static final String UNITS = "Years";
	protected final static Double MIN = new Double(0d);
	protected final static Double MAX = new Double(1e6);
	

	/**
	 * This sets the default value as given.
	 */
	public HistoricOpenIntervalParam(double defaultStdDev) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultStdDev);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public HistoricOpenIntervalParam() { this(0.0);}
	
	
}
