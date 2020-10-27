package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class ParentCoulombCompatibilityFilter extends JumpPlausibilityFilter
implements ScalarCoulombPlausibilityFilter {
	
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

	public ParentCoulombCompatibilityFilter(SubSectStiffnessCalculator stiffnessCalc,
			StiffnessAggregationMethod aggMethod, float threshold, Directionality directionality) {
		this.stiffnessCalc = stiffnessCalc;
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
		if (result == null || verbose) {
			// need to calculate it
			
			// swap out with full parent section clusters
			int fromID = newJump.fromCluster.parentSectionID;
			int toID = newJump.toCluster.parentSectionID;
			
			double forward = calc(fromID, toID);
			if (verbose) System.out.println(getShortName()+": forward, "+fromID+"=>"+toID+" = "+forward);
			if (directionality == Directionality.EITHER && (float)forward >= threshold && !verbose) {
				// short circuit
				result = true;
			} else {
				double reversed = calc(toID, fromID);
				if (verbose) System.out.println(getShortName()+": reversed, "+toID+"=>"+fromID+" = "+reversed);
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
			synchronized (passCache) {
				passCache.put(pair, result);
			}
		}
		return result ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double calc(int sourceID, int receiverID) {
		StiffnessResult stiffness = stiffnessCalc.calcParentStiffness(StiffnessType.CFF, sourceID, receiverID);
		return stiffness.getValue(aggMethod);
	}

	@Override
	public String getShortName() {
		if (threshold == 0f)
			return "ParentCFF≥0";
		return "ParentCFF≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Parent Section Coulomb ≥ "+(float)threshold;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumJumps()  == 0)
			return null;
		float min = Float.POSITIVE_INFINITY;
		for (Jump jump : rupture.getJumpsIterable())
			min = Float.min(min, getValue(rupture, jump));
		return min;
	}

	@Override
	public Float getValue(ClusterRupture rupture, Jump newJump) {
		// swap out with full parent section clusters
		int fromID = newJump.fromCluster.parentSectionID;
		int toID = newJump.toCluster.parentSectionID;

		double forward = calc(fromID, toID);
		double reversed = calc(toID, fromID);
		switch (directionality) {
		case BOTH:
			return Float.min((float)forward, (float)reversed);
		case EITHER:
			return Float.max((float)forward, (float)reversed);
		case SUM:
			return (float)(forward + reversed);

		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast(threshold);
	}

	@Override
	public SubSectStiffnessCalculator getStiffnessCalc() {
		return stiffnessCalc;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

}
