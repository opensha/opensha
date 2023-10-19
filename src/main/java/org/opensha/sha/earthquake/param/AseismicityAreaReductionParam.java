package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.BooleanParameter;

public class AseismicityAreaReductionParam extends BooleanParameter {
	
	public static final String NAME = "Aseismicity Reduces Area";
	public final static String INFO = "If selected, fault aseismicity will be applied as a fractional area reduction "
			+ "by increasing the upper seismogenic depth.";
	public static final Boolean DEFAULT = false;
	
	public AseismicityAreaReductionParam() {
		this(DEFAULT);
	}
	
	public AseismicityAreaReductionParam(boolean defaultVal) {
		super(NAME, defaultVal);
		super.setInfo(INFO);
		super.setDefaultValue(defaultVal);
	}

}
