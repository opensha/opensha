package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each added cluster as a rupture is built. It ensures
 * that, conditioned on unit slip of the existing rupture, each new cluster has a net Coulomb compatibility
 * at or above the given threshold. For example, to ensure that each added cluster is net postitive, set
 * the threshold to 0.
 * 
 * @author kevin
 *
 */
public class ClusterCoulombCompatibilityFilter extends JumpPlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public ClusterCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this.stiffnessCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		StiffnessResult[] stiffness = stiffnessCalc.calcAggRupToClusterStiffness(rupture, newJump.toCluster);
		double val = stiffnessCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
		PlausibilityResult result =
				(float)val >= threshold ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": val="+val+"\tresult="+result.name());
		return result;
	}

	@Override
	public String getShortName() {
		return "JumpClusterCoulomb≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Jump Cluster Coulomb  ≥ "+(float)threshold;
	}

}
