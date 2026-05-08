package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import org.opensha.sha.util.TectonicRegionType;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the creation of joint (multi-fault) ruptures by merging nucleation ruptures
 * (typically subduction) with nearby target ruptures (typically crustal). Uses spatial indexing
 * via {@link RuptureProximityLookup} to find candidates, then applies a chain of
 * {@link MultiRuptureCompatibilityFilter}s (e.g. Coulomb stress checks) to validate each pair.
 * Compatible pairs are merged into {@link MultiClusterRupture}s via splay jumps.
 *
 * <p>Merging is parallelised. Use {@link #setVerbose(boolean)} to control diagnostic output.
 */
public class RuptureMerger {

    static DecimalFormat oDF = new DecimalFormat("0.##");

    boolean VERBOSE = true;
    List<MultiRuptureCompatibilityFilter> compatibilityFilters = new ArrayList<>();
    TargetRuptureSelector selector;
    final SectionDistanceAzimuthCalculator disAzCalc;
    final double maxJumpDist;
    final List<ClusterRupture> nucleationRuptures;
    final List<ClusterRupture> targetRuptures;

    final Map<Integer, Collection<ClusterRupture>> sectionToRupture;

    public RuptureMerger(FaultSystemRupSet rupSet, double maxJumpDist, List<ClusterRupture> nucleationRuptures, List<ClusterRupture> targetRuptures) {
        disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        this.maxJumpDist = maxJumpDist;
        this.nucleationRuptures = nucleationRuptures;
        this.targetRuptures = targetRuptures;
        this.sectionToRupture = buildJumpLookup();
        System.out.println("Indices Created. sectionToRupture: " + sectionToRupture.size() + " entries");
    }

    public SectionDistanceAzimuthCalculator getDisAzCalc(){
        return disAzCalc;
    }

    public void setTargetSelector(TargetRuptureSelector selector) {
        this.selector = selector;
    }

    public Map<Integer, Collection<ClusterRupture>> buildJumpLookup() {
        RuptureProximityLookup lookup = new RuptureProximityLookup(disAzCalc, targetRuptures, maxJumpDist);
        Set<Integer> nucleationSections = new HashSet<>();
        for (ClusterRupture rupture : nucleationRuptures) {
            for (FaultSection sect : rupture.buildOrderedSectionList()) {
                nucleationSections.add(sect.getSectionId());
            }
        }
        return lookup.findNearbyRuptures(nucleationSections);
    }

    /**
     * Returns target ruptures that are spatially close to the given nucleation rupture,
     * filtered through the configured {@link TargetRuptureSelector}.
     */
    public List<ClusterRupture> getJumpTargets(ClusterRupture nucleationRupture) {
        Set<ClusterRupture> ruptures = new HashSet<>();
        for(FaultSection section: nucleationRupture.buildOrderedSectionList()){
            ruptures.addAll(sectionToRupture.getOrDefault(section.getSectionId(), Collections.emptyList()));
        }
        return selector.select(ruptures);
    }


    public long countPossibleJumps(){
        return nucleationRuptures.parallelStream().mapToLong(
                r -> getJumpTargets(r).size()
        ).sum();
    }

    public void addFilter(MultiRuptureCompatibilityFilter filter) {
        compatibilityFilters.add(filter);
    }
    
    public void setVerbose(boolean verbose) {
    	this.VERBOSE = verbose;
    }

    transient int mergeCounter = 0;

    /**
     * Creates a jump between the two ruptures if at least two fault sections are within maxJumpDist.
     * Does not guarantee to create the shortest jump.
     * @param nucleation
     * @param target
     * @return
     */
    protected MultiRuptureJump makeJump(ClusterRupture nucleation, ClusterRupture target) {
        return new MultiRuptureJump(nucleation.clusters[0].startSect, nucleation, target.clusters[0].startSect, target, maxJumpDist);
    }

    /**
     * Create new ruptures that combine the nucleation rupture and any of the target ruptures if they
     * are compatible.
     * It should be possible to use the resulting ruptures as new nucleation ruptures in order to
     * create more complex ruptures. But we need to implement avoiding duplicate sections beforehand.
     *
     * @param nucleation A rupture that we want to combine with other ruptures
     * @return a list of merged ruptures.
     */
    public List<ClusterRupture> merge(ClusterRupture nucleation) {
        List<ClusterRupture> result =

        getJumpTargets(nucleation).parallelStream().map(target -> {
            MultiRuptureJump jump = makeJump(nucleation, target);
                PlausibilityResult compatibility = PlausibilityResult.PASS;
                for (MultiRuptureCompatibilityFilter filter : compatibilityFilters) {
                    compatibility = compatibility.logicalAnd(filter.apply(jump, VERBOSE));
                    if (!compatibility.canContinue()) {
                        break;
                    }
                }
                if (compatibility.isPass()) {
                    return MultiClusterRupture.takeSplayJump(jump);
                }
                return null;
        }).filter(Objects::nonNull).toList();

        int counter = mergeCounter++;
        if (counter % 10 == 0) {
            System.out.print('.');
            if (counter % 1000 == 0)
            	System.out.println();
        }
        return result;
    }

    /**
     * Merges all nucleation ruptures with all compatible target ruptures in parallel.
     *
     * @return list of newly created multi-cluster ruptures
     */
    public List<ClusterRupture> merge() {
        return nucleationRuptures
                .parallelStream()
                .flatMap(nucleation -> getJumpTargets(nucleation).stream().map(target -> makeJump(nucleation, target)))
                .map(jump -> {
                    PlausibilityResult compatibility = PlausibilityResult.PASS;
                    for (MultiRuptureCompatibilityFilter filter : compatibilityFilters) {
                        compatibility = compatibility.logicalAnd(filter.apply(jump, VERBOSE));
                        if (!compatibility.canContinue()) {
                            break;
                        }
                    }
                    if (compatibility.isPass()) {
                        return MultiClusterRupture.takeSplayJump(jump);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static File resolveFile(String... locations) {
        for(String location:locations) {
            File file = new File(location);
            if(file.exists()) {
                return file;
            }
        }
        return null;
    }

    public static boolean isSubduction(ClusterRupture rupture) {
        TectonicRegionType tectonicRegionType = rupture.clusters[0].startSect.getTectonicRegionType();
        return tectonicRegionType == TectonicRegionType.SUBDUCTION_INTERFACE || tectonicRegionType == TectonicRegionType.SUBDUCTION_SLAB;
    }

    public String setUpKevinsFilters(RuptureMerger merger, StiffnessCalcModule stiffness) {
        // what fraction of interactions should be positive? this number will take some tuning
        float fractThreshold = 0.75f;
        String outPrefix = "_cff" + oDF.format(fractThreshold) + "IntsPos";
        MultiRuptureFractCoulombPositiveFilter fractCoulombFilter = new MultiRuptureFractCoulombPositiveFilter(stiffness.stiffnessCalc, fractThreshold, Directionality.BOTH);
        merger.addFilter(fractCoulombFilter);

        // force the net coulomb from one rupture to the other to positive; this more heavily weights nearby interactions
        Directionality netDirectionality = Directionality.BOTH; // require it to be positive to from subduction to crustal AND from crustal to subduction
//     	Directionality netDirectionality = Directionality.EITHER; // require it to be positive to from subduction to crustal OR from crustal to subduction
        outPrefix += "_cffNetPositive" + netDirectionality;
        MultiRuptureNetCoulombPositiveFilter netCoulombFilter = new MultiRuptureNetCoulombPositiveFilter(stiffness.stiffnessCalc, netDirectionality);
        merger.addFilter(netCoulombFilter);

        return outPrefix;
    }

    public String setUpBrucesFilters(RuptureMerger merger, StiffnessCalcModule stiffness) {

        double selfStiffnessThreshold = 0;
        String outPrefix = "_cff" + oDF.format(selfStiffnessThreshold) + "SelfStiffness";
        MultiRuptureSelfStiffnessFilter selfStiffnessFilter = new MultiRuptureSelfStiffnessFilter(stiffness.stiffnessCalc);
        merger.addFilter(selfStiffnessFilter);

        return outPrefix;
    }

    /**
     * RuptureMerger configuration with defaults
     */
    public static class Config{
        public File ruptureSet;
        public File filterFile;
        public File outputDir = new File("/tmp/");
        public double maxJumpDist = 15;
        public int areaSpreadCount = 3;
        public double areaSpreadDiff = 0.1;
        public double stiffnessGridSpacing = 2;
        public File stiffnessCacheDir = new File("/tmp/stiffnessCaches/");
    }

    public static void merge(Config config) throws IOException {

        // load ruptures and split them up into crustal and subduction
    	File inputFile = config.ruptureSet;
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(inputFile);
        List<ClusterRupture> nucleationRuptures = new ArrayList<>();
        List<ClusterRupture> targetRuptures = new ArrayList<>();
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranded(rupSet);
        }

        List<ClusterRupture> ruptures = cRups.getAll();
        File filterFile = config.filterFile;
        if (filterFile != null) {
            int oldRupCount = ruptures.size();
            ruptures = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new FileReader(filterFile));
            String line = "";
            while ((line = reader.readLine()) != null) {
                ruptures.add(cRups.get(Integer.parseInt(line.trim())));
            }
            reader.close();
            System.out.println("Filter file exists. We're only using " + ruptures.size() + " of " + oldRupCount + " ruptures.");
        }

        for (ClusterRupture rupture : ruptures) {
            if (isSubduction(rupture)) {
                nucleationRuptures.add(rupture);
            } else {
                targetRuptures.add(rupture);
            }
        }

        System.out.println("Loaded " + nucleationRuptures.size() + " nucleation ruptures");
        System.out.println("Loaded " + targetRuptures.size() + " target ruptures");

        // set up RuptureMerger
        String outPrefix = "mergedRupset_"+oDF.format(config.maxJumpDist)+"km";
        RuptureMerger merger = new RuptureMerger(rupSet, config.maxJumpDist, nucleationRuptures, targetRuptures);

        AreaSpreadSelector targetSelector = new AreaSpreadSelector(merger.getDisAzCalc(), config.areaSpreadCount, config.areaSpreadDiff);
        merger.setTargetSelector(targetSelector);

        System.out.println("possible jumps: "+merger.countPossibleJumps());

        StiffnessCalcModule stiffness = new StiffnessCalcModule(rupSet, config.stiffnessGridSpacing, config.stiffnessCacheDir);

        if (stiffness.stiffGridSpacing != 1d)
            outPrefix += "_cffPatch" + oDF.format(stiffness.stiffGridSpacing) + "km";

        // outPrefix += merger.setUpKevinsFilters(merger, stiffness);

        outPrefix += merger.setUpBrucesFilters(merger, stiffness);

//        MultiRuptureSelfStiffnessFractionAreaFilter areaFilter = new MultiRuptureSelfStiffnessFractionAreaFilter(stiffness.stiffnessCalc, 0.9, rupSet.getFaultSectionDataList());
//        merger.addFilter(areaFilter);

        // run RuptureMerger for one nucleation rupture for now
//     	int fromID = 97653;
//        ClusterRupture testNucleation = cRups.get(fromID);
//        outPrefix += "_from"+fromID;
//        
//        // precalculate coulomb metrics between each section in parallel
//        firstCoulombFilter.parallelCacheStiffness(testNucleation, targetRuptures, merger.disAzCalc, merger.maxJumpDist);
//        stiffnessCacheSize = checkUpdateStiffnessCache(stiffnessCacheFile, stiffnessCacheSize, stiffnessCache);
//        
//        List<ClusterRupture> mergedRuptures = merger.merge(testNucleation, targetRuptures);

        // full calculation
     	merger.setVerbose(false); // otherwise too much stdout
        List<ClusterRupture> mergedRuptures = merger.merge();

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

        stiffness.checkUpdateStiffnessCache();

        List<ClusterRupture> combinedRuptures = new ArrayList<>(cRups.getAll());
        combinedRuptures.addAll(mergedRuptures);

        FaultSystemRupSet resultRupSet =
                FaultSystemRupSet.builderForClusterRups(
                                rupSet.getFaultSectionDataList(),
                                combinedRuptures)
                        // magnitudes will be wrong, but this is required
                        .forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
                        .addModule(stiffness)
                        .build();
        resultRupSet.write(new File(config.outputDir, outPrefix + ".zip"));

        // quick sanity check
        RupCartoonGenerator.plotRupture(config.outputDir, outPrefix, mergedRuptures.get(0), "merged rupture", false, true);

        List<ClusterRupture> sortedRups = new ArrayList<>(mergedRuptures);
        sortedRups.sort(Comparator.comparing((ClusterRupture r) -> r.clusters[0].subSects.size()).thenComparing((ClusterRupture r) -> r.buildOrderedSectionList().size()));
//
//        MultiRuptureStiffnessPlot plot = new MultiRuptureStiffnessPlot(stiffness.stiffnessCalc);
//        plot.plot(new File("/tmp"), outPrefix + "stiffness", sortedRups.get(sortedRups.size() - 1), "merged rupture " + (sortedRups.size() - 1));
    }

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        // dirty...but this will help us collaborate better...
        config.ruptureSet = resolveFile(
                "C:\\runs\\run_4\\nzshm22_complete_merged.zip",
                "C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip",
                "/home/kevin/Downloads/rupset-disjointed.zip");
        config.filterFile = resolveFile("C:\\tmp\\filteredRuptures.txt", "C:\\runs\\run_4\\filteredRuptures.txt");

        merge(config);
    }
}
