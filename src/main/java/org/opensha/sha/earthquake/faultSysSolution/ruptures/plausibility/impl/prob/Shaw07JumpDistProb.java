package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

/**
 * Jump distance probability proposed in Shaw and Dieterich (2007)
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
		if ((float)a != 1f)
			return "Shaw07 [A="+CumulativeProbabilityFilter.optionalDigitDF.format(a)+", R₀="+CumulativeProbabilityFilter.optionalDigitDF.format(r0)+"]";
		return "Shaw07 [R₀="+CumulativeProbabilityFilter.optionalDigitDF.format(r0)+"]";
	}

	@Override
	public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
		return calcJumpProbability(jump.distance);
	}
	
	public double calcJumpProbability(double distance) {
		return a*Math.exp(-distance/r0);
	}
	
	public static void main(String[] args) {
		Shaw07JumpDistProb prob = new Shaw07JumpDistProb(1d, 3d);
		for (double d=0; d<=25; d += 0.1)
			System.out.println((float)d+" km:\t"+prob.calcJumpProbability(d));
	}
	
}