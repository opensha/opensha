package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

public class BPTAveragingTypeParam extends EnumParameter<BPTAveragingTypeOptions> {
	
	private static final long serialVersionUID = 1L;
	
	public static final String NAME = "BPT Averaging Type";
	public static final String INFO = "This is for setting the different types of averaging methods in the BPT"
			+ " probability model";

	public BPTAveragingTypeParam() {
		super(NAME, EnumSet.allOf(BPTAveragingTypeOptions.class),
				BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE, null);
		setInfo(INFO);
	}

}
