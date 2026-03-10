package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a sufficient fraction of individual sections in each component pass a per-section
 * stiffness test. Uses a normalised-by-count aggregation to evaluate each section, then requires
 * that at least {@code threshold} fraction of sections pass. More granular than
 * {@link MultiRuptureSelfStiffnessFilter} which only checks aggregate values.
 */
public class MultiRuptureSelfStiffnessSpreadFilterFilter implements MultiRuptureCompatibilityFilter {

    final private AggregatedStiffnessCalculator aggCalc;
    final private AggregatedStiffnessCalculator normByCountAgg;
    final double sectionThreshold;
    final double threshold;

    public MultiRuptureSelfStiffnessSpreadFilterFilter(SubSectStiffnessCalculator stiffnessCalc) {
        this(stiffnessCalc, 0, 0.9);
    }

    public MultiRuptureSelfStiffnessSpreadFilterFilter(SubSectStiffnessCalculator stiffnessCalc,
                                                       double sectionThreshold, double threshold) {
        this.aggCalc = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM);
        this.normByCountAgg = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.NUM_POSITIVE,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.NORM_BY_COUNT);
        this.threshold = threshold;
        this.sectionThreshold =sectionThreshold;
    }

    public boolean applyToSection(List<FaultSection> sources, List<FaultSection> receiver) {
        return normByCountAgg.calc(sources, receiver) >= sectionThreshold;
    }

    public PlausibilityResult applyToComponent(List<FaultSection> all, List<FaultSection> targets) {
        int thresholdCount = (int) Math.round(threshold * targets.size());
        int passCount = 0;
        List<FaultSection> targetList = new ArrayList<>();
        targetList.add(targetList.get(0));
        for (FaultSection section : targets) {
            targetList.set(0, section);
            if (applyToSection(all, targetList)) {
                passCount++;
                if (passCount >= thresholdCount) {
                    return PlausibilityResult.PASS;
                }
            }
        }
        return PlausibilityResult.FAIL_HARD_STOP;
    }

    public PlausibilityResult apply(List<FaultSection> subduction, List<FaultSection> crustal) {
        List<FaultSection> all = new ArrayList<>();
        all.addAll(subduction);
        all.addAll(crustal);
        double allCru = aggCalc.calc(all, crustal);
        if (allCru < threshold) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
        double allSub = aggCalc.calc(all, subduction);
        if (allSub < threshold) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
        return PlausibilityResult.PASS;
    }

    @Override
    public PlausibilityResult apply(MultiRuptureJump jump, boolean verbose) {
        ClusterRupture fromRup = jump.fromRupture;
        List<FaultSection> fromSects = new ArrayList<>(fromRup.getTotalNumSects());
        for (FaultSubsectionCluster cluster : fromRup.getClustersIterable()) {
            fromSects.addAll(cluster.subSects);
        }
        ClusterRupture toRup = jump.toRupture;
        List<FaultSection> toSects = new ArrayList<>(toRup.getTotalNumSects());
        for (FaultSubsectionCluster cluster : toRup.getClustersIterable()) {
            toSects.addAll(cluster.subSects);
        }

        return apply(fromSects, toSects);
    }
}
