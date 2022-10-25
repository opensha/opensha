package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;

public class HardDistCutoffJumpProbCalc implements BinaryJumpProbabilityCalc, DistDependentJumpProbabilityCalc {
	
	private double maxDist;

	public HardDistCutoffJumpProbCalc(double maxDist) {
		this.maxDist = maxDist;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return false;
	}

	@Override
	public String getName() {
		return "MaxDist="+MaxJumpDistModels.oDF.format(maxDist)+"km";
	}

	@Override
	public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
		return (float)jump.distance <= (float)maxDist;
	}

	@Override
	public double calcJumpProbability(double distance) {
		if ((float)distance < (float)maxDist)
			return 1d;
		return 0;
	}

	@Override
	public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
		return calcJumpProbability(jump.distance);
	}
	
}