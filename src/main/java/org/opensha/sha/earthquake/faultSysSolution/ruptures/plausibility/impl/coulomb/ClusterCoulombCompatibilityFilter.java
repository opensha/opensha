package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb;

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
	private Range<Float> acceptableRange;

	public ClusterCoulombCompatibilityFilter(AggregatedStiffnessCalculator aggCalc, float threshold) {
		this(aggCalc, Range.atLeast(threshold));
	}
	public ClusterCoulombCompatibilityFilter(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange) {
		this.aggCalc = aggCalc;
		this.acceptableRange = acceptableRange;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double worstVal = doTest(new ArrayList<>(), rupture.clusters[0], rupture.getTreeNavigator(),
				verbose, !verbose);
		PlausibilityResult result =
				acceptableRange.contains((float)worstVal) ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
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
	
	private float doTest(List<FaultSection> curSects, FaultSubsectionCluster nextCluster,
			RuptureTreeNavigator navigator, boolean verbose, boolean shortCircuit) {
		float val = Float.POSITIVE_INFINITY;
		if (!curSects.isEmpty()) {
			// check rupture so far
			val = (float)aggCalc.calc(curSects, nextCluster.subSects);
			if (verbose)
				System.out.println(getShortName()+": "+curSects.size()+" sects to "
						+nextCluster+", val="+val);
			else if (!acceptableRange.contains((float)val))
				return val;
		}
		
		if (navigator != null) {
			for (FaultSubsectionCluster descendant : navigator.getDescendants(nextCluster)) {
				List<FaultSection> newSects = new ArrayList<>(curSects);
				newSects.addAll(nextCluster.subSects);
				val = getWorseValue((float)val, (float)doTest(newSects, descendant, navigator, verbose, shortCircuit));
				if (!verbose && !acceptableRange.contains((float)val))
					break;
			}
		}
		
		return val;
	}

	@Override
	public String getShortName() {
		String type = "["+aggCalc.getScalarShortName()+"]";
		return "JumpCluster"+type+getRangeStr();
	}

	@Override
	public String getName() {
		String type = "["+aggCalc.getScalarName()+"]";
		return "Jump Cluster "+type+" "+getRangeStr();
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return true;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return acceptableRange;
	}

	@Override
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

}
