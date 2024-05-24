package org.opensha.sha.earthquake.faultSysSolution.ruptures.MultiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

class RuptureJumpDistFilter implements MultiRuptureCompatibilityFilter {

    final double maxDist;
    final SectionDistanceAzimuthCalculator disAzCalc;

    public RuptureJumpDistFilter(double maxDist, SectionDistanceAzimuthCalculator disAzCalc) {
        this.maxDist = maxDist;
        this.disAzCalc = disAzCalc;
    }

    @Override
    public MultiRuptureCompatibilityResult apply(MultiRuptureCompatibilityResult previous,
                                                 ClusterRupture nucleation,
                                                 ClusterRupture target,
                                                 boolean verbose) {
        for (FaultSection targetSection : target.buildOrderedSectionList()) {
            for (FaultSection nucleationSection : nucleation.clusters[0].subSects) {
                double distance = disAzCalc.getDistance(targetSection, nucleationSection);
                if (distance <= maxDist) {
                    // Just take the first jump we find for now.
                    RuptureJump jump = new RuptureJump(nucleationSection, nucleation, targetSection, target, distance);
                    return new MultiRuptureCompatibilityResult(PlausibilityResult.PASS, jump);
                }
            }
        }
        return MultiRuptureCompatibilityResult.FAIL;
    }
}
