package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.ParameterizedModel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Interface for a probability model for FaultSystemSolution ERFs. Models can define their own parameter lists, which
 * will be shown in the GUI.
 */
public interface FSS_ProbabilityModel extends ParameterizedModel {
	
	/**
	 * @return the fault-system solution that this model was instantiated for
	 */
	public FaultSystemSolution getFaultSystemSolution();

	/**
	 * This returns the time-dependent probability of the given rupture, start time, and duration.
	 * 
	 * @param ruptureIndex rupture index in fault system solution
	 * @param ruptureRate long term rupture rate (which could be different than the original in the solution)
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @return time-dependent probability
	 */
	public double getProbability(int ruptureIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears);
	
	/**
	 * This returns the time-dependent probability gain (relative to a Poisson model) of the given rupture, start time, and duration.
	 * 
	 * @param ruptureIndex rupture index in fault system solution
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @return time-dependent probability gain
	 */
	public double getProbabilityGain(int ruptureIndex, long forecastStartTimeMillis, double durationYears);
	
	/**
	 * Returns an array of date of last event for each fault section, which may differ from the original values in the
	 * {@link FaultSystemSolution}, e.g., during simulations.
	 * <p>
	 * This returns a copy of the array and individual updates will not be propagated to this model; you access
	 * values without array-copy overhead in the subclasses or via {@link #getSectDOLE(int)}.  
	 * 
	 * @return Copy of the array of date of last event for each fault section
	 */
	public long[] getSectDOLE();
	
	/**
	 * Returns an array of date of last event for each fault section, which may differ from the original values in the
	 * {@link FaultSystemSolution}, e.g., during simulations.
	 * 
	 * @param sectIndex fault section index
	 * @return date of last event for the given fault section, or Long.MIN_VALUE if unknown
	 */
	public long getSectDOLE(int sectIndex);
	
	/**
	 * Sets the date of last event for each fault section in this calculator. The original {@link FaultSection} and
	 * {@link FaultSystemSolution} are not updated to reflect this change. Any deviations from the original values
	 * can be overridden with {@link #resetSectDOLE()}.
	 * 
	 * @param sectDatesOfLastEvent
	 */
	public void setSectDOLE(long[] sectDatesOfLastEvent);
	
	/**
	 * Sets the date of last event for the given fault section. The original {@link FaultSection} and
	 * {@link FaultSystemSolution} are not updated to reflect this change. Any deviations from the original values
	 * can be overridden with {@link #resetSectDOLE()}.
	 * 
	 * @param sectDOLE
	 */
	public void setSectDOLE(int sectIndex, long sectDateOfLastEvent);
	
	/**
	 * Resets all dates of last event to the values stored in the {@link FaultSection}'s within the
	 * {@link FaultSystemSolution}
	 */
	public void resetSectDOLE();
	
	/**
	 * 
	 * @return the number of sections with a valid date of last event
	 */
	public int getNumSectsWithDOLE();
	
	/**
	 * Returns the long-term (time-independent) participation rate for all sections
	 * 
	 * @return section participation rates
	 */
	public double[] getSectLongTermPartRates();
	
	/**
	 * Returns the long-term (time-independent) participation rate for the given section
	 * @param sectIndex
	 * @return section participation rate
	 */
	public double getSectLongTermPartRate(int sectIndex);
	
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
	
	/**
	 * Simple Poisson probability model implementation
	 */
	public static class Poisson extends AbstractFSS_ProbabilityModel {

		public Poisson(FaultSystemSolution fltSysSol) {
			super(fltSysSol);
		}

		@Override
		public double getProbability(int fltSysRupIndex,
				double ruptureRate, long forecastStartTimeMillis, double durationYears) {
			return TimeDepUtils.rateToPoissonProb(ruptureRate, durationYears);
		}

		@Override
		public double getProbabilityGain(int fltSysRupIndex,
				long forecastStartTimeMillis, double durationYears) {
			return 1d;
		}

		@Override
		public String getName() {
			return "Poisson";
		}
		
		@Override
		public String toString() {
			return getMetadataString();
		}
		
	}
	
