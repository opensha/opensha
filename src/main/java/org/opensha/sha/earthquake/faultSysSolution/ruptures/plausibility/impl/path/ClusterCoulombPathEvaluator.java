package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.util.Collection;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * A cluster-by-cluster Coulomb path evaluator
 * 
 * @author kevin
 *
 */
public class ClusterCoulombPathEvaluator extends ScalarCoulombPathEvaluator {

	public ClusterCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			PlausibilityResult failureType) {
		super(aggCalc, acceptableRange, failureType);
	}

	@Override
	public Float getAdditionValue(Collection<FaultSection> curSects, PathAddition addition, boolean verbose) {
		return (float)aggCalc.calc(curSects, addition.toSects);
		//			return (float)aggCalc.calc(addition.fromCluster.subSects, addition.toSects);
	}

	@Override
	protected PathNavigator getPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster) {
		return new ClusterPathNavigator(nucleationCluster, rupture.getTreeNavigator());
	}

	@Override
	public String getShortName() {
		return "Cl ["+aggCalc.getScalarShortName()+"]"+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
	}

	@Override
	public String getName() {
		return "Cluster ["+aggCalc.getScalarName()+"] "+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
	}

}