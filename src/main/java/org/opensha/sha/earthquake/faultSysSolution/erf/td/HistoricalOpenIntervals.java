package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.param.impl.ParameterizedEnumParameter;
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
	
	public static EnumSet<HistoricalOpenIntervals> UCERF3_MODELS = EnumSet.of(UCERF3, NONE);
	
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
	
	public static ParameterizedEnumParameter<HistoricalOpenIntervals, HistoricalOpenInterval> buildParameter(
			FaultSystemSolution fltSysSolution, EnumSet<HistoricalOpenIntervals> choices, HistoricalOpenIntervals defaultValue) {
		return new ParameterizedEnumParameter<HistoricalOpenIntervals, HistoricalOpenInterval>(
				PARAM_NAME, choices, defaultValue, null, e -> e.instance(fltSysSolution));
	}

}
