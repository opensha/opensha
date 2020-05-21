package org.opensha.sha.imr.param.IntensityMeasureParams;

import java.util.Arrays;
import java.util.EnumSet;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.DoubleParameter;

import com.google.common.base.Preconditions;

/**
 * Significant shaking duration intensity measure
 * 
 * @author kevin
 *
 */
public class SignificantDurationParam extends DoubleParameter {
	
	public final static String NAME = "Significant Duration";
	public final static String UNITS = "s";
	public final static String INFO = "Significant Duration";
	public final static Double MIN = new Double(Math.log(Double.MIN_VALUE));
	public final static Double MAX = new Double(Double.MAX_VALUE);
	
	private DurationTimeIntervalParam intervalParam;
	
	public SignificantDurationParam() {
		this(DurationTimeInterval.values());
	}
	
	public SignificantDurationParam(DurationTimeInterval... intervals) {
		super(NAME, MIN, MAX);
		
		Preconditions.checkArgument(intervals.length > 0);
		
		EnumSet<DurationTimeInterval> set;
		if (intervals.length == 1)
			set = EnumSet.of(intervals[0]);
		else
			set = EnumSet.of(intervals[0], Arrays.copyOfRange(intervals, 1, intervals.length));
		
		intervalParam = new DurationTimeIntervalParam(set, intervals[0]);
		addIndependentParameter(intervalParam);
		setNonEditable();
	}
	
	public DurationTimeInterval getTimeInterval() {
		return intervalParam.getValue();
	}
	
	public void setTimeInterval(DurationTimeInterval interval) {
		intervalParam.setValue(interval);
	}
	
	public static DurationTimeInterval getTimeInterval(Parameter<?> param) {
		Preconditions.checkState(param instanceof SignificantDurationParam);
		return ((SignificantDurationParam)param).getTimeInterval();
	}
	
	public static void setTimeInterval(Parameter<?> param, DurationTimeInterval interval) {
		Preconditions.checkState(param instanceof SignificantDurationParam);
		((SignificantDurationParam)param).setTimeInterval(interval);
	}

}
