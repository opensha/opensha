package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Interface for a probability model for FaultSystemSolution ERFs. Models can define their own parameter lists, which
 * will be shown in the GUI.
 */
public interface FSS_ProbabilityModel {

	/**
	 * This returns the time-dependent probability of the given rupture, start time, and duration.
	 * 
	 * @param fltSysSolution fault system solution
	 * @param fltSysRupIndex ruptures index in fault system solution
	 * @param ruptureRate long term rupture rate (which could be different than the original in the solution)
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @return time-dependent probability
	 */
	public double getProbability(FaultSystemSolution fltSysSolution, int fltSysRupIndex,
			double ruptureRate, long forecastStartTimeMillis, double durationYears);
	
	/**
	 * This returns the time-dependent probability gain (relative to a Poisson model) of the given rupture, start time, and duration.
	 * 
	 * @param fltSysSolution fault system solution
	 * @param fltSysRupIndex ruptures index in fault system solution
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @return time-dependent probability gain
	 */
	public double getProbabilityGain(FaultSystemSolution fltSysSolution, int fltSysRupIndex,
			long forecastStartTimeMillis, double durationYears);
	
	/**
	 * This returns any adjustable parameters for this model, or null if there are none.
	 * 
	 * The default implementation returns null.
	 * 
	 * @return adjustable parameters or null if there are none
	 */
	public default ParameterList getAdjustableParameters() {
		return null;
	}
	
	/**
	 * Simple Poisson probability model implementation
	 */
	public static class Poisson implements FSS_ProbabilityModel {

		@Override
		public double getProbability(FaultSystemSolution fss, int fltSysRupIndex,
				double ruptureRate, long forecastStartTimeMillis, double durationYears) {
			return TimeDepUtils.rateToPoissonProb(ruptureRate, durationYears);
		}

		@Override
		public double getProbabilityGain(FaultSystemSolution fss, int fltSysRupIndex,
				long forecastStartTimeMillis, double durationYears) {
			return 1d;
		}
		
	}
	
	/**
	 * Weighted-combination of probability models. Note that it is assumed that each passed in model has all parameters
	 * set to their final values, and no parameters will be passed on to the ERF/GUI
	 */
	public static class WeightedCombination implements FSS_ProbabilityModel {
		
		private WeightedList<? extends FSS_ProbabilityModel> probModels;
		private ParameterList params;

		public WeightedCombination(WeightedList<? extends FSS_ProbabilityModel> probModels) {
			this(probModels, null);
		}

		public WeightedCombination(WeightedList<? extends FSS_ProbabilityModel> probModels, ParameterList params) {
			this.params = params;
			if (!probModels.isNormalized()) {
				// normalize it, but don't modify what was passed in
				probModels = new WeightedList<>(probModels);
				probModels.normalize();
			}
			if (!(probModels instanceof WeightedList.Unmodifiable<?>))
				// make it unmodifiable
				probModels = new WeightedList.Unmodifiable<>(probModels);
			this.probModels = probModels;
		}

		@Override
		public double getProbability(FaultSystemSolution fss, int fltSysRupIndex,
				double ruptureRate, long forecastStartTimeMillis, double durationYears) {
			double ret = 0d;
			for (WeightedValue<? extends FSS_ProbabilityModel> value : probModels)
				ret += value.weight * value.value.getProbability(fss, fltSysRupIndex,
						ruptureRate, forecastStartTimeMillis, durationYears);
			// already normalized in the constructor, don't need to re-normalize
			return ret;
		}

		@Override
		public double getProbabilityGain(FaultSystemSolution fss, int fltSysRupIndex, long forecastStartTimeMillis,
				double durationYears) {
			double ret = 0d;
			for (WeightedValue<? extends FSS_ProbabilityModel> value : probModels)
				ret += value.weight * value.value.getProbabilityGain(fss, fltSysRupIndex,
						forecastStartTimeMillis, durationYears);
			// already normalized in the constructor, don't need to re-normalize
			return ret;
		}

		@Override
		public ParameterList getAdjustableParameters() {
			return params;
		}
		
	}
	
}
