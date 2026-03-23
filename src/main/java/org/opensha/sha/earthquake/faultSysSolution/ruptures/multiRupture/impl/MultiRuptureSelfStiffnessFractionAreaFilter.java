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
 * Checks that the fraction of each component's area (crustal and subduction) with positive
 * self-stiffness meets a threshold. Unlike {@link MultiRuptureSelfStiffnessFilter} which checks
 * aggregate stiffness, this filter ensures that a sufficient spatial proportion of each
 * component is positively coupled.
 */
public class MultiRuptureSelfStiffnessFractionAreaFilter implements MultiRuptureCompatibilityFilter {

    final private AggregatedStiffnessCalculator aggCalc;
    final double threshold;
    final double[] areas;

    public MultiRuptureSelfStiffnessFractionAreaFilter(SubSectStiffnessCalculator stiffnessCalc, double threshold, List<? extends FaultSection> sections) {
        this.aggCalc = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM);
        this.threshold = threshold;
        this.areas = new double[sections.size()];
        sections.parallelStream().forEach(s ->
                areas[s.getSectionId()] = s.getArea(false));
    }

    public PlausibilityResult apply(List<FaultSection> subduction, List<FaultSection> crustal) {
        List<FaultSection> all = new ArrayList<>();
        all.addAll(subduction);
        all.addAll(crustal);
        double crustalArea = crustal.stream().mapToDouble(s -> areas[s.getSectionId()]).sum();
        double passCrustalArea = crustal.stream()
                .filter(s -> aggCalc.calc(all, List.of(s)) > 0)
                .mapToDouble(s -> areas[s.getSectionId()])
                .sum();
        if (passCrustalArea / crustalArea < threshold) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
        double subArea = subduction.stream().mapToDouble(s -> areas[s.getSectionId()]).sum();
        double passSubArea = subduction.stream()
                .filter(s -> aggCalc.calc(all, List.of(s)) > 0)
                .mapToDouble(s -> areas[s.getSectionId()])
                .sum();
        if (passSubArea / subArea < threshold) {
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
