package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public enum HistoricalOpenIntervals {
	
	UCERF3("UCERF3: 1875") {
		private long startTimeMillis = TimeDepUtils.utcStartOfYear(1875).getTimeInMillis();
		
		@Override
		public long getOpenIntervalStartTime(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			return startTimeMillis;
		}
	},
	NONE("None") {
		@Override
		public long getOpenIntervalStartTime(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			return Long.MAX_VALUE;
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
	
	/**
	 * 
	 * @param fltSysSolution
	 * @param fltSysRupIndex
	 * @return calendar 
	 */
	public abstract long getOpenIntervalStartTime(FaultSystemSolution fltSysSolution, int fltSysRupIndex);

}
