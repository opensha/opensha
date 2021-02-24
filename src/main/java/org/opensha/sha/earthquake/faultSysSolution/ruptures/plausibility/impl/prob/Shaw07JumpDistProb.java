package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

/**
 * Jump distance probability proposed in Shaw (2007)
 * 
 * @author kevin
 *
 */
public class Shaw07JumpDistProb extends JumpProbabilityCalc {
	
	private double a;
	private double r0;

	public Shaw07JumpDistProb(double a, double r0) {
		this.a = a;
		this.r0 = r0;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return false;
	}

	@Override
	public String getName() {
		return "Shaw07 [A="+CumulativeProbabilityFilter.optionalDigitDF.format(a)+", R0="+CumulativeProbabilityFilter.optionalDigitDF.format(r0)+"]";
	}

	@Override
	public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
		return a*Math.exp(-jump.distance/r0);
	}
	
}