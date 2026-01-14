package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public enum HistoricalOpenIntervals {
	
	UCERF3("UCERF3: 1875") {
		@Override
		public HistoricalOpenInterval instance(FaultSystemSolution fltSysSolution) {
			return new HistoricalOpenInterval.SingleYear(1875, false); // false -> not adjustable
		}
	},
	SINGLE_YEAR("Single Year") {
		@Override
		public HistoricalOpenInterval instance(FaultSystemSolution fltSysSolution) {
			return new HistoricalOpenInterval.SingleYear(1875, true); // true -> adjustable
		}
	},
	NONE("None") {
		@Override
		public HistoricalOpenInterval instance(FaultSystemSolution fltSysSolution) {
			return new HistoricalOpenInterval.None();
		}
	};
	
	public static EnumSet<HistoricalOpenIntervals> UCERF3_MODELS = EnumSet.of(UCERF3, SINGLE_YEAR, NONE);
	
	private String name;

	private HistoricalOpenIntervals(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public abstract HistoricalOpenInterval instance(FaultSystemSolution fltSysSolution);
	
	public static final String PARAM_NAME = "Historical Open Interval";
	private final static String PARAM_INFO = "Historic time interval over which an event is known not to have occurred, "
			+ "usually described by the start year of that interval.";
	
	public static EnumParameterizedModelarameter<HistoricalOpenIntervals, HistoricalOpenInterval> buildParameter(
			FaultSystemSolution fltSysSolution, EnumSet<HistoricalOpenIntervals> choices, HistoricalOpenIntervals defaultValue) {
		EnumParameterizedModelarameter<HistoricalOpenIntervals, HistoricalOpenInterval> param = new EnumParameterizedModelarameter<>(
				PARAM_NAME, choices, defaultValue, true, e -> e.instance(fltSysSolution));
		param.setInfo(PARAM_INFO);
		return param;
	}

}
