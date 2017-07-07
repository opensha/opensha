package org.opensha.sha.calc.params;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.StringParameter;

public class NonSupportedTRT_OptionsParam extends StringParameter {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "If source TRT not supported by IMR";
	public final static String INFO = "Tells how to set TRT in IMR if TRT from source is not supported";
	public final static String USE_DEFAULT = "Use IMR's default TRT value";
	public final static String USE_ORIG = "Use TRT value already set in IMR";
	public final static String THROW = "Throw runtime exception";
	public final static String DEFAULT = USE_ORIG;
	
	private static StringConstraint buildConstraint() {
		StringConstraint constraint = new StringConstraint();
		constraint.addString(USE_DEFAULT);
		constraint.addString(USE_ORIG);
		constraint.addString(THROW);
		constraint.setNonEditable();
		return constraint;
	}
	
	public NonSupportedTRT_OptionsParam() throws ConstraintException {
		super(NAME, buildConstraint(), DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}

}
