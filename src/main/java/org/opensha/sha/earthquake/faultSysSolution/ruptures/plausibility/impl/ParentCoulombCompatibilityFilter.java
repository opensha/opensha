package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class ParentCoulombCompatibilityFilter extends JumpPlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;
	private Directionality directionality;
	
	private transient Map<IDPairing, Boolean> passCache;
	
	public enum Directionality {
		EITHER,
		BOTH,
		SUM
	}

	public ParentCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold, Directionality directionality) {
		this.stiffnessCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
		this.directionality = directionality;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		if (passCache == null) {
			synchronized (this) {
				if (passCache == null)
					passCache = new HashMap<>();
			}
		}
		int id1, id2;
		if (newJump.fromCluster.parentSectionID < newJump.toCluster.parentSectionID) {
			id1 = newJump.fromCluster.parentSectionID;
			id2 = newJump.toCluster.parentSectionID;
		} else {
			id2 = newJump.fromCluster.parentSectionID;
			id1 = newJump.toCluster.parentSectionID;
		}
		if (id1 == id2)
			return PlausibilityResult.PASS;
		IDPairing pair = new IDPairing(id1, id2);
		Boolean result = passCache.get(pair);
		if (result == null) {
			// need to calculate it
			double forward = calc(newJump.fromCluster, newJump.toCluster);
			if (directionality == Directionality.EITHER && (float)forward >= threshold) {
				// short circuit
				result = true;
			} else {
				double reversed = calc(newJump.toCluster, newJump.fromCluster);
				switch (directionality) {
				case BOTH:
					result = (float)reversed >= threshold && (float)forward >= threshold;
					break;
				case EITHER:
					result = (float)reversed >= threshold || (float)forward >= threshold;
					break;
				case SUM:
					result = (float)(reversed+forward) >= threshold;
					break;

				default:
					throw new IllegalStateException();
				}
			}
			passCache.put(pair, result);
		}
		return result ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(FaultSubsectionCluster source, FaultSubsectionCluster receiver) {
		StiffnessResult[] stiffness = stiffnessCalc.calcClusterStiffness(source, receiver);
		return stiffnessCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
	}

	@Override
	public String getShortName() {
		return "ParentCoulomb≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Parent Section Coulomb ≥ "+(float)threshold;
	}

}
