package org.opensha.sha.calc.params;

import org.opensha.commons.param.impl.BooleanParameter;

public class IncludeMagDistFilterParam extends BooleanParameter {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Use Mag-Distance Filter?";
	public final static String INFO = "This specifies whether to apply the magnitude-distance filter";
	public final static boolean DEFAULT = false;

	public IncludeMagDistFilterParam() {
		super(NAME, DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}

}
