package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.StiffnessAggregation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the net Coulomb compatibility of a rupture. For each participating section, it computes
 * Coulomb with all other sections as a source that section as the receiver.
 * 
 * @author kevin
 *
 */
public class NetRuptureCoulombFilter implements ScalarCoulombPlausibilityFilter {
	
	private AggregatedStiffnessCalculator aggCalc;
	private Range<Float> acceptableRange;
	
	// can't use the fitler data shortcut for anything where the final aggregation step involves a median
	private static EnumSet<AggregationMethod> filterDataAggMethods = EnumSet.complementOf(
			EnumSet.of(AggregationMethod.GREATER_MEAN_MEDIAN, AggregationMethod.GREATER_SUM_MEDIAN, AggregationMethod.MEDIAN));

	public NetRuptureCoulombFilter(AggregatedStiffnessCalculator aggCalc, float threshold) {
		this(aggCalc, Range.atLeast(threshold));
	}

	public NetRuptureCoulombFilter(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange) {
		super();
		this.aggCalc = aggCalc;
		Preconditions.checkArgument(acceptableRange.hasLowerBound() || acceptableRange.hasUpperBound());
		this.acceptableRange = acceptableRange;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumSects() == 1)
			return PlausibilityResult.PASS;
		float val = getValue(rupture);
		PlausibilityResult result = acceptableRange.contains(val) ?
				PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": val="+val+", result="+result);
		return result;
	}

	@Override
	public String getShortName() {
		String name = aggCalc.getScalarShortName();
		return name+getRangeStr();
	}

	@Override
	public String getName() {
		String name = "Net Rupture ["+aggCalc.getScalarName()+"]";
		return name+getRangeStr();
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumSects() == 1)
			return null;
		
		// TODO think about filterdata approach to speed it up
//		if (rupture instanceof FilterDataClusterRupture && filterDataAggMethods.contains(aggCalc.getSectsToSectsAggMethod())) {
//			FilterDataClusterRupture fdRupture = (FilterDataClusterRupture)rupture;
//			Object data = fdRupture.getFilterData(this);
//			StiffnessAggregation aggregation = null;
//			if (data != null && data instanceof FilterData) {
//				FilterData filterData = (FilterData)data;
//				List<FaultSection> newSects = new ArrayList<>();
//				for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
//					for (FaultSection sect : cluster.subSects) {
//						Preconditions.checkState(rupture.contains(sect));
//						if (!filterData.prevRupture.contains(sect))
//							newSects.add(sect);
//					}
//				}
//				if (newSects.isEmpty()) {
//					Preconditions.checkState(rupture.unique.equals(filterData.prevRupture.unique));
//					aggregation = filterData.prevAggregation;
//				} else {
//					result = new RuptureCoulombResult(filterData.prevResult, newSects, stiffnessCalc, aggMethod);
//				}
//			} else {
//				List<FaultSection> allSects = new ArrayList<>();
//				for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
//					allSects.addAll(cluster.subSects);
//				aggregation = aggCalc.getSectsToSectsAggregation(allSects, allSects);
//			}
//			fdRupture.addFilterData(this, new FilterData(rupture, aggregation));
//			return (float)aggregation.get(aggCalc.getSectsToSectsAggMethod());
//		}
		List<FaultSection> allSects = new ArrayList<>();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			allSects.addAll(cluster.subSects);
		return (float)aggCalc.calc(allSects, allSects);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return acceptableRange;
	}
	
	private static class FilterData {
		private final ClusterRupture prevRupture;
		private final StiffnessAggregation prevAggregation;
		
		public FilterData(ClusterRupture prevRupture,StiffnessAggregation prevAggregation) {
			super();
			this.prevRupture = prevRupture;
			this.prevAggregation = prevAggregation;
		}
	}

	@Override
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

}
