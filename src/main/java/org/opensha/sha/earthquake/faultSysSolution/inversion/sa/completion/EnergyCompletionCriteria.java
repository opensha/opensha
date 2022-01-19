package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class EnergyCompletionCriteria implements CompletionCriteria {
	
	private double maxEnergy;
	
	/**
	 * Creates an EnergyCompletionCriteria that will be satisfied when the best solution energy is
	 * less than or equal to the given energy level.
	 * @param maxEnergy
	 */
	public EnergyCompletionCriteria(double maxEnergy) {
		this.maxEnergy = maxEnergy;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		return state.energy[0] <= maxEnergy;
	}
	
	@Override
	public String toString() {
		return "EnergyCompletionCriteria(maxEnergy: "+maxEnergy+")";
	}
	
	public double getMaxEnergy() {
		return maxEnergy;
	}

}
