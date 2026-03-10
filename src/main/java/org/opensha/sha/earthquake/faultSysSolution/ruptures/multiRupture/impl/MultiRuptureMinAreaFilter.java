package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects multi-rupture jumps where the crustal or subduction component has a total rupture
 * area below the configured minimum thresholds (specified in km&sup2;).
 */
public class MultiRuptureMinAreaFilter implements MultiRuptureCompatibilityFilter {

    final double minCrustal;
    final double minSubduction;

    final static int SQUARE_KILOMETERS_TO_METERS = 1000000;

    public MultiRuptureMinAreaFilter(double minCrustal, double minSubduction) {
        this.minCrustal = minCrustal * SQUARE_KILOMETERS_TO_METERS;
        this.minSubduction = minSubduction * SQUARE_KILOMETERS_TO_METERS;
    }

    public PlausibilityResult apply(List<FaultSection> subduction, List<FaultSection> crustal) {

        if (minCrustal > 0 && crustal.stream().mapToDouble(s -> s.getArea(false)).sum() < minCrustal) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
        if (minSubduction > 0 && subduction.stream().mapToDouble(s -> s.getArea(false)).sum() < minSubduction) {
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
