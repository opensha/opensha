package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

public class BPTAveragingTypeParam extends EnumParameter<BPTAveragingTypeOptions> {
	
	private static final long serialVersionUID = 1L;
	
	// TODO remove BPT from class name and these statics
	public static final String NAME = "BPT Averaging Type";
	public static final String INFO = "This is for setting the different types of averaging methods in the BPT"
			+ " probability model";

	public BPTAveragingTypeParam() {
		this(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
	}

	public BPTAveragingTypeParam(BPTAveragingTypeOptions defaultValue) {
		this(defaultValue, EnumSet.allOf(BPTAveragingTypeOptions.class));
	}

	public BPTAveragingTypeParam(BPTAveragingTypeOptions defaultValue, EnumSet<BPTAveragingTypeOptions> allowedValues) {
		super(NAME, allowedValues, defaultValue, null);
		setInfo(INFO);
	}

}