	/**
	 * Weighted-combination of probability models. Note that it is assumed that each passed in model has all parameters
	 * set to their final values, and no parameters will be passed on to the ERF/GUI
	 */
	public static class WeightedCombination implements FSS_ProbabilityModel {
		
		private FSS_ProbabilityModel refModel;
		private WeightedList<? extends FSS_ProbabilityModel> probModels;
		private ParameterList params;
		private String name;

		public WeightedCombination(WeightedList<? extends FSS_ProbabilityModel> probModels) {
			this(null, probModels, null);
		}

		public WeightedCombination(String name, WeightedList<? extends FSS_ProbabilityModel> probModels, ParameterList params) {
			this.name = name;
			refModel = probModels.getValue(0);
			this.params = params;
			if (!probModels.isNormalized()) {
				// normalize it, but don't modify what was passed in
				probModels = new WeightedList<>(probModels);
				probModels.normalize();
			}
			if (!(probModels instanceof WeightedList.Unmodifiable<?>))
				// make it unmodifiable
				probModels = new WeightedList.Unmodifiable<>(probModels);
			if (name == null)
				name = "Weighted combination of "+probModels.size()+" models";
			this.probModels = probModels;
		}

		@Override
		public double getProbability(int fltSysRupIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears) {
			double ret = 0d;
			for (WeightedValue<? extends FSS_ProbabilityModel> value : probModels)
				ret += value.weight * value.value.getProbability(fltSysRupIndex,
						ruptureRate, forecastStartTimeMillis, durationYears);
			// already normalized in the constructor, don't need to re-normalize
			return ret;
		}

		@Override
		public double getProbabilityGain(int fltSysRupIndex, long forecastStartTimeMillis, double durationYears) {
			double ret = 0d;
			for (WeightedValue<? extends FSS_ProbabilityModel> value : probModels)
				ret += value.weight * value.value.getProbabilityGain(fltSysRupIndex,
						forecastStartTimeMillis, durationYears);
			// already normalized in the constructor, don't need to re-normalize
			return ret;
		}

		@Override
		public ParameterList getAdjustableParameters() {
			return params;
		}

		@Override
		public FaultSystemSolution getFaultSystemSolution() {
			return refModel.getFaultSystemSolution();
		}

		@Override
		public long[] getSectDOLE() {
			return refModel.getSectDOLE();
		}

		@Override
		public long getSectDOLE(int sectIndex) {
			return refModel.getSectDOLE(sectIndex);
		}

		@Override
		public void setSectDOLE(long[] sectDatesOfLastEvent) {
			for (int i=0; i<probModels.size(); i++)
				probModels.getValue(i).setSectDOLE(sectDatesOfLastEvent);
		}

		@Override
		public void setSectDOLE(int sectIndex, long sectDateOfLastEvent) {
			for (int i=0; i<probModels.size(); i++)
				probModels.getValue(i).setSectDOLE(sectIndex, sectDateOfLastEvent);
		}

		@Override
		public void resetSectDOLE() {
			for (int i=0; i<probModels.size(); i++)
				probModels.getValue(i).resetSectDOLE();
		}
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getMetadataString();
		}

		@Override
		public double[] getSectLongTermPartRates() {
			return refModel.getSectLongTermPartRates();
		}

		@Override
		public double getSectLongTermPartRate(int sectIndex) {
			return refModel.getSectLongTermPartRate(sectIndex);
		}

		@Override
		public int getNumSectsWithDOLE() {
			return refModel.getNumSectsWithDOLE();
		}
		
		/**
		 * Set to UCERF3 PDF/CDF discretizations and interpolation
		 */
		public void setUCERF3_Discretization() {
			for (int i=0; i<probModels.size(); i++) {
				if(probModels.getValue(i) instanceof AbstractProbDistProbabilityModel) {
					((AbstractProbDistProbabilityModel)probModels.getValue(i)).setProbDistsDiscretization(9, 18001, false);
					((AbstractProbDistProbabilityModel)probModels.getValue(i)).setIntegrationNormCDFsDiscretization(5d, 501);
				}
			
			}
		}
		
	}
	
}
