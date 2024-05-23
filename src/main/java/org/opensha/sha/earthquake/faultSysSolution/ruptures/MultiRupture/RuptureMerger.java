package org.opensha.sha.earthquake.faultSysSolution.ruptures.MultiRupture;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RuptureMerger {

    final boolean VERBOSE = true;
    List<MultiRuptureCompatibilityFilter> compatibilityFilters = new ArrayList<>();

    public void addFilter(MultiRuptureCompatibilityFilter filter) {
        compatibilityFilters.add(filter);
    }

    transient int mergeCounter = 0;

    /**
     * Create new ruptures that combine the nucleation rupture and any of the target ruptures if they
     * are compatible.
     * It should be possible to use the resulting ruptures as new nucleation ruptures in order to
     * create more complex ruptures. But we need to implement avoiding duplicate sections beforehand.
     *
     * @param nucleation A rupture that we want to combine with other ruptures
     * @param targets    a list of ruptures that are potential jump targets from the nucleation rupture.
     * @return a list of merged ruptures.
     */
    public List<ClusterRupture> merge(ClusterRupture nucleation, List<ClusterRupture> targets) {
        List<ClusterRupture> result = new ArrayList<>();

        for (ClusterRupture target : targets) {
            MultiRuptureCompatibilityResult compatibility = MultiRuptureCompatibilityResult.PASS;
            for (MultiRuptureCompatibilityFilter filter : compatibilityFilters) {
                compatibility = compatibility.and(filter.apply(compatibility, nucleation, target, VERBOSE));
                if (!compatibility.canContinue()) {
                    break;
                }
            }
            if (compatibility.isPass()) {
                Preconditions.checkState(compatibility.jump != null);
                result.add(MultiClusterRupture.takeSplayJump(compatibility.jump));
            }
        }

        int counter = mergeCounter++;
        if (counter % 10 == 0) {
            System.out.print('.');
        }
        return result;
    }

    public List<ClusterRupture> merge(List<ClusterRupture> nucleations, List<ClusterRupture> targets) {
        return nucleations
                .parallelStream()
                .map(nucleation -> merge(nucleation, targets))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) throws IOException {

        // load ruptures and split them up into crustal and subduction
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(
                new File("C:\\Users\\user\\GNS\\nzshm-opensha\\TEST\\ruptures\\rupset-disjointed.zip"));
        List<ClusterRupture> nucleationRuptures = new ArrayList<>();
        List<ClusterRupture> targetRuptures = new ArrayList<>();
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        for (ClusterRupture rupture : cRups) {
            if (rupture.clusters[0].subSects.get(0).getSectionName().contains("row:")) {
                nucleationRuptures.add(rupture);
            } else {
                targetRuptures.add(rupture);
            }
        }

        System.out.println("Loaded " + nucleationRuptures.size() + " nucleation ruptures");
        System.out.println("Loaded " + targetRuptures.size() + " target ruptures");

        // set up RuptureMerger
        RuptureMerger merger = new RuptureMerger();
        SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        RuptureJumpDistFilter distFilter = new RuptureJumpDistFilter(5, distCalc);
        merger.addFilter(distFilter);

        // run RuptureMerger for one nucleation rupture for now
        ClusterRupture testNucleation = cRups.get(97653);
        List<ClusterRupture> mergedRuptures = merger.merge(testNucleation, targetRuptures);

//        List<ClusterRupture> shortList = nucleationRuptures.stream()
//                .filter(rupture -> rupture.clusters[0].subSects.get(0).getFaultTrace().first().lat > -42 &&
//                                rupture.clusters[0].subSects.get(0).getFaultTrace().first().lat < -40.5 &&
//                                rupture.clusters[0].subSects.get(0).getFaultTrace().last().lat > -42 &&
//                                rupture.clusters[0].subSects.get(0).getFaultTrace().last().lat < -40.5)
//                .filter(rupture -> rupture.buildOrderedSectionList().size() > 5)
//                .collect(Collectors.toList())
//                .subList(0, 50);
//        List<ClusterRupture> mergedRuptures = merger.merge(shortList, targetRuptures);

        System.out.println("Generated " + mergedRuptures.size() + " ruptures.");

        // write only the merged ruptures
        FaultSystemRupSet resultRupSet =
                FaultSystemRupSet.builderForClusterRups(
                                rupSet.getFaultSectionDataList(),
                                mergedRuptures)
                        // magnitudes will be wrong, but this is required
                        .forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
                        .build();
        resultRupSet.write(new File("/tmp/mergedRupset.zip"));

        // quick sanity check
        RupCartoonGenerator.plotRupture(new File("/tmp/"), "mergedRupture", mergedRuptures.get(0), "merged rupture", false, true);

    }
}
