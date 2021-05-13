package org.opensha.sha.imr.param.IntensityMeasureParams;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

public class DurationTimeIntervalParam extends EnumParameter<DurationTimeInterval> {
	
	public static final String NAME = "Time Interval";
	
	public DurationTimeIntervalParam(EnumSet<DurationTimeInterval> choices, DurationTimeInterval defaultValue) {
		super(NAME, choices, defaultValue, null);
	}

}
