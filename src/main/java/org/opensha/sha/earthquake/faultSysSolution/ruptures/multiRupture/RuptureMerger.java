package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureFractCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureNetCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RuptureMerger {

    boolean VERBOSE = true;
    List<MultiRuptureCompatibilityFilter> compatibilityFilters = new ArrayList<>();
    final SectionDistanceAzimuthCalculator disAzCalc;
    final double maxJumpDist;

    public RuptureMerger(FaultSystemRupSet rupSet, double maxJumpDist) {
        disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        this.maxJumpDist = maxJumpDist;
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
        for (FaultSection targetSection : target.buildOrderedSectionList()) {
            for (FaultSection nucleationSection : nucleation.clusters[0].subSects) {
                double distance = disAzCalc.getDistance(targetSection, nucleationSection);
                if (distance <= maxJumpDist) {
                    return new MultiRuptureJump(nucleationSection, nucleation, targetSection, target, distance);
                }
            }
        }
        return null;
    }

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
            MultiRuptureJump jump = makeJump(nucleation, target);
            if (jump != null) {
                PlausibilityResult compatibility = PlausibilityResult.PASS;
                for (MultiRuptureCompatibilityFilter filter : compatibilityFilters) {
                    compatibility = compatibility.logicalAnd(filter.apply(jump, VERBOSE));
                    if (!compatibility.canContinue()) {
                        break;
                    }
                }
                if (compatibility.isPass()) {
                    result.add(MultiClusterRupture.takeSplayJump(jump));
                }
            }
        }

        int counter = mergeCounter++;
        if (counter % 10 == 0) {
            System.out.print('.');
            if (counter % 1000 == 0)
            	System.out.println();
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

    public static File resolveFile(String... locations) {
        for(String location:locations) {
            File file = new File(location);
            if(file.exists()) {
                return file;
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {

        // load ruptures and split them up into crustal and subduction
    	// dirty...but this will help us collaborate better...
    	// Oakley's file:
    	File inputFile = resolveFile(
                "C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip",
                "/home/kevin/Downloads/rupset-disjointed.zip");
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(inputFile);
        List<ClusterRupture> nucleationRuptures = new ArrayList<>();
        List<ClusterRupture> targetRuptures = new ArrayList<>();
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranged(rupSet);
        }

        List<ClusterRupture> ruptures = cRups.getAll();
        File filterFile = resolveFile("C:\\Users\\user\\GNS\\rupture sets\\filteredRuptures.txt");
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
            if (rupture.clusters[0].subSects.get(0).getSectionName().contains("row:")) {
                nucleationRuptures.add(rupture);
            } else {
                targetRuptures.add(rupture);
            }
        }

        System.out.println("Loaded " + nucleationRuptures.size() + " nucleation ruptures");
        System.out.println("Loaded " + targetRuptures.size() + " target ruptures");
        
        DecimalFormat oDF = new DecimalFormat("0.##");

        // set up RuptureMerger
        double maxJumpDist = 5d;
        String outPrefix = "mergedRupset_"+oDF.format(maxJumpDist)+"km";
        RuptureMerger merger = new RuptureMerger(rupSet, 5);
        
        // Coulomb filter
        // stiffness grid spacing, increase if it's taking too long
     	double stiffGridSpacing = 2d;
     	if (stiffGridSpacing != 1d)
     		outPrefix += "_cffPatch"+oDF.format(stiffGridSpacing)+"km";
     	// stiffness calculation constants
     	double lameLambda = 3e4;
     	double lameMu = 3e4;
     	double coeffOfFriction = 0.5;
     	SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
     			rupSet.getFaultSectionDataList(), stiffGridSpacing, lameLambda, lameMu, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
     	AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);

     	File cacheDir = new File("/tmp");
     	File stiffnessCacheFile = null;
     	int stiffnessCacheSize = 0;
     	if (cacheDir != null && cacheDir.exists()) {
     		stiffnessCacheFile = new File(cacheDir, stiffnessCache.getCacheFileName());
     		stiffnessCacheSize = 0;
     		if (stiffnessCacheFile.exists()) {
     			try {
     				stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
     			} catch (IOException e) {
     				System.err.println("WARNING: exception loading previous cache");
     				e.printStackTrace();
     			}
     		} else {
     			System.out.println("Will cache to: "+stiffnessCacheFile.getAbsolutePath());
     		}
     	}
     	
     	// will be used to quickly cache all interactions
     	MultiRuptureCoulombFilter firstCoulombFilter = null;
     	
     	// what fraction of interactions should be positive? this number will take some tuning
     	float fractThreshold = 0.75f;
     	outPrefix += "_cff"+oDF.format(fractThreshold)+"IntsPos";
     	MultiRuptureFractCoulombPositiveFilter fractCoulombFilter = new MultiRuptureFractCoulombPositiveFilter(stiffnessCalc, fractThreshold);
     	if (firstCoulombFilter == null)
     		firstCoulombFilter = fractCoulombFilter;
     	merger.addFilter(fractCoulombFilter);
     	
     	// force the net coulomb from one rupture to the other to positive; this more heavily weights nearby interactions
     	Directionality netDirectionality = Directionality.BOTH; // require it to be positive to from subduction to crustal AND from crustal to subduction
//     	Directionality netDirectionality = Directionality.EITHER; // require it to be positive to from subduction to crustal OR from crustal to subduction
     	outPrefix += "_cffNetPositive"+netDirectionality;
     	MultiRuptureNetCoulombPositiveFilter netCoulombFilter = new MultiRuptureNetCoulombPositiveFilter(stiffnessCalc, netDirectionality);
     	if (firstCoulombFilter == null)
     		firstCoulombFilter = netCoulombFilter;
     	merger.addFilter(netCoulombFilter);

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
        List<ClusterRupture> mergedRuptures = merger.merge(nucleationRuptures, targetRuptures);

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
        
        checkUpdateStiffnessCache(stiffnessCacheFile, stiffnessCacheSize, stiffnessCache);

        // write only the merged ruptures
        FaultSystemRupSet resultRupSet =
                FaultSystemRupSet.builderForClusterRups(
                                rupSet.getFaultSectionDataList(),
                                mergedRuptures)
                        // magnitudes will be wrong, but this is required
                        .forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
                        .build();
        resultRupSet.write(new File("/tmp/"+outPrefix+".zip"));

        // quick sanity check
        RupCartoonGenerator.plotRupture(new File("/tmp/"), outPrefix, mergedRuptures.get(0), "merged rupture", false, true);

    }
    
    private static int checkUpdateStiffnessCache(File stiffnessCacheFile, int stiffnessCacheSize, AggregatedStiffnessCache stiffnessCache) {
        int newSize = stiffnessCache.calcCacheSize();
        if (stiffnessCacheFile != null && stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
        	// we've calculated new Coulomb values, write out new cache files
        	System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
        	try {
        		stiffnessCache.writeCacheFile(stiffnessCacheFile);
        		System.out.println("DONE writing stiffness cache");
                return newSize; 
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
        }
        return stiffnessCacheSize;
    }
}
