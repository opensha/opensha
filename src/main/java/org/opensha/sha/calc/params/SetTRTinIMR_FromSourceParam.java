package org.opensha.sha.calc.params;

import org.opensha.commons.param.impl.BooleanParameter;

public class SetTRTinIMR_FromSourceParam extends BooleanParameter {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Set TRT From Source?";
	public final static String INFO = "Tells whether to set the Tectonic-Region-Type " +
			"Param in the IMR from the value in each source (if not the value already " +
			"specific in the IMR is used)";
	public final static Boolean DEFAULT = false;
	
	public SetTRTinIMR_FromSourceParam() {
		super(NAME, DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}

}
