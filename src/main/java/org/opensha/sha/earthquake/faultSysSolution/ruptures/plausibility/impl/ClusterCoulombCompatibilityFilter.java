package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.collect.Range;

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
public class ClusterCoulombCompatibilityFilter implements ScalarCoulombPlausibilityFilter {
	
	private AggregatedStiffnessCalculator aggCalc;
	private float threshold;

	public ClusterCoulombCompatibilityFilter(AggregatedStiffnessCalculator aggCalc, float threshold) {
		this.aggCalc = aggCalc;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double worstVal = doTest(new ArrayList<>(), rupture.clusters[0], rupture.getTreeNavigator(),
				verbose, !verbose);
		PlausibilityResult result =
				(float)worstVal >= threshold ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": worst val="+worstVal+"\tresult="+result.name());
		return result;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumJumps()  == 0)
			return null;
		return (float)doTest(new ArrayList<>(), rupture.clusters[0], rupture.getTreeNavigator(),
				false, false);
	}
	
	private double doTest(List<FaultSection> curSects, FaultSubsectionCluster nextCluster,
			RuptureTreeNavigator navigator, boolean verbose, boolean shortCircuit) {
		double val = Double.POSITIVE_INFINITY;
		if (!curSects.isEmpty()) {
			// check rupture so far
			val = aggCalc.calc(curSects, nextCluster.subSects);
			if (verbose)
				System.out.println(getShortName()+": "+curSects.size()+" sects to "
						+nextCluster+", val="+val);
			else if ((float)val < threshold)
				return val;
		}
		
		if (navigator != null) {
			for (FaultSubsectionCluster descendant : navigator.getDescendants(nextCluster)) {
				List<FaultSection> newSects = new ArrayList<>(curSects);
				newSects.addAll(nextCluster.subSects);
				val = Math.min(val, doTest(newSects, descendant, navigator, verbose, shortCircuit));
				if (!verbose && (float)val < threshold)
					break;
			}
		}
		
		return val;
	}

	@Override
	public String getShortName() {
		if (threshold == 0f)
			return "JumpClusterCFF≥0";
		return "JumpClusterCFF≥"+(float)threshold;
	}

	@Override
	public String getName() {
		return "Jump Cluster Coulomb  ≥ "+(float)threshold;
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return true;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast((float)threshold);
	}

	@Override
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

}
