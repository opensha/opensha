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
 * Checks self-coupling: the net Coulomb stress from all sections onto each component (crustal
 * and subduction separately) must exceed the threshold. Also rejects crustal components with total area
 * below 100 km&sup2;.
 */
public class MultiRuptureSelfStiffnessFilter implements MultiRuptureCompatibilityFilter {

    final private AggregatedStiffnessCalculator aggCalc;
    final double threshold;

    public MultiRuptureSelfStiffnessFilter(SubSectStiffnessCalculator stiffnessCalc) {
        this(stiffnessCalc, 0);
    }

    public MultiRuptureSelfStiffnessFilter(SubSectStiffnessCalculator stiffnessCalc, double threshold) {
        this.aggCalc = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM);
        this.threshold = threshold;
    }

    public PlausibilityResult apply(List<FaultSection> subduction, List<FaultSection> crustal) {
        if(crustal.stream().mapToDouble(s->s.getArea(false)).sum() < 100000000) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
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
