package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.Parameterized;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

public interface HistoricalOpenInterval extends Parameterized {
	
	/**
	 * Returns the start time in epoch milliseconds of the open interval for the given rupture in the
	 * {@link FaultSystemSolution} used to instantiate this model, potentially combining different values across
	 * sections for which {@link #getSectOpenIntervalStartTime(FaultSection, int)} varies.
	 * 
	 * @param ruptureIndex
	 * @return start time of the open interval for this rupture in epoch milliseconds 
	 */
	public abstract long getRuptureOpenIntervalStartTime(int ruptureIndex);
	
	/**
	 * Returns the current open interval in years for the given rupture in the {@link FaultSystemSolution} used to
	 * instantiate this model.
	 * 
	 * @param ruptureIndex
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @return open interval in years for this rupture and forecast start time
	 */
	public default double getRuptureOpenInterval(int ruptureIndex, long forecastStartTimeMillis) {
		return getOpenIntervalYears(getRuptureOpenIntervalStartTime(ruptureIndex), forecastStartTimeMillis);
	}
	
	/**
	 * Returns the start time in epoch milliseconds for the open interval for the given section in the
	 * {@link FaultSystemSolution} used to instantiate this model.
	 * 
	 * @param sectionIndex
	 * @return start time of the open interval for this section in epoch milliseconds
	 */
	public abstract long getSectionOpenIntervalStartTime(int sectionIndex);
	
	/**
	 * Returns the current open interval in years for the given section in the
	 * {@link FaultSystemSolution} used to instantiate this model.
	 * 
	 * @param sectionIndex
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @return open interval in years for this section and forecast start time
	 */
	public default double getSectionOpenInterval(int sectionIndex, long forecastStartTimeMillis) {
		return getOpenIntervalYears(getSectionOpenIntervalStartTime(sectionIndex), forecastStartTimeMillis);
	}
	
	/**
	 * 
	 * @param openIntervalStartTimeMillis
	 * @param forecastStartTimeMillis
	 * @return open interval in years, or 0 if openIntervalStartTimeMillis >= forecastStartTimeMillis
	 */
	public static double getOpenIntervalYears(long openIntervalStartTimeMillis, long forecastStartTimeMillis) {
		if (openIntervalStartTimeMillis >= forecastStartTimeMillis)
			return 0d;
		double interval = forecastStartTimeMillis - openIntervalStartTimeMillis;
		return (double)interval / TimeDepUtils.MILLISEC_PER_YEAR;
	}
	
	/**
	 * This returns any adjustable parameters for this model, or null if there are none.
	 * <p>
	 * The default implementation returns null.
	 * 
	 * @return adjustable parameters or null if there are none
	 */
	public default ParameterList getAdjustableParameters() {
		return null;
	}
	
	public static class None implements HistoricalOpenInterval {

		@Override
		public long getRuptureOpenIntervalStartTime(int ruptureIndex) {
			return Long.MAX_VALUE;
		}

		@Override
		public double getRuptureOpenInterval(int ruptureIndex, long forecastStartTimeMillis) {
			return 0d;
		}

		@Override
		public long getSectionOpenIntervalStartTime(int sectionIndex) {
			return Long.MAX_VALUE;
		}

		@Override
		public double getSectionOpenInterval(int sectionIndex, long forecastStartTimeMillis) {
			return 0d;
		}

		@Override
		public String toString() {
			return "None";
		}
	}
	
	public static class SingleYear implements HistoricalOpenInterval {
		
		private int year;
		private long startTimeMillis;
		
		private ParameterList params;

		public SingleYear(int year, boolean adjustable) {
			this.year = year;
			this.startTimeMillis = TimeDepUtils.utcStartOfYear(year).getTimeInMillis();
			
			if (adjustable) {
				params = new ParameterList();
				
				int min = Integer.min(1700, year);
				int max = Integer.max(2100, year);
				IntegerParameter yearParam = new IntegerParameter(
						"Historical Open Interval Start Year", min, max, Integer.valueOf(year));
				yearParam.addParameterChangeListener(l -> {
					this.year = yearParam.getValue();
					this.startTimeMillis = TimeDepUtils.utcStartOfYear(year).getTimeInMillis();
				});
			}
		}

		@Override
		public long getRuptureOpenIntervalStartTime(int ruptureIndex) {
			return startTimeMillis;
		}

		@Override
		public long getSectionOpenIntervalStartTime(int sectionIndex) {
			return startTimeMillis;
		}
		
		@Override
		public ParameterList getAdjustableParameters() {
			return params;
		}

		@Override
		public String toString() {
			return "Single Year: "+year;
		}
		
	}

}
