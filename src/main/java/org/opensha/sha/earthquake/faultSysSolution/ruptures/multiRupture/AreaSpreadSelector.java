package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

public class AreaSpreadSelector implements TargetRuptureSelector {

    private final SectionDistanceAzimuthCalculator distCalc;
    private final int count;
    private final double minDiffFraction;

    public AreaSpreadSelector(
            SectionDistanceAzimuthCalculator distCalc, int count, double minDiffFraction) {
        this.distCalc = distCalc;
        this.count = count;
        this.minDiffFraction = minDiffFraction;
    }

    @Override
    public List<ClusterRupture> select(Collection<ClusterRupture> ruptures) {
        if (ruptures.isEmpty()) {
            return new ArrayList<>();
        }

        // Compute areas and sort by area ascending
        List<RuptureWithArea> withAreas = new ArrayList<>();
        for (ClusterRupture rup : ruptures) {
            double area = computeArea(rup);
            withAreas.add(new RuptureWithArea(rup, area));
        }
        withAreas.sort(Comparator.comparingDouble(r -> r.area));

        // Deduplicate: keep rupture only if area differs by >= minDiffFraction from last kept
        List<RuptureWithArea> deduped = new ArrayList<>();
        deduped.add(withAreas.get(0));
        for (int i = 1; i < withAreas.size(); i++) {
            double lastArea = deduped.get(deduped.size() - 1).area;
            double currentArea = withAreas.get(i).area;
            if (lastArea == 0 || Math.abs(currentArea - lastArea) / lastArea >= minDiffFraction) {
                deduped.add(withAreas.get(i));
            }
        }

        // Pick count evenly spaced entries
        if (deduped.size() <= count) {
            List<ClusterRupture> result = new ArrayList<>();
            for (RuptureWithArea r : deduped) {
                result.add(r.rupture);
            }
            return result;
        }

        List<ClusterRupture> result = new ArrayList<>();
        int n = deduped.size() - 1;
        for (int i = 0; i < count; i++) {
            int index = (int) Math.round((double) i * n / (count - 1));
            result.add(deduped.get(index).rupture);
        }
        return result;
    }

    double computeArea(ClusterRupture rupture) {
        double totalArea = 0;
        for (FaultSection sect : rupture.buildOrderedSectionList()) {
            totalArea += distCalc.getSurface(sect).getArea();
        }
        return totalArea;
    }

    private static class RuptureWithArea {
        final ClusterRupture rupture;
        final double area;

        RuptureWithArea(ClusterRupture rupture, double area) {
            this.rupture = rupture;
            this.area = area;
        }
    }
}
