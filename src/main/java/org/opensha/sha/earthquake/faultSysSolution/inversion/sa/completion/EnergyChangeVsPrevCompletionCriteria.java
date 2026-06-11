package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class EnergyChangeVsPrevCompletionCriteria implements CompletionCriteria {
	
	private double minFractImprovement;
	private int numConsecutiveRequired;
	
	private int streak = 0;
	
	private InversionState prevState;
	
	private static final boolean D = false;

	public EnergyChangeVsPrevCompletionCriteria(double minFractImprovement, int numConsecutiveRequired) {
		this.minFractImprovement = minFractImprovement;
		this.numConsecutiveRequired = numConsecutiveRequired;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		if (D) System.out.println("EnergyChangeVsPrev: isSatisfied called with "+state);
		if (prevState != null && state.iterations < prevState.iterations) {
			// we went back in time, assume it's being resused and start over
			System.out.println("EnergyChangeVsPrev: we went back in time! "+state.iterations+" < "+prevState.iterations);
			prevState = null;
			streak = 0;
		}
		if (prevState == null) {
			if (D) System.out.println("EnergyChangeVsPrev: no prev state");
			prevState = state;
			return false;
		}
		double prevE = prevState.energy[0];
		double newE = state.energy[0];
		double fractImprovement = (prevE - newE)/prevE;
		if (fractImprovement <= minFractImprovement)
			streak++;
		else
			streak = 0;
		if (D) System.out.println("EnergyChangeVsPrev: frctImprov= ("+(float)prevE+" - "+(float)newE+") / "+(float)prevE
				+" = "+(float)fractImprovement+"; streak="+streak);
		prevState = state;
		return streak >= numConsecutiveRequired;
	}
	
	@Override
	public String toString() {
		return "EnergyChangeVsPrev(minFractImprovement: "+(float)minFractImprovement+", streak="+numConsecutiveRequired+")";
	}

}
