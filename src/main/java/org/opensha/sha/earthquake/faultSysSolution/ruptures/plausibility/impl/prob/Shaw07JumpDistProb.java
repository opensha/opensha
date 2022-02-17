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
	
	/**
	 * This calculates an a value that would shift the model to the right by the given horizontal offset
	 * 
	 * @param a
	 * @param r0
	 * @param horzOffset
	 * @return
	 */
	public static Shaw07JumpDistProb forHorzOffset(double a, double r0, double horzOffset) {
		double a0 = Math.exp(horzOffset/r0);
		return new Shaw07JumpDistProb(a*a0, r0);
	}

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
		double prob = a*Math.exp(-distance/r0);
		if (a > 1)
			// apply minimum as raw probabilities can be >1 with a>1
			prob = Math.min(1d, prob);
		return prob;
	}
	
	public double calcJumpDistance(double probability) {
		return calcJumpDistance(probability, a, r0);
	}
	
	public static double calcJumpDistance(double probability, double a, double r0) {
		Preconditions.checkState(probability > 0 && probability <= 1, "Bad probability: %s", probability);
		return r0*Math.log(a/probability);
	}
	
	public static void main(String[] args) {
		Shaw07JumpDistProb prob = new Shaw07JumpDistProb(1d, 3d);
		for (double d=0; d<=25; d += 0.1)
			System.out.println((float)d+" km:\t"+prob.calcJumpProbability(d));
	}
	
}