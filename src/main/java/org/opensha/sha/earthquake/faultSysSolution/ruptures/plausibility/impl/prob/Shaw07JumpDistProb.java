package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.text.DecimalFormat;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;

import com.google.common.base.Preconditions;

/**
 * Jump distance probability proposed in Shaw and Dieterich (2007)
 * 
 * @author kevin
 *
 */
public class Shaw07JumpDistProb implements DistDependentJumpProbabilityCalc {
	
	private double a;
	private double r0;
	
	public static final double R0_DEFAULT = 3d;

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
			return "Shaw07 [A="+optionalDigitDF.format(a)+", R₀="+optionalDigitDF.format(r0)+"]";
		return "Shaw07 [R₀="+optionalDigitDF.format(r0)+"]";
	}
	
	static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	public double calcJumpProbability(double distance) {
		return calcJumpProbability(distance, a, r0);
	}
	
	public static double calcJumpProbability(double distance, double a, double r0) {
		return a*Math.exp(-distance/r0);
	}
	
	public double calcJumpDistance(double probability) {
		return calcJumpDistance(probability, a, r0);
	}
	
	public static double calcJumpDistance(double probability, double a, double r0) {
		Preconditions.checkState(probability > 0 && probability <= 1, "Bat probability: %s", probability);
		return r0*Math.log(a/probability);
	}
	
	public static void main(String[] args) {
		Shaw07JumpDistProb prob = new Shaw07JumpDistProb(1d, 3d);
		for (double d=0; d<=25; d += 0.1)
			System.out.println((float)d+" km:\t"+prob.calcJumpProbability(d));
	}
	
}